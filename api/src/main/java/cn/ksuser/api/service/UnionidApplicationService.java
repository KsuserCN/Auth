package cn.ksuser.api.service;

import cn.ksuser.api.entity.ConsoleAuditLog;
import cn.ksuser.api.entity.Oauth2Application;
import cn.ksuser.api.entity.UnionidApplication;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.ConsoleAuditLogRepository;
import cn.ksuser.api.repository.Oauth2ApplicationRepository;
import cn.ksuser.api.repository.UnionidApplicationRepository;
import cn.ksuser.api.repository.UserRepository;
import cn.ksuser.api.result.ApiResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class UnionidApplicationService {
    private final Oauth2ApplicationRepository apps;
    private final UnionidApplicationRepository requests;
    private final UserRepository users;
    private final ConsoleSessionService sessions;
    private final ConsoleAuditLogRepository audits;
    private final ObjectMapper json;

    public UnionidApplicationService(Oauth2ApplicationRepository apps, UnionidApplicationRepository requests,
                                     UserRepository users, ConsoleSessionService sessions,
                                     ConsoleAuditLogRepository audits, ObjectMapper json) {
        this.apps = apps; this.requests = requests; this.users = users;
        this.sessions = sessions; this.audits = audits; this.json = json;
    }

    @Transactional
    public void apply(User owner, String appId, String rawReason) {
        if (!(("personal".equals(owner.getDeveloperVerificationType()) || "enterprise".equals(owner.getDeveloperVerificationType())) || owner.isSystemAdmin())) {
            throw new Oauth2Exception(HttpStatus.FORBIDDEN, "access_denied", ApiResultCode.OAUTH2_022);
        }
        String reason = reason(rawReason);
        Oauth2Application app = owned(owner, appId);
        if (!Boolean.TRUE.equals(app.getIsActive()) || Set.of("pending", "approved").contains(app.getUnionidStatus())) throw conflict();
        UnionidApplication request = new UnionidApplication();
        request.setAppId(app.getAppId()); request.setStatus("pending"); request.setReason(reason);
        requests.save(request);
        app.setUnionidStatus("pending"); app.setUnionidReason(reason); app.setUnionidReviewReason(null);
        apps.save(app);
    }

    @Transactional
    public void disable(User owner, String appId) {
        Oauth2Application app = owned(owner, appId);
        String previous = app.getUnionidStatus();
        if (!Set.of("pending", "approved").contains(previous)) throw conflict();
        String next = "pending".equals(previous) ? "none" : "revoked";
        UnionidApplication latest = requests.findFirstByAppIdOrderByCreatedAtDescIdDesc(appId).orElse(null);
        if (latest != null && previous.equals(latest.getStatus())) {
            latest.setStatus("pending".equals(previous) ? "withdrawn" : "revoked");
            latest.setReviewReason("开发者主动关闭"); latest.setReviewedAt(LocalDateTime.now());
            requests.save(latest);
        }
        app.setUnionidStatus(next); app.setUnionidReviewReason(null);
        apps.save(app);
    }

    @Transactional(readOnly = true)
    public ConsoleAdminService.PageView<View> list(int page, int size, String status) {
        if (page < 0 || size < 1 || size > 100 || status == null
            || !Set.of("all", "pending", "approved", "rejected", "revoked", "withdrawn").contains(status)) throw invalid();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<UnionidApplication> result = "all".equals(status) ? requests.findAll(pageable) : requests.findAllByStatus(status, pageable);
        return new ConsoleAdminService.PageView<>(result.getContent().stream().map(this::view).toList(), result.getTotalElements(), page, size);
    }

    @Transactional
    public View review(ConsoleSessionService.ConsolePrincipal actor, Long requestId, Review input, String ip) {
        sessions.requireFresh(actor);
        if (input == null || input.decision() == null || !Set.of("approve", "reject", "revoke").contains(input.decision())) throw invalid();
        String reviewReason = reason(input.reason());
        String appId = requests.findAppIdById(requestId).orElseThrow(UnionidApplicationService::missing);
        // Lock application before the current-read request query; do not cache an entity from a prior snapshot.
        Oauth2Application app = apps.findByAppIdForUpdate(appId).orElseThrow(UnionidApplicationService::missing);
        UnionidApplication latest = requests.findFirstByAppIdOrderByCreatedAtDescIdDesc(app.getAppId()).orElseThrow(UnionidApplicationService::missing);
        if (!Objects.equals(latest.getId(), requestId)) throw conflict();
        UnionidApplication request = latest;
        boolean revoke = "revoke".equals(input.decision());
        String expected = revoke ? "approved" : "pending";
        if (!expected.equals(app.getUnionidStatus()) || !expected.equals(request.getStatus())) throw conflict();
        String next = revoke ? "revoked" : "approve".equals(input.decision()) ? "approved" : "rejected";
        if ("approved".equals(next) && !Boolean.TRUE.equals(app.getIsActive())) throw conflict();
        request.setStatus(next); request.setReviewReason(reviewReason);
        request.setReviewerUuid(actor.userUuid()); request.setReviewedAt(LocalDateTime.now());
        requests.save(request);
        app.setUnionidStatus(next); app.setUnionidReviewReason(reviewReason); apps.save(app);
        try {
            audits.save(new ConsoleAuditLog(actor.userUuid(), app.getAppId(), "unionid." + input.decision(),
                json.writeValueAsString(Map.of("requestId", requestId, "previousStatus", expected, "status", next, "reason", reviewReason)), ip));
        } catch (JsonProcessingException ex) { throw new IllegalStateException("Audit serialization failed", ex); }
        return view(request);
    }

    private Oauth2Application owned(User owner, String appId) {
        return apps.findByAppIdForUpdate(appId).filter(app -> Objects.equals(owner.getId(), app.getOwnerUserId()))
            .orElseThrow(UnionidApplicationService::missing);
    }
    private View view(UnionidApplication request) {
        Oauth2Application app = apps.findByAppId(request.getAppId()).orElseThrow(UnionidApplicationService::missing);
        User owner = users.findById(app.getOwnerUserId()).orElse(null);
        return new View(request.getId(), app.getAppId(), app.getAppName(), app.getApplicationType(), app.getOwnerUserId(),
            owner == null ? null : owner.getUsername(), request.getStatus(), request.getReason(), request.getReviewReason(),
            request.getCreatedAt(), request.getReviewedAt());
    }
    private static String reason(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 1000) {
            throw new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", ApiResultCode.OAUTH2_UNIONID_REASON_INVALID);
        }
        return value.trim();
    }
    private static Oauth2Exception invalid() { return new Oauth2Exception(HttpStatus.BAD_REQUEST, "invalid_request", ApiResultCode.BAD_REQUEST); }
    private static Oauth2Exception missing() { return new Oauth2Exception(HttpStatus.NOT_FOUND, "invalid_client", ApiResultCode.OAUTH2_023); }
    private static Oauth2Exception conflict() { return new Oauth2Exception(HttpStatus.CONFLICT, "invalid_request", ApiResultCode.OAUTH2_UNIONID_STATE_INVALID); }
    public record Review(String decision, String reason) { }
    public record View(Long id, String appId, String appName, String applicationType, Long ownerUserId, String ownerUsername,
                       String status, String reason, String reviewReason, LocalDateTime createdAt, LocalDateTime reviewedAt) { }
}
