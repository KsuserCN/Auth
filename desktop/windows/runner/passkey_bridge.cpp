#include "passkey_bridge.h"

#include <flutter/standard_method_codec.h>

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <optional>
#include <string>
#include <vector>

#include <webauthn.h>

#include "utils.h"

namespace runner {
namespace {

using EncodableValue = flutter::EncodableValue;
using EncodableList = flutter::EncodableList;
using EncodableMap = flutter::EncodableMap;

constexpr char kChannelName[] = "ksuser/passkey";
constexpr char kWebAuthnTypeGet[] = "webauthn.get";

constexpr char kBase64UrlAlphabet[] =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

std::optional<std::string> GetStringValue(const EncodableValue& value) {
  if (const auto* str = std::get_if<std::string>(&value)) {
    return *str;
  }
  return std::nullopt;
}

std::optional<std::string> GetStringArgument(const EncodableMap& arguments,
                                             const char* key) {
  const auto it = arguments.find(EncodableValue(key));
  if (it == arguments.end()) {
    return std::nullopt;
  }
  return GetStringValue(it->second);
}

std::optional<EncodableList> GetListArgument(const EncodableMap& arguments,
                                             const char* key) {
  const auto it = arguments.find(EncodableValue(key));
  if (it == arguments.end()) {
    return std::nullopt;
  }
  if (const auto* list = std::get_if<EncodableList>(&it->second)) {
    return *list;
  }
  return std::nullopt;
}

bool IsCanceledHRESULT(HRESULT hr, const std::wstring& error_name) {
  return hr == HRESULT_FROM_WIN32(ERROR_CANCELLED) || hr == E_ABORT ||
         error_name.find(L"CANCEL") != std::wstring::npos;
}

std::wstring GetWebAuthnErrorName(HRESULT hr) {
  const wchar_t* error_name = WebAuthNGetErrorName(hr);
  if (error_name == nullptr) {
    return std::wstring();
  }
  return error_name;
}

std::string JsonEscape(const std::string& value) {
  std::string escaped;
  escaped.reserve(value.size() + 8);
  for (const unsigned char ch : value) {
    switch (ch) {
      case '\\':
        escaped += "\\\\";
        break;
      case '"':
        escaped += "\\\"";
        break;
      case '\b':
        escaped += "\\b";
        break;
      case '\f':
        escaped += "\\f";
        break;
      case '\n':
        escaped += "\\n";
        break;
      case '\r':
        escaped += "\\r";
        break;
      case '\t':
        escaped += "\\t";
        break;
      default:
        if (ch < 0x20) {
          const char hex_digits[] = "0123456789ABCDEF";
          escaped += "\\u00";
          escaped += hex_digits[(ch >> 4) & 0x0F];
          escaped += hex_digits[ch & 0x0F];
        } else {
          escaped.push_back(static_cast<char>(ch));
        }
        break;
    }
  }
  return escaped;
}

std::vector<uint8_t> BuildClientDataJson(const std::string& challenge,
                                         const std::string& origin) {
  const std::string json =
      "{\"type\":\"" + std::string(kWebAuthnTypeGet) + "\","
      "\"challenge\":\"" + JsonEscape(challenge) + "\","
      "\"origin\":\"" + JsonEscape(origin) + "\","
      "\"crossOrigin\":false}";
  return std::vector<uint8_t>(json.begin(), json.end());
}

int Base64UrlValue(unsigned char ch) {
  if (ch >= 'A' && ch <= 'Z') {
    return ch - 'A';
  }
  if (ch >= 'a' && ch <= 'z') {
    return ch - 'a' + 26;
  }
  if (ch >= '0' && ch <= '9') {
    return ch - '0' + 52;
  }
  if (ch == '-' || ch == '+') {
    return 62;
  }
  if (ch == '_' || ch == '/') {
    return 63;
  }
  return -1;
}

std::optional<std::vector<uint8_t>> Base64UrlDecode(const std::string& value) {
  std::vector<uint8_t> decoded;
  int buffer = 0;
  int bits_collected = 0;

  for (const unsigned char ch : value) {
    if (ch == '=') {
      break;
    }
    if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
      continue;
    }
    const int next = Base64UrlValue(ch);
    if (next < 0) {
      return std::nullopt;
    }
    buffer = (buffer << 6) | next;
    bits_collected += 6;
    if (bits_collected >= 8) {
      bits_collected -= 8;
      decoded.push_back(
          static_cast<uint8_t>((buffer >> bits_collected) & 0xFF));
    }
  }

