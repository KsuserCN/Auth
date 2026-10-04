package cn.ksuser.api.service;

import cn.ksuser.api.entity.*;
import cn.ksuser.api.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AppleRevocationServiceTest {
    @Test void retriesOutagesDurablyAndDeletesOnlyAfterSuccessfulRevocation() {
        AppleRevocationTaskRepository tasks=mock(AppleRevocationTaskRepository.class);
        AppleTokenClient client=mock(AppleTokenClient.class);AppleCredentialCipher cipher=mock(AppleCredentialCipher.class);
        AppleRevocationTask task=new AppleRevocationTask();task.setClientId("cn.ksuser.ios");task.setEncryptedToken("encrypted");task.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        when(tasks.findTop20ByNextAttemptAtBeforeOrderByNextAttemptAtAsc(any())).thenReturn(List.of(task));
        when(cipher.decrypt("encrypted")).thenReturn("refresh");doThrow(new IllegalStateException("network")).doNothing().when(client).revoke("refresh","cn.ksuser.ios");
        AppleRevocationService service=new AppleRevocationService(tasks,mock(UserOauthAccountRepository.class),client,cipher);
        service.retry();assertEquals(1,task.getAttempts());assertTrue(task.getNextAttemptAt().isAfter(LocalDateTime.now()));
        verify(tasks).save(task);verify(tasks,never()).delete(task);
        service.retry();verify(tasks).delete(task);
    }
    @Test void durableTaskCopiesOnlyEncryptedProviderTokenAndHasNoUserReference() {
        AppleRevocationTaskRepository tasks=mock(AppleRevocationTaskRepository.class);
        UserOauthAccount account=new UserOauthAccount();account.setAppleClientId("app");account.setAppleRefreshTokenEncrypted("ciphertext");
        AppleRevocationService service=new AppleRevocationService(tasks,mock(UserOauthAccountRepository.class),mock(AppleTokenClient.class),mock(AppleCredentialCipher.class));
        service.enqueue(account);
        var captured=org.mockito.ArgumentCaptor.forClass(AppleRevocationTask.class);verify(tasks).save(captured.capture());
        assertEquals("ciphertext",captured.getValue().getEncryptedToken());assertEquals("app",captured.getValue().getClientId());
    }
}
