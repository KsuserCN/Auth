package cn.ksuser.api.service;

import cn.ksuser.api.entity.ConsoleAuditLog;
import cn.ksuser.api.entity.OfficialApplication;
import cn.ksuser.api.entity.Oauth2Application;
import cn.ksuser.api.entity.User;
import cn.ksuser.api.exception.Oauth2Exception;
import cn.ksuser.api.repository.ConsoleAuditLogRepository;
import cn.ksuser.api.repository.ConsoleUserRepository;
import cn.ksuser.api.repository.OfficialApplicationRepository;
import cn.ksuser.api.repository.Oauth2ApplicationRepository;
import cn.ksuser.api.result.ApiResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ConsoleAdminService {
    private static final String DEFAULT_APPLICATION_LOGO_URL = "https://static.ksuser.cn/favicon.png";
    private final ConsoleUserRepository users;
    private final OfficialApplicationRepository applications;
    private final ConsoleAuditLogRepository audits;
    private final ConsoleSessionService sessions;
    private final PasswordEncoder passwords;
    private final ObjectMapper json;
    private final Oauth2ApplicationRepository oauthApplications;
    private UserService userService;
    public ConsoleAdminService(ConsoleUserRepository users, OfficialApplicationRepository applications,
                               ConsoleAuditLogRepository audits, ConsoleSessionService sessions,
                               PasswordEncoder passwords, ObjectMapper json,
                               Oauth2ApplicationRepository oauthApplications) {
        this.users=users; this.applications=applications; this.audits=audits;
        this.sessions=sessions; this.passwords=passwords; this.json=json;
        this.oauthApplications=oauthApplications;
    }

    @Autowired
    void setUserService(UserService userService) { this.userService=userService; }

    @Transactional(readOnly=true)
    public PageView<UserView> listUsers(int page, int size, String search, String role, String sort, String direction, String verification) {
        if (search != null && search.length() > 255) throw invalid();
        if (role != null && !role.isBlank() && !List.of("USER","ADMIN","SUPER_ADMIN").contains(role)) throw invalid();
        if (verification != null && !verification.isBlank()
            && !List.of("none", "personal", "enterprise", "admin", "verified").contains(verification)) throw invalid();
        String sortProperty = switch (sort == null ? "" : sort) {
            case "username" -> "username";
            case "uuid" -> "uuid";
            case "systemRole" -> "systemRole";
            case "createdAt" -> "createdAt";
            default -> throw invalid();
        };
        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException failure) {
            throw invalid();
        }
        Specification<User> spec = (root, query, builder) -> {
            java.util.ArrayList<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(builder.or(builder.like(root.get("uuid"), pattern, '\\'),
                    builder.like(root.get("username"), pattern, '\\'), builder.like(root.get("email"), pattern, '\\')));
            }
            if (role != null && !role.isBlank()) predicates.add(builder.equal(root.get("systemRole"), role));
            if (verification != null && !verification.isBlank()) {
                var admin = root.get("systemRole").in("ADMIN", "SUPER_ADMIN");
                if ("verified".equals(verification)) {
                    predicates.add(builder.or(root.get("verificationType").in("personal", "enterprise"), admin));
                } else if ("admin".equals(verification)) {
                    predicates.add(admin);
                } else {
                    predicates.add(builder.equal(root.get("verificationType"), verification));
                }
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        if (page < 0 || size < 1 || size > 100) throw invalid();
        Sort ordering = Sort.by(sortDirection, sortProperty);
        if (!"uuid".equals(sortProperty)) ordering = ordering.and(Sort.by(Sort.Direction.ASC, "uuid"));
        Page<User> result = users.findAll(spec, PageRequest.of(page, size, ordering));
        return new PageView<>(result.map(UserView::of).getContent(), result.getTotalElements(), page, size);
    }
    @Transactional(readOnly=true)
    public UserView user(String uuid) { return UserView.of(users.findByUuid(uuid).orElseThrow(ConsoleAdminService::userMissing)); }

    @Transactional
    public UserView changeVerification(ConsoleSessionService.ConsolePrincipal actor, String uuid,
                                       VerificationRequest request, String ip) {
        sessions.requireFresh(actor);
        if (request == null || !List.of("none", "personal", "enterprise").contains(
            request.developerVerificationType() == null ? "" : request.developerVerificationType())) throw invalidVerification();
        User target = users.findByUuidForUpdate(uuid).orElseThrow(ConsoleAdminService::userMissing);
        String name = request.verificationEntityName() == null ? "" : request.verificationEntityName().trim();
        boolean needsName = target.isSystemAdmin() || !"none".equals(request.developerVerificationType());
        if (name.length() > 200 || (needsName && name.isBlank())) throw invalidVerification();
        VerificationRequest previous = new VerificationRequest(target.getDeveloperVerificationType(), target.getVerificationEntityName());
        if (users.updateVerification(target.getId(), request.developerVerificationType(), needsName ? name : null,
            LocalDateTime.now()) != 1) throw userMissing();
        UserView updated = user(uuid);
        audit(actor, uuid, "user.verification.update", Map.of("previous", previous,
            "verification", new VerificationRequest(updated.developerVerificationType(), updated.verificationEntityName())), ip);
        return updated;
    }

    @Transactional(readOnly=true)
    public UserApplicationsView userApplications(String uuid) {
        User owner = users.findByUuid(uuid).orElseThrow(ConsoleAdminService::userMissing);
        List<OwnedApplicationView> apps = oauthApplications.findAllByOwnerUserIdOrderByCreatedAtDesc(owner.getId()).stream()
            .map(OwnedApplicationView::of)
            .sorted(java.util.Comparator.comparing(OwnedApplicationView::createdAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                .thenComparing(OwnedApplicationView::protocol).thenComparing(OwnedApplicationView::clientId))
            .toList();
        return new UserApplicationsView(UserView.of(owner), apps);
    }

    @Transactional
    public UserView changeRole(ConsoleSessionService.ConsolePrincipal actor, String uuid, String role, String ip) {
        sessions.requireFresh(actor);
        if (!List.of("USER", "ADMIN").contains(role == null ? "" : role)) throw invalid();
        User target = users.findByUuidForUpdate(uuid).orElseThrow(ConsoleAdminService::userMissing);
        if ("SUPER_ADMIN".equals(target.getSystemRole())) throw denied();
        String previous = target.getSystemRole();
        target.setSystemRole(role);
        target.setUpdatedAt(LocalDateTime.now());
        users.save(target);
        audit(actor, uuid, "user.role.update", Map.of("previousRole",previous,"systemRole",role), ip);
        return UserView.of(target);
    }

    @Transactional
    public void deleteUser(ConsoleSessionService.ConsolePrincipal actor, String uuid, String ip) {
        sessions.requireFresh(actor);
        User target = users.findByUuidForUpdate(uuid).orElseThrow(ConsoleAdminService::userMissing);
        if ("SUPER_ADMIN".equals(target.getSystemRole()) || actor.userUuid().equals(target.getUuid())) throw denied();
        Map<String, String> details = Map.of("systemRole", target.getSystemRole());
        userService.deleteUser(target);
        audit(actor, uuid, "user.delete", details, ip);
    }

    @Transactional(readOnly=true)
    public List<ApplicationView> listApplications() { return applications.findAllByOrderByCreatedAtDesc().stream().map(ApplicationView::of).toList(); }

    @Transactional
    public ApplicationSecret createApplication(ConsoleSessionService.ConsolePrincipal actor, ApplicationRequest request, String ip) {
        sessions.requireFresh(actor);
        validate(request);
        String id = request.clientId() == null || request.clientId().isBlank()
            ? OfficialAuthorizationService.randomValue("official_") : request.clientId().trim();
        if (!id.matches("[A-Za-z0-9_-]{8,128}") || OfficialAuthorizationService.CONSOLE_CLIENT_ID.equals(id)) throw invalid();
        if (applications.existsByClientId(id)) throw conflict();
        OfficialApplication app = new OfficialApplication();
        app.setClientId(id); app.setClientType(request.clientType()); app.setReserved(false);
        ApplicationRequest normalized = new ApplicationRequest(request.clientId(), request.clientName(),
            request.logoUrl()==null || request.logoUrl().isBlank() ? DEFAULT_APPLICATION_LOGO_URL : request.logoUrl(),
            request.clientType(), request.redirectUris(), request.scopes(), request.isActive(), request.allowMissingNonce());
        apply(app, normalized);
        String secret = null;
        if ("confidential".equals(app.getClientType())) {
            secret = OfficialAuthorizationService.randomValue("ofs_");
            app.setClientSecretHash(passwords.encode(secret));
        }
        applications.saveAndFlush(app);
        audit(actor,id,"official.application.create",ApplicationView.of(app),ip);
        return new ApplicationSecret(ApplicationView.of(app),secret);
    }

    @Transactional
    public ApplicationView updateApplication(ConsoleSessionService.ConsolePrincipal actor, String id, ApplicationRequest request, String ip) {
        sessions.requireFresh(actor);
        OfficialApplication app = appForUpdate(id);
        // The UI may omit immutable fields on update; supplied changes are explicitly rejected.
        if ((request.clientId()!=null && !request.clientId().equals(app.getClientId()))
            || (request.clientType()!=null && !request.clientType().equals(app.getClientType()))) throw invalid();
        ApplicationRequest normalized = new ApplicationRequest(app.getClientId(), request.clientName(), request.logoUrl(),
            app.getClientType(), request.redirectUris(), request.scopes(), request.isActive(),
            request.allowMissingNonce() == null ? app.getAllowMissingNonce() : request.allowMissingNonce());
        validate(normalized);
        if (reserved(app) && (!Boolean.TRUE.equals(normalized.isActive())
            || !normalized.redirectUris().contains(sessions.callbackUri())
            || !normalized.scopes().containsAll(List.of("openid", "profile", "email"))
            || Boolean.TRUE.equals(normalized.allowMissingNonce()))) throw denied();
        ApplicationView previous = ApplicationView.of(app);
        apply(app, normalized);
        applications.saveAndFlush(app);
        audit(actor,id,"official.application.update",Map.of("previous",previous,"application",ApplicationView.of(app)),ip);
        return ApplicationView.of(app);
    }

    @Transactional
    public void deleteApplication(ConsoleSessionService.ConsolePrincipal actor, String id, String ip) {
        sessions.requireFresh(actor);
        OfficialApplication app = appForUpdate(id);
        if (reserved(app)) throw denied();
        ApplicationView previous=ApplicationView.of(app);
        applications.delete(app);
        audit(actor,id,"official.application.delete",previous,ip);
    }

    @Transactional
    public ApplicationSecret rotateSecret(ConsoleSessionService.ConsolePrincipal actor, String id, String ip) {
        sessions.requireFresh(actor);
        OfficialApplication app=appForUpdate(id);
        if (!"confidential".equals(app.getClientType()) || reserved(app)) throw invalid();
        String secret=OfficialAuthorizationService.randomValue("ofs_");
        app.setClientSecretHash(passwords.encode(secret));
        applications.saveAndFlush(app);
        audit(actor,id,"official.application.secret.rotate",Map.of("clientId",id),ip);
        return new ApplicationSecret(ApplicationView.of(app),secret);
    }

    @Transactional(readOnly=true)
    public PageView<ConsoleAuditLog> listAudits(int page,int size,String sort,String direction) {
        if (page < 0 || size < 1 || size > 100) throw invalid();
        String sortProperty = switch (sort == null ? "" : sort) {
            case "createdAt" -> "createdAt";
            case "action" -> "action";
            case "actorUuid" -> "actorUuid";
            case "target" -> "target";
            case "ipAddress" -> "ipAddress";
            default -> throw invalid();
        };
        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException failure) {
            throw invalid();
        }
        Sort ordering = Sort.by(sortDirection, sortProperty).and(Sort.by(Sort.Direction.DESC, "id"));
        Page<ConsoleAuditLog> result=audits.findAll(PageRequest.of(page,size,ordering));
        return new PageView<>(result.getContent(),result.getTotalElements(),page,size);
    }
    private OfficialApplication appForUpdate(String id) {
        return applications.findByClientIdForUpdate(id).orElseThrow(() -> new Oauth2Exception(HttpStatus.NOT_FOUND,"invalid_request",ApiResultCode.CONSOLE_APPLICATION_INVALID));
    }
    private static boolean reserved(OfficialApplication app) { return Boolean.TRUE.equals(app.getReserved()) || OfficialAuthorizationService.CONSOLE_CLIENT_ID.equals(app.getClientId()); }
    private static void validate(ApplicationRequest request) {
        if (request == null || request.clientName()==null || request.clientName().isBlank() || request.clientName().length()>120
            || !List.of("public","confidential").contains(request.clientType()==null?"":request.clientType())
            || request.redirectUris()==null || request.redirectUris().isEmpty() || request.redirectUris().size()>20
            || request.scopes()==null || request.scopes().size()>OfficialAuthorizationService.ALL_SCOPES.size()
            || request.scopes().stream().anyMatch(java.util.Objects::isNull) || !request.scopes().contains("openid")
            || !OfficialAuthorizationService.ALL_SCOPES.containsAll(request.scopes()) || request.isActive()==null
            || (Boolean.TRUE.equals(request.allowMissingNonce()) && !"confidential".equals(request.clientType()))) throw invalid();
        if (request.logoUrl()!=null && !request.logoUrl().isBlank()) {
            if (request.logoUrl().length()>500) throw invalid();
            validateUrl(request.logoUrl(),false);
        }
        for (String uri:request.redirectUris()) {
            if (uri==null || uri.length()>2048) throw invalid();
            validateUrl(uri,true);
        }
    }
    private static void validateUrl(String raw,boolean callback) {
        try {
            URI uri=URI.create(raw);
            String host=uri.getHost();
            boolean loopback="localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
            if (host==null || uri.getUserInfo()!=null || uri.getFragment()!=null
                || (!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme()) && loopback))
                || (callback && raw.contains(" "))) throw invalid();
        } catch (IllegalArgumentException ex) { throw invalid(); }
    }
    private static void apply(OfficialApplication app,ApplicationRequest request) {
        app.setClientName(request.clientName().trim());
        app.setLogoUrl(request.logoUrl()==null || request.logoUrl().isBlank()?null:request.logoUrl().trim());
        app.setRedirectUris(String.join("\n", request.redirectUris().stream().distinct().toList()));
        app.setScopes(OfficialAuthorizationService.normalizeScopes(String.join(" ",request.scopes())));
        app.setIsActive(request.isActive());
        app.setAllowMissingNonce(Boolean.TRUE.equals(request.allowMissingNonce()));
    }
    private void audit(ConsoleSessionService.ConsolePrincipal actor,String target,String action,Object details,String ip) {
        try { audits.save(new ConsoleAuditLog(actor.userUuid(),target,action,json.writeValueAsString(details),ip)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Audit serialization failed",ex); }
    }
    private static Oauth2Exception invalid() { return new Oauth2Exception(HttpStatus.BAD_REQUEST,"invalid_request",ApiResultCode.CONSOLE_APPLICATION_INVALID); }
    private static Oauth2Exception invalidVerification() { return new Oauth2Exception(HttpStatus.BAD_REQUEST,"invalid_request",ApiResultCode.BAD_REQUEST); }
    private static Oauth2Exception denied() { return new Oauth2Exception(HttpStatus.FORBIDDEN,"access_denied",ApiResultCode.CONSOLE_ACCESS_DENIED); }
    private static Oauth2Exception conflict() { return new Oauth2Exception(HttpStatus.CONFLICT,"invalid_request",ApiResultCode.REQUEST_CONFLICT); }
    private static Oauth2Exception userMissing() { return new Oauth2Exception(HttpStatus.NOT_FOUND,"invalid_request",ApiResultCode.CONSOLE_USER_NOT_FOUND); }

    public record PageView<T>(List<T> items,long total,int page,int size) {}
    public record ApplicationRequest(String clientId,String clientName,String logoUrl,String clientType,List<String> redirectUris,List<String> scopes,Boolean isActive,Boolean allowMissingNonce) {}
    public record ApplicationSecret(ApplicationView application,String clientSecret) {}
    public record VerificationRequest(String developerVerificationType, String verificationEntityName) {}
    public record UserApplicationsView(UserView user, List<OwnedApplicationView> applications) {}
    public record OwnedApplicationView(String protocol, String clientId, String clientName, String logoUrl,
                                       List<String> redirectUris, List<String> scopes, String contactInfo,
                                       Boolean isActive, LocalDateTime createdAt, LocalDateTime updatedAt, String clientType, String applicationType, String unionidStatus) {
        public static OwnedApplicationView of(Oauth2Application app) {
            return new OwnedApplicationView("oauth2", app.getAppId(), app.getAppName(), app.getLogoUrl(),
                OfficialAuthorizationService.parseRedirectUris(app.getRedirectUri()), OfficialAuthorizationService.parseScopes(app.getScopes()), app.getContactInfo(),
                app.getIsActive(), app.getCreatedAt(), app.getUpdatedAt(), app.getClientType(), app.getApplicationType(), app.getUnionidStatus());
        }
    }
    public record ApplicationView(String clientId,String clientName,String logoUrl,String clientType,List<String> redirectUris,List<String> scopes,
                                  Boolean isActive,Boolean reserved,Boolean allowMissingNonce,LocalDateTime createdAt,LocalDateTime updatedAt) {
        public static ApplicationView of(OfficialApplication app) { return new ApplicationView(app.getClientId(),app.getClientName(),app.getLogoUrl(),app.getClientType(),
            OfficialAuthorizationService.parseRedirectUris(app.getRedirectUris()),OfficialAuthorizationService.parseScopes(app.getScopes()),app.getIsActive(),app.getReserved(),app.getAllowMissingNonce(),app.getCreatedAt(),app.getUpdatedAt()); }
    }
    /** Deliberate field allowlist: credentials, session verifiers and MFA/Passkey material never enter Console responses. */
    public record UserView(String uuid,String username,String email,String avatarUrl,String realName,String gender,LocalDate birthDate,String region,String bio,
                           String developerVerificationType,String verificationEntityName,String systemRole,LocalDateTime createdAt,LocalDateTime updatedAt) {
        public static UserView of(User user) { return new UserView(user.getUuid(),user.getUsername(),user.getEmail(),user.getAvatarUrl(),user.getRealName(),user.getGender(),
            user.getBirthDate(),user.getRegion(),user.getBio(),user.getDeveloperVerificationType(),user.getVerificationEntityName(),user.getSystemRole(),user.getCreatedAt(),user.getUpdatedAt()); }
    }
}