  return decoded;
}

std::string Base64UrlEncode(const uint8_t* data, size_t length) {
  if (data == nullptr || length == 0) {
    return std::string();
  }

  std::string encoded;
  encoded.reserve(((length + 2) / 3) * 4);

  for (size_t index = 0; index < length; index += 3) {
    const uint32_t octet_a = data[index];
    const uint32_t octet_b = index + 1 < length ? data[index + 1] : 0;
    const uint32_t octet_c = index + 2 < length ? data[index + 2] : 0;
    const uint32_t triple = (octet_a << 16) | (octet_b << 8) | octet_c;

    encoded.push_back(kBase64UrlAlphabet[(triple >> 18) & 0x3F]);
    encoded.push_back(kBase64UrlAlphabet[(triple >> 12) & 0x3F]);
    if (index + 1 < length) {
      encoded.push_back(kBase64UrlAlphabet[(triple >> 6) & 0x3F]);
    }
    if (index + 2 < length) {
      encoded.push_back(kBase64UrlAlphabet[triple & 0x3F]);
    }
  }

  return encoded;
}

DWORD ParseTimeoutMilliseconds(const std::string& raw_timeout) {
  try {
    const unsigned long value = std::stoul(raw_timeout);
    if (value == 0) {
      return 300000;
    }
    return static_cast<DWORD>(std::min<unsigned long>(value, 300000));
  } catch (...) {
    return 300000;
  }
}

DWORD MapUserVerificationRequirement(const std::string& raw_value) {
  std::string value = raw_value;
  std::transform(value.begin(), value.end(), value.begin(),
                 [](unsigned char ch) {
                   return static_cast<char>(std::tolower(ch));
                 });
  if (value == "required") {
    return WEBAUTHN_USER_VERIFICATION_REQUIREMENT_REQUIRED;
  }
  if (value == "discouraged") {
    return WEBAUTHN_USER_VERIFICATION_REQUIREMENT_DISCOURAGED;
  }
  return WEBAUTHN_USER_VERIFICATION_REQUIREMENT_PREFERRED;
}

}  // namespace

PasskeyBridge::PasskeyBridge(flutter::BinaryMessenger* messenger,
                             HWND window_handle)
    : window_handle_(window_handle),
      channel_(std::make_unique<MethodChannel>(
          messenger, kChannelName,
          &flutter::StandardMethodCodec::GetInstance())) {
  channel_->SetMethodCallHandler(
      [this](const MethodCall& call, std::unique_ptr<MethodResult> result) {
        HandleMethodCall(call, std::move(result));
      });
}

PasskeyBridge::~PasskeyBridge() {
  if (channel_) {
    channel_->SetMethodCallHandler(nullptr);
  }
}

void PasskeyBridge::HandleMethodCall(const MethodCall& call,
                                     std::unique_ptr<MethodResult> result) {
  if (call.method_name() == "isAvailable") {
    HandleIsAvailable(std::move(result));
    return;
  }

  if (call.method_name() != "performAssertion") {
    result->NotImplemented();
    return;
  }

  const EncodableValue* raw_arguments = call.arguments();
  const auto* arguments =
      raw_arguments == nullptr ? nullptr : std::get_if<EncodableMap>(raw_arguments);
  if (arguments == nullptr) {
    result->Error("bad_args", "Passkey 参数缺失");
    return;
  }
  HandlePerformAssertion(*arguments, std::move(result));
}

void PasskeyBridge::HandleIsAvailable(std::unique_ptr<MethodResult> result) {
  result->Success(EncodableValue(IsAvailable()));
}

bool PasskeyBridge::IsAvailable() const {
  if (window_handle_ == nullptr || WebAuthNGetApiVersionNumber() < 1) {
    return false;
  }

  BOOL available = FALSE;
  const HRESULT hr =
      WebAuthNIsUserVerifyingPlatformAuthenticatorAvailable(&available);
  return SUCCEEDED(hr) && available == TRUE;
}

