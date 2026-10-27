package cn.ksuser.api.dto;

/** A one-use proof of an existing credential, specifically for TOTP enrollment. */
public record TotpRegistrationStartRequest(String proofId) { }
