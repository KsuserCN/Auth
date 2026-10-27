package cn.ksuser.api.dto;

public record QrApprovalPasskeyVerifyRequest(String approveCode, String passkeyChallengeId,
                                            PasskeyAuthenticationVerifyRequest credential) { }
