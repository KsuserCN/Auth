package cn.ksuser.api.service;

import cn.ksuser.api.entity.User;
import cn.ksuser.api.entity.UserOauthAccount;
import cn.ksuser.api.repository.UserOauthAccountRepository;
import cn.ksuser.api.repository.UserRepository;
import cn.ksuser.api.result.ApiResultCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Finalizes only server-held claims after all required authentication factors have succeeded. */
@Service
public class OauthBindingCompletionService {
    private final OauthPendingBindingService pending;
    private final UserRepository users;
    private final UserOauthAccountRepository accounts;
    private final TransactionTemplate transactions;

    public OauthBindingCompletionService(OauthPendingBindingService pending, UserRepository users,
            UserOauthAccountRepository accounts, PlatformTransactionManager transactionManager) {
        this.pending = pending; this.users = users; this.accounts = accounts;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public User complete(OauthPendingBindingService.Claim claim) {
        pending.consumeClaim(claim);
        String provider = claim.identity().provider();
        try {
            return transactions.execute(status -> {
                User user = users.findByIdForUpdate(claim.userId()).orElseThrow(() ->
                    new OauthPendingBindingService.BindingFailure(HttpStatus.UNAUTHORIZED, ApiResultCode.AUTH_005));
                var identity = claim.identity();
                if (accounts.findByProviderAndUserId(identity.provider(), user.getId()).isPresent())
                    throw conflict(ApiResultCode.OAUTH_080, provider);
                boolean exists = accounts.findByProviderAndProviderUserId(identity.provider(), identity.providerUserId()).isPresent()
                    || identity.unionId() != null && accounts.findByProviderAndUnionId(identity.provider(), identity.unionId()).isPresent();
                if (exists) throw conflict(ApiResultCode.OAUTH_081, provider);
                UserOauthAccount account = new UserOauthAccount();
                account.setUserId(user.getId()); account.setProvider(identity.provider());
                account.setProviderUserId(identity.providerUserId()); account.setUnionId(identity.unionId());
                account.setIsEnabled(true);
                accounts.saveAndFlush(account);
                return user;
            });
        } catch (DataIntegrityViolationException ex) {
            // Database uniqueness also arbitrates claims for one provider identity across different users.
            throw conflict(ApiResultCode.OAUTH_081, provider);
        }
    }

    private static OauthPendingBindingService.BindingFailure conflict(ApiResultCode code, String provider) {
        String displayName = switch (provider) {
            case "qq" -> "QQ";
            case "github" -> "GitHub";
            case "microsoft" -> "Microsoft";
            case "google" -> "Google";
            default -> provider;
        };
        return new OauthPendingBindingService.BindingFailure(HttpStatus.CONFLICT, code.withParams("provider", displayName));
    }
}
