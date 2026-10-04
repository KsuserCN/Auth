package cn.ksuser.api.service;

import cn.ksuser.api.entity.TotpRecoveryCode;
import cn.ksuser.api.repository.TotpRecoveryCodeRepository;
import cn.ksuser.api.repository.UserTotpRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;

class TotpRecoveryCodeTest {
    private TotpRecoveryCodeRepository codes;
    private UserTotpRepository configs;
    private TotpService service;
    private byte[] hash;
    @BeforeEach void setUp() throws Exception {
        codes=mock(TotpRecoveryCodeRepository.class);configs=mock(UserTotpRepository.class);
        service=new TotpService(configs,codes);
        hash=MessageDigest.getInstance("SHA-256").digest("ABCDEFGH".getBytes(StandardCharsets.UTF_8));
        when(codes.countByUserIdAndUnused(17L)).thenReturn(1L);
    }
    @Test void normalizedRecoveryCodeIsConsumedOnceForOnlyItsOwner() {
        TotpRecoveryCode code=new TotpRecoveryCode(17L,hash,null);
        when(codes.findByUserIdAndCodeHash(eq(17L),aryEq(hash))).thenReturn(Optional.of(code));
        assertFalse(service.verifyRecoveryCode(18L,"ABCDEFGH"));
        assertTrue(service.verifyRecoveryCode(17L," abcdefgh "));
        assertTrue(code.isUsed());
        assertFalse(service.verifyRecoveryCode(17L,"ABCDEFGH"));
        verify(codes,times(1)).save(code);verifyNoInteractions(configs);
    }
    @Test void usedOrUnknownRecoveryCodeDoesNotConsumeAnotherRecord() {
        TotpRecoveryCode code=new TotpRecoveryCode(17L,hash,null);code.markAsUsed();
        when(codes.findByUserIdAndCodeHash(eq(17L),aryEq(hash))).thenReturn(Optional.of(code));
        assertFalse(service.verifyRecoveryCode(17L,"ABCDEFGH"));
        assertFalse(service.verifyRecoveryCode(17L,"ZZZZZZZZ"));
        verify(codes,never()).save(any());verifyNoInteractions(configs);
    }
    @Test void malformedRecoveryCodeNeverQueriesDatabase() {
        assertFalse(service.verifyRecoveryCode(17L,"123456"));
        assertFalse(service.verifyRecoveryCode(17L,"ABCD-EFGH"));
        assertFalse(service.verifyRecoveryCode(17L,null));verifyNoInteractions(codes,configs);
    }
}
