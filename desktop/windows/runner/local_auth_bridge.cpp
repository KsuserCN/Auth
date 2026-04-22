#include "local_auth_bridge.h"

#include <flutter/standard_method_codec.h>

#include <optional>
#include <string>

#include <roapi.h>
#include <userconsentverifierinterop.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Security.Credentials.UI.h>
#include <winrt/base.h>

namespace runner {
namespace {

using EncodableValue = flutter::EncodableValue;
using EncodableMap = flutter::EncodableMap;
using UserConsentVerificationResult =
    winrt::Windows::Security::Credentials::UI::UserConsentVerificationResult;
using UserConsentVerifier =
    winrt::Windows::Security::Credentials::UI::UserConsentVerifier;
using UserConsentVerifierAvailability =
    winrt::Windows::Security::Credentials::UI::UserConsentVerifierAvailability;

constexpr char kChannelName[] = "ksuser/local_auth";

std::optional<std::string> GetStringArgument(const EncodableMap& arguments,
                                             const char* key) {
  const auto it = arguments.find(EncodableValue(key));
  if (it == arguments.end()) {
    return std::nullopt;
  }
  if (const auto* str = std::get_if<std::string>(&it->second)) {
    return *str;
  }
  return std::nullopt;
}

std::string NormalizeReason(std::optional<std::string> raw_reason) {
  if (!raw_reason.has_value()) {
    return "Please verify your identity";
  }

  std::string reason = *raw_reason;
  const size_t start = reason.find_first_not_of(" \t\r\n");
  if (start == std::string::npos) {
    return "Please verify your identity";
  }
  const size_t end = reason.find_last_not_of(" \t\r\n");
  return reason.substr(start, end - start + 1);
}

std::string MessageForAvailability(UserConsentVerifierAvailability availability) {
  switch (availability) {
    case UserConsentVerifierAvailability::Available:
      return std::string();
    case UserConsentVerifierAvailability::DeviceBusy:
      return "Windows authentication device is busy. Try again later.";
    case UserConsentVerifierAvailability::DeviceNotPresent:
      return "No supported Windows authentication method is available on this device.";
    case UserConsentVerifierAvailability::DisabledByPolicy:
      return "Windows authentication is disabled by policy.";
    case UserConsentVerifierAvailability::NotConfiguredForUser:
      return "Set up Windows Hello, PIN, or another supported sign-in method first.";
    default:
      return "Windows authentication is unavailable on this device.";
  }
}

std::string MessageForVerificationResult(UserConsentVerificationResult result) {
  switch (result) {
    case UserConsentVerificationResult::Verified:
      return std::string();
    case UserConsentVerificationResult::DeviceBusy:
      return "Windows authentication device is busy. Try again later.";
    case UserConsentVerificationResult::DeviceNotPresent:
      return "No supported Windows authentication method is available on this device.";
    case UserConsentVerificationResult::DisabledByPolicy:
      return "Windows authentication is disabled by policy.";
    case UserConsentVerificationResult::NotConfiguredForUser:
      return "Set up Windows Hello, PIN, or another supported sign-in method first.";
    case UserConsentVerificationResult::RetriesExhausted:
      return "Too many failed verification attempts. Try again later.";
    case UserConsentVerificationResult::Canceled:
      return "The user canceled Windows authentication.";
    default:
      return "Windows authentication failed.";
  }
}

std::string MessageForHresult(const winrt::hresult_error& error,
                              const char* fallback) {
  const std::wstring message = error.message().c_str();
  if (!message.empty()) {
    return winrt::to_string(message);
  }
  return fallback;
}

bool EnsureInteropAvailable() {
  try {
    const winrt::hstring class_name = winrt::name_of<UserConsentVerifier>();
    winrt::com_ptr<IUserConsentVerifierInterop> interop;
    winrt::check_hresult(::RoGetActivationFactory(
        winrt::get_abi(class_name), __uuidof(IUserConsentVerifierInterop),
        interop.put_void()));
    return interop != nullptr;
  } catch (...) {
    return false;
  }
}

}  // namespace

LocalAuthBridge::LocalAuthBridge(flutter::BinaryMessenger* messenger,
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

LocalAuthBridge::~LocalAuthBridge() {
  if (channel_) {
    channel_->SetMethodCallHandler(nullptr);
  }
}

void LocalAuthBridge::HandleMethodCall(const MethodCall& call,
                                       std::unique_ptr<MethodResult> result) {
  if (call.method_name() == "isAvailable") {
    HandleIsAvailable(std::move(result));
    return;
  }

  if (call.method_name() != "authenticate") {
    result->NotImplemented();
    return;
  }

  const EncodableValue* raw_arguments = call.arguments();
  const auto* arguments =
      raw_arguments == nullptr ? nullptr : std::get_if<EncodableMap>(raw_arguments);
  if (arguments == nullptr) {
    result->Error("bad_args", "Missing local authentication arguments.");
    return;
  }
  HandleAuthenticate(*arguments, std::move(result));
}

bool LocalAuthBridge::IsAvailable(std::string* error_message) const {
  if (window_handle_ == nullptr || !EnsureInteropAvailable()) {
    if (error_message != nullptr) {
      *error_message = "Windows authentication is unavailable on this device.";
    }
    return false;
  }

  try {
    const UserConsentVerifierAvailability availability =
        UserConsentVerifier::CheckAvailabilityAsync().get();
    const std::string message = MessageForAvailability(availability);
    if (error_message != nullptr) {
      *error_message = message;
    }
    return availability == UserConsentVerifierAvailability::Available;
  } catch (const winrt::hresult_error& error) {
    if (error_message != nullptr) {
      *error_message = MessageForHresult(
          error, "Windows authentication is unavailable on this device.");
    }
    return false;
  }
}

void LocalAuthBridge::HandleIsAvailable(std::unique_ptr<MethodResult> result) {
  result->Success(EncodableValue(IsAvailable()));
}

void LocalAuthBridge::HandleAuthenticate(
    const EncodableMap& arguments, std::unique_ptr<MethodResult> result) {
  if (request_in_progress_) {
    result->Error("failed", "Another Windows authentication request is already in progress.");
    return;
  }

  std::string availability_message;
  if (!IsAvailable(&availability_message)) {
    result->Error("not_available",
                  availability_message.empty() ? "Windows authentication is unavailable on this device."
                                                : availability_message);
    return;
  }

  const std::string reason = NormalizeReason(GetStringArgument(arguments, "reason"));

  try {
    const winrt::hstring class_name = winrt::name_of<UserConsentVerifier>();
    winrt::com_ptr<IUserConsentVerifierInterop> interop;
    winrt::check_hresult(::RoGetActivationFactory(
        winrt::get_abi(class_name), __uuidof(IUserConsentVerifierInterop),
        interop.put_void()));

    const winrt::hstring reason_text = winrt::to_hstring(reason);
    using Operation =
        winrt::Windows::Foundation::IAsyncOperation<UserConsentVerificationResult>;
    Operation operation{nullptr};

    request_in_progress_ = true;
    winrt::check_hresult(interop->RequestVerificationForWindowAsync(
        window_handle_, winrt::get_abi(reason_text),
        winrt::guid_of<Operation>(),
        reinterpret_cast<void**>(winrt::put_abi(operation))));
    const UserConsentVerificationResult verification_result = operation.get();
    request_in_progress_ = false;

    if (verification_result == UserConsentVerificationResult::Verified) {
      result->Success(EncodableValue(true));
      return;
    }

    const std::string message = MessageForVerificationResult(verification_result);
    if (verification_result == UserConsentVerificationResult::Canceled) {
      result->Error("canceled", message);
      return;
    }
    if (verification_result == UserConsentVerificationResult::NotConfiguredForUser ||
        verification_result == UserConsentVerificationResult::DeviceNotPresent) {
      result->Error("not_available", message);
      return;
    }
    result->Error("failed", message);
  } catch (const winrt::hresult_error& error) {
    request_in_progress_ = false;
    result->Error("failed",
                  MessageForHresult(error, "Windows authentication failed."));
  }
}

}  // namespace runner