void PasskeyBridge::HandlePerformAssertion(
    const EncodableMap& arguments, std::unique_ptr<MethodResult> result) {
  if (!IsAvailable()) {
    result->Error("not_available", "当前设备未启用 Windows 原生 Passkey");
    return;
  }

  if (request_in_progress_) {
    result->Error("busy", "已有进行中的 Passkey 请求");
    return;
  }

  const std::optional<std::string> challenge =
      GetStringArgument(arguments, "challenge");
  const std::optional<std::string> rp_id = GetStringArgument(arguments, "rpId");
  const std::optional<std::string> origin =
      GetStringArgument(arguments, "origin");
  const std::optional<std::string> timeout =
      GetStringArgument(arguments, "timeout");
  const std::optional<std::string> user_verification =
      GetStringArgument(arguments, "userVerification");

  if (!challenge.has_value() || challenge->empty()) {
    result->Error("bad_args", "challenge 不能为空");
    return;
  }
  if (!rp_id.has_value() || rp_id->empty()) {
    result->Error("bad_args", "rpId 不能为空");
    return;
  }
  if (!origin.has_value() || origin->empty()) {
    result->Error("bad_args", "origin 不能为空");
    return;
  }

  const std::wstring rp_id_wide = Utf16FromUtf8(*rp_id);
  if (rp_id_wide.empty()) {
    result->Error("bad_args", "rpId 格式无效");
    return;
  }

  std::vector<std::vector<uint8_t>> credential_ids;
  std::vector<WEBAUTHN_CREDENTIAL> credentials;
  if (const auto allow_credentials =
          GetListArgument(arguments, "allowCredentials");
      allow_credentials.has_value()) {
    for (const EncodableValue& item : *allow_credentials) {
      const auto* credential_map = std::get_if<EncodableMap>(&item);
      if (credential_map == nullptr) {
        result->Error("bad_args", "allowCredentials 格式无效");
        return;
      }
      const std::optional<std::string> credential_id =
          GetStringArgument(*credential_map, "id");
      if (!credential_id.has_value() || credential_id->empty()) {
        result->Error("bad_args", "allowCredentials.id 不能为空");
        return;
      }

      const auto decoded_id = Base64UrlDecode(*credential_id);
      if (!decoded_id.has_value() || decoded_id->empty()) {
        result->Error("bad_args", "allowCredentials.id 编码无效");
        return;
      }

      credential_ids.push_back(*decoded_id);
    }
  }

  credentials.reserve(credential_ids.size());
  for (auto& credential_id : credential_ids) {
    WEBAUTHN_CREDENTIAL credential = {};
    credential.dwVersion = WEBAUTHN_CREDENTIAL_CURRENT_VERSION;
    credential.cbId = static_cast<DWORD>(credential_id.size());
    credential.pbId = credential_id.data();
    credential.pwszCredentialType = WEBAUTHN_CREDENTIAL_TYPE_PUBLIC_KEY;
    credentials.push_back(credential);
  }

  const std::vector<uint8_t> client_data_json =
      BuildClientDataJson(*challenge, *origin);

  WEBAUTHN_CLIENT_DATA client_data = {};
  client_data.dwVersion = WEBAUTHN_CLIENT_DATA_CURRENT_VERSION;
  client_data.cbClientDataJSON = static_cast<DWORD>(client_data_json.size());
  client_data.pbClientDataJSON =
      const_cast<PBYTE>(client_data_json.data());
  client_data.pwszHashAlgId = WEBAUTHN_HASH_ALGORITHM_SHA_256;

  WEBAUTHN_AUTHENTICATOR_GET_ASSERTION_OPTIONS options = {};
  options.dwVersion = WEBAUTHN_AUTHENTICATOR_GET_ASSERTION_OPTIONS_CURRENT_VERSION;
  options.dwTimeoutMilliseconds =
      ParseTimeoutMilliseconds(timeout.value_or("300000"));
  options.CredentialList.cCredentials = static_cast<DWORD>(credentials.size());
  options.CredentialList.pCredentials =
      credentials.empty() ? nullptr : credentials.data();
  options.dwAuthenticatorAttachment =
      WEBAUTHN_AUTHENTICATOR_ATTACHMENT_PLATFORM;
  options.dwUserVerificationRequirement =
      MapUserVerificationRequirement(user_verification.value_or("preferred"));

  PWEBAUTHN_ASSERTION assertion = nullptr;
  request_in_progress_ = true;
  const HRESULT hr = WebAuthNAuthenticatorGetAssertion(
      window_handle_, rp_id_wide.c_str(), &client_data, &options, &assertion);
  request_in_progress_ = false;

  if (FAILED(hr) || assertion == nullptr) {
    const std::wstring error_name = GetWebAuthnErrorName(hr);
    if (assertion != nullptr) {
      WebAuthNFreeAssertion(assertion);
    }
    if (IsCanceledHRESULT(hr, error_name)) {
      result->Error("canceled", "用户取消了 Passkey 验证");
      return;
    }

    const std::string detail = Utf8FromUtf16(error_name.c_str());
    result->Error("assertion_failed",
                  detail.empty() ? "Passkey 验证失败" : detail);
    return;
  }

  flutter::EncodableMap payload;
  payload[EncodableValue("credentialRawId")] = EncodableValue(Base64UrlEncode(
      assertion->Credential.pbId, assertion->Credential.cbId));
  payload[EncodableValue("clientDataJSON")] =
      EncodableValue(Base64UrlEncode(client_data_json.data(),
                                     client_data_json.size()));
  payload[EncodableValue("authenticatorData")] =
      EncodableValue(Base64UrlEncode(assertion->pbAuthenticatorData,
                                     assertion->cbAuthenticatorData));
  payload[EncodableValue("signature")] = EncodableValue(
      Base64UrlEncode(assertion->pbSignature, assertion->cbSignature));

  WebAuthNFreeAssertion(assertion);
  result->Success(EncodableValue(payload));
}

}  // namespace runner
