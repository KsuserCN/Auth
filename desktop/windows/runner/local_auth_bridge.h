#ifndef RUNNER_LOCAL_AUTH_BRIDGE_H_
#define RUNNER_LOCAL_AUTH_BRIDGE_H_

#include <flutter/encodable_value.h>
#include <flutter/method_channel.h>

#include <memory>

#include <windows.h>

namespace runner {

class LocalAuthBridge {
 public:
  LocalAuthBridge(flutter::BinaryMessenger* messenger, HWND window_handle);
  ~LocalAuthBridge();

  LocalAuthBridge(const LocalAuthBridge&) = delete;
  LocalAuthBridge& operator=(const LocalAuthBridge&) = delete;

 private:
  using EncodableValue = flutter::EncodableValue;
  using MethodChannel = flutter::MethodChannel<EncodableValue>;
  using MethodCall = flutter::MethodCall<EncodableValue>;
  using MethodResult = flutter::MethodResult<EncodableValue>;

  void HandleMethodCall(const MethodCall& call,
                        std::unique_ptr<MethodResult> result);
  void HandleIsAvailable(std::unique_ptr<MethodResult> result);
  void HandleAuthenticate(const flutter::EncodableMap& arguments,
                          std::unique_ptr<MethodResult> result);
  bool IsAvailable(std::string* error_message = nullptr) const;

  HWND window_handle_;
  bool request_in_progress_ = false;
  std::unique_ptr<MethodChannel> channel_;
};

}  // namespace runner

#endif  // RUNNER_LOCAL_AUTH_BRIDGE_H_
