package cn.ksuser.api.service;

import cn.ksuser.api.dto.MobileAppPlatforms;
import cn.ksuser.api.entity.Oauth2AppPlatform;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.Oauth2AppPlatformRepository;
import cn.ksuser.api.result.ApiResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class MobileAppPlatformService {
    private final Oauth2AppPlatformRepository repository;
    private final ObjectMapper mapper;
    private final MobileAssociationVerifier verifier;

    public MobileAppPlatformService(Oauth2AppPlatformRepository repository, ObjectMapper mapper, MobileAssociationVerifier verifier) {
        this.repository = repository; this.mapper = mapper; this.verifier = verifier;
    }

    /** Client supplied verification results are always discarded. */
    public MobileAppPlatforms normalize(MobileAppPlatforms value) {
        if (value == null || value.ios() == null && value.android() == null) throw invalid();
        MobileAppPlatforms.Ios ios = null;
        MobileAppPlatforms.Android android = null;
        if (value.ios() != null) {
            var i = value.ios();
            String bundleId = identifier(i.bundleId(), 255, "[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+");
            String prefix = identifier(i.appIdPrefix(), 10, "[A-Z0-9]{10}");
            String scheme = optional(i.urlScheme(), 100);
            if (scheme != null && (!scheme.matches("[A-Za-z][A-Za-z0-9+.-]*") || Set.of("http", "https", "file", "javascript", "data").contains(scheme.toLowerCase(Locale.ROOT)))) throw invalid();
            String store = optional(i.appStoreId(), 32);
            if (store != null && !store.matches("[0-9]+")) throw invalid();
            ios = new MobileAppPlatforms.Ios(bundleId, prefix, callback(i.universalLink()), scheme, store, "unverified", null, null);
        }
        if (value.android() != null) {
            var a = value.android();
            String name = identifier(a.packageName(), 255, "[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+");
            if (a.sha256CertFingerprints() == null || a.sha256CertFingerprints().isEmpty() || a.sha256CertFingerprints().size() > 10) throw invalid();
            List<String> fingerprints = a.sha256CertFingerprints().stream().map(MobileAppPlatformService::fingerprint).distinct().sorted().toList();
            String download = optional(a.downloadUrl(), 500);
            if (download != null) {
                try {
                    URI uri = URI.create(download);
                    if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getPort() != -1 && uri.getPort() != 443) throw invalid();
                    download = uri.toASCIIString();
                } catch (IllegalArgumentException ex) { throw invalid(); }
            }
            android = new MobileAppPlatforms.Android(name, fingerprints, callback(a.appLink()), download, "unverified", null, null);
        }
        MobileAppPlatforms normalized = new MobileAppPlatforms(ios, android);
        if (redirectUris(normalized).length() > 500) throw invalid();
        return normalized;
    }

    public String redirectUris(MobileAppPlatforms value) {
        List<String> result = new ArrayList<>();
        if (value.ios() != null) result.add(value.ios().universalLink());
        if (value.android() != null && !result.contains(value.android().appLink())) result.add(value.android().appLink());
        return String.join(";", result);
    }

    @Transactional
    public void savePlatforms(String appId, MobileAppPlatforms value) {
        MobileAppPlatforms normalized = normalize(value);
        save(appId, "ios", normalized.ios());
        save(appId, "android", normalized.android());
    }

    private void save(String appId, String platform, Object config) {
        var existing = repository.findByAppIdAndPlatform(appId, platform);
        if (config == null) {
            // Keep a tombstone so removing/re-adding a platform cannot reset its verification quota.
            existing.filter(Oauth2AppPlatform::isConfigured).ifPresent(row -> {
                row.setConfigured(false); row.setConfigRevision(UUID.randomUUID().toString());
                row.setVerificationStatus("unverified"); row.setVerificationMessage(null); row.setVerifiedAt(null); repository.save(row);
            });
            return;
        }
        String json = encode(config);
        Oauth2AppPlatform row = existing.orElseGet(Oauth2AppPlatform::new);
        if (row.isConfigured() && json.equals(row.getConfigJson())) return;
        row.setAppId(appId); row.setPlatform(platform); row.setConfigJson(json);
        row.setConfigured(true);
        row.setConfigRevision(UUID.randomUUID().toString()); row.setVerificationStatus("unverified");
        row.setVerificationMessage(null); row.setVerifiedAt(null);
        // Keep the rate limit across edits so changes cannot bypass verification quotas.
        repository.save(row);
    }

    public MobileAppPlatforms getPlatforms(String appId) {
        MobileAppPlatforms.Ios ios = null; MobileAppPlatforms.Android android = null;
        for (var row : repository.findAllByAppId(appId)) {
            if (!row.isConfigured()) continue;
            String at = row.getVerifiedAt() == null ? null : row.getVerifiedAt().toString();
            if ("ios".equals(row.getPlatform())) {
                var i = decode(row.getConfigJson(), MobileAppPlatforms.Ios.class);
                ios = new MobileAppPlatforms.Ios(i.bundleId(), i.appIdPrefix(), i.universalLink(), i.urlScheme(), i.appStoreId(), row.getVerificationStatus(), row.getVerificationMessage(), at);
            } else if ("android".equals(row.getPlatform())) {
                var a = decode(row.getConfigJson(), MobileAppPlatforms.Android.class);
                android = new MobileAppPlatforms.Android(a.packageName(), a.sha256CertFingerprints(), a.appLink(), a.downloadUrl(), row.getVerificationStatus(), row.getVerificationMessage(), at);
            }
        }
        return new MobileAppPlatforms(ios, android);
    }

    /** No database transaction or row lock is held during a remote request. */
    public MobileAppPlatforms verifyPlatform(String appId, String platform) {
        if (!List.of("ios", "android").contains(platform == null ? "" : platform)) throw invalid();
        var row = repository.findByAppIdAndPlatform(appId, platform).orElseThrow(MobileAppPlatformService::invalid);
        if (!row.isConfigured()) throw invalid();
        String revision = row.getConfigRevision(), config = row.getConfigJson();
        LocalDateTime attempt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        if (repository.claimVerification(appId, platform, revision, attempt, attempt.minusMinutes(1)) != 1) {
            throw new Oauth2Exception(HttpStatus.TOO_MANY_REQUESTS, "slow_down", ApiResultCode.NATIVE_AUTHORIZATION_RATE_LIMITED);
        }
        String status = "verified", message = "关联文件验证通过";
        try {
            if ("ios".equals(platform)) verifier.verifyIos(decode(config, MobileAppPlatforms.Ios.class));
            else verifier.verifyAndroid(decode(config, MobileAppPlatforms.Android.class));
        } catch (MobileAssociationVerifier.VerificationFailure ex) {
            status = "failed"; message = ex.getMessage();
        }
        repository.finishVerification(appId, platform, revision, attempt, status, message, "verified".equals(status) ? LocalDateTime.now() : null);
        return getPlatforms(appId);
    }

    public void validateNative(String appId, String platform, String redirectUri) {
        validatedPlatform(appId, platform, redirectUri);
    }

    public String nativeRevision(String appId, String platform, String redirectUri) {
        return validatedPlatform(appId, platform, redirectUri).getConfigRevision();
    }

    /** Also guards browser and QR OAuth routes, so they cannot bypass native platform registration. */
    public void validateMobileRedirect(String appId, String redirectUri) {
        matchingRevisions(appId, redirectUri);
    }

    /** Browser/QR requests have no explicit platform and may use a callback shared by both platforms. */
    public Map<String, String> matchingRevisions(String appId, String redirectUri) {
        Map<String, String> revisions = new LinkedHashMap<>();
        for (var row : repository.findAllByAppId(appId)) {
            if (matches(row, redirectUri)) revisions.put(row.getPlatform(), row.getConfigRevision());
        }
        if (revisions.isEmpty()) throw invalid();
        return Map.copyOf(revisions);
    }

    /** Only the revisions frozen into a code can validate it; newly registered platforms cannot rescue it. */
    public boolean matchesAnyRevision(String appId, String redirectUri, Map<String, String> revisions) {
        if (revisions == null || revisions.isEmpty() || revisions.size() > 2
            || revisions.entrySet().stream().anyMatch(entry -> !supported(entry.getKey()) || entry.getValue() == null || entry.getValue().isBlank())) return false;
        return repository.findAllByAppId(appId).stream().anyMatch(row -> matches(row, redirectUri)
            && row.getConfigRevision().equals(revisions.get(row.getPlatform())));
    }

    private Oauth2AppPlatform validatedPlatform(String appId, String platform, String redirectUri) {
        if (!supported(platform)) throw invalid();
        var row = repository.findByAppIdAndPlatform(appId, platform).orElseThrow(MobileAppPlatformService::invalid);
        if (!matches(row, redirectUri)) throw invalid();
        return row;
    }

    private boolean matches(Oauth2AppPlatform row, String redirectUri) {
        if (!supported(row.getPlatform()) || !row.isConfigured() || !"verified".equals(row.getVerificationStatus())
            || row.getConfigRevision() == null || row.getConfigRevision().isBlank()) return false;
        String callback = "ios".equals(row.getPlatform())
            ? decode(row.getConfigJson(), MobileAppPlatforms.Ios.class).universalLink()
            : decode(row.getConfigJson(), MobileAppPlatforms.Android.class).appLink();
        return callback.equals(redirectUri);
    }

    private static boolean supported(String platform) { return "ios".equals(platform) || "android".equals(platform); }

    static String callback(String input) {
        String value = optional(input, 500);
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !uri.getHost().contains(".") || uri.getUserInfo() != null
                || uri.getPort() != -1 && uri.getPort() != 443 || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getRawPath() == null || !uri.getRawPath().startsWith("/") || !uri.equals(uri.normalize())
                || uri.getPath().contains("\\") || uri.getPath().codePoints().anyMatch(Character::isISOControl)
                || uri.getRawPath().contains(";") || uri.getRawPath().toLowerCase(Locale.ROOT).matches(".*%(?:2f|5c|3f|23|3b).*")
                || uri.getPath().matches(".*/\\.{1,2}(?:/.*)?")) throw invalid();
            return new URI("https", null, uri.getHost().toLowerCase(Locale.ROOT), -1, null, null, null).toString() + uri.getRawPath();
        } catch (Exception ex) { throw invalid(); }
    }
    static String fingerprint(String value) {
        if (value == null) throw invalid();
        String flat = value.trim().replace(":", "").toUpperCase(Locale.ROOT);
        if (!flat.matches("[A-F0-9]{64}")) throw invalid();
        return String.join(":", flat.split("(?<=\\G..)(?!$)"));
    }
    private static String identifier(String value, int max, String pattern) { String s = optional(value, max); if (s == null || !s.matches(pattern)) throw invalid(); return s; }
    private static String optional(String value, int max) { if (value == null || value.isBlank()) return null; String s = value.trim(); if (s.length() > max || s.codePoints().anyMatch(Character::isISOControl)) throw invalid(); return s; }
    private String encode(Object value) { try { return mapper.writeValueAsString(value); } catch (JsonProcessingException ex) { throw new IllegalStateException(ex); } }
    private <T> T decode(String value, Class<T> type) { try { return mapper.readValue(value, type); } catch (JsonProcessingException ex) { throw new IllegalStateException(ex); } }
    private static Oauth2Exception invalid() { return new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", ApiResultCode.NATIVE_AUTHORIZATION_INVALID); }
}
