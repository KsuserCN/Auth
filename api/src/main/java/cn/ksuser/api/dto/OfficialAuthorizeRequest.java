package cn.ksuser.api.dto;

public record OfficialAuthorizeRequest(String clientId, String redirectUri, String responseType,
    String scope, String state, String nonce, String codeChallenge, String codeChallengeMethod,
    Boolean approved) { }
