#ifndef RUNNER_FLUTTER_WINDOW_H_
#define RUNNER_FLUTTER_WINDOW_H_

#include <flutter/dart_project.h>
#include <flutter/encodable_value.h>
#include <flutter/flutter_view_controller.h>
#include <flutter/method_channel.h>

#include <memory>

#include "local_auth_bridge.h"
#include "passkey_bridge.h"
#include "win32_window.h"

// A window that does nothing but host a Flutter view.
class FlutterWindow : public Win32Window {
 public:
  // Creates a new FlutterWindow hosting a Flutter view running |project|.
  explicit FlutterWindow(const flutter::DartProject& project);
  virtual ~FlutterWindow();

 protected:
  // Win32Window:
  bool OnCreate() override;
  void OnDestroy() override;
  LRESULT MessageHandler(HWND window, UINT const message, WPARAM const wparam,
                         LPARAM const lparam) noexcept override;

 private:
  bool MoveToTray();
  bool AddTrayIcon();
  void RemoveTrayIcon();
  void ShowFromTray();
  void ShowTrayMenu();

  // The project to run.
  flutter::DartProject project_;

  // The Flutter instance hosted by this window.
  std::unique_ptr<flutter::FlutterViewController> flutter_controller_;

  // Windows native Passkey bridge exposed to Flutter.
  std::unique_ptr<runner::PasskeyBridge> passkey_bridge_;

  // Windows system local-auth bridge exposed to Flutter.
  std::unique_ptr<runner::LocalAuthBridge> local_auth_bridge_;

  // Native system tray controls used to keep the authenticated session alive.
  std::unique_ptr<flutter::MethodChannel<flutter::EncodableValue>>
      window_control_channel_;
  UINT taskbar_created_message_ = 0;
  HWND tray_icon_window_ = nullptr;
  bool tray_icon_added_ = false;
  bool is_in_tray_ = false;
};

#endif  // RUNNER_FLUTTER_WINDOW_H_
