package cn.ksuser.api.dto;

public record TotpEnrollmentPasskeyVerifyRequest(String passkeyChallengeId,
                                                PasskeyAuthenticationVerifyRequest credential) { }
