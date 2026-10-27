package cn.ksuser.api.dto;

/** Existing credentials are verified specifically for the supplied QR challenge. */
public record QrApprovalVerifyRequest(String approveCode, String method, String password,
                                     String code, String recoveryCode) { }
