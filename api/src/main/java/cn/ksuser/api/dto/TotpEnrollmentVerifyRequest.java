package cn.ksuser.api.dto;

/** Re-authenticate with a credential that existed before the TOTP being enrolled. */
public record TotpEnrollmentVerifyRequest(String method, String password, String code) { }
