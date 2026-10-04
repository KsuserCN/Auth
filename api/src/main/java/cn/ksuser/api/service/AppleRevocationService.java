package cn.ksuser.api.service;

import cn.ksuser.api.entity.*;
import cn.ksuser.api.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;

@Service
public class AppleRevocationService {
    private static final Logger log = LoggerFactory.getLogger(AppleRevocationService.class);
    private final AppleRevocationTaskRepository tasks;
    private final UserOauthAccountRepository accounts;
    private final AppleTokenClient client;
    private final AppleCredentialCipher cipher;
    public AppleRevocationService(AppleRevocationTaskRepository tasks, UserOauthAccountRepository accounts, AppleTokenClient client, AppleCredentialCipher cipher) {
        this.tasks=tasks;this.accounts=accounts;this.client=client;this.cipher=cipher;
    }
    @Transactional
    public void enqueueForUser(Long userId) {
        accounts.findByUserId(userId).stream().filter(a -> "apple".equals(a.getProvider())).forEach(this::enqueue);
    }
    public void enqueue(UserOauthAccount account) {
        if (account.getAppleRefreshTokenEncrypted()==null || account.getAppleRefreshTokenEncrypted().isBlank()) return;
        AppleRevocationTask task = new AppleRevocationTask();
        task.setClientId(account.getAppleClientId());
        task.setEncryptedToken(account.getAppleRefreshTokenEncrypted());
        task.setNextAttemptAt(LocalDateTime.now());
        tasks.save(task);
    }
    @Scheduled(fixedDelayString="${app.apple.revocation-retry-delay-ms:60000}")
    public void retry() {
        for (AppleRevocationTask task : tasks.findTop20ByNextAttemptAtBeforeOrderByNextAttemptAtAsc(LocalDateTime.now())) {
            try { client.revoke(cipher.decrypt(task.getEncryptedToken()), task.getClientId()); tasks.delete(task); }
            catch (Exception ex) {
                int attempts=Math.min(1000000,task.getAttempts()+1); task.setAttempts(attempts);
                task.setNextAttemptAt(LocalDateTime.now().plusSeconds(Math.min(86400,60L << Math.min(attempts,10))));
                tasks.save(task);
                log.warn("Apple revocation task {} deferred, attempt {}",task.getId(),attempts);
            }
        }
    }
}
