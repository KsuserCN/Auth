#include "flutter_window.h"

#include <flutter/standard_method_codec.h>

#include <optional>
#include <shellapi.h>

#include "flutter/generated_plugin_registrant.h"
#include "resource.h"

namespace {

constexpr char kWindowControlChannelName[] = "ksuser/window_control";
constexpr UINT kTrayIconMessage = WM_APP + 1;
constexpr UINT kTrayIconId = 1;
constexpr UINT kShowWindowCommand = 1001;
constexpr UINT kQuitCommand = 1002;

}  // namespace

FlutterWindow::FlutterWindow(const flutter::DartProject& project)
    : project_(project) {}

FlutterWindow::~FlutterWindow() {}

bool FlutterWindow::OnCreate() {
  if (!Win32Window::OnCreate()) {
    return false;
  }

  RECT frame = GetClientArea();

  // The size here must match the window dimensions to avoid unnecessary surface
  // creation / destruction in the startup path.
  flutter_controller_ = std::make_unique<flutter::FlutterViewController>(
      frame.right - frame.left, frame.bottom - frame.top, project_);
  // Ensure that basic setup of the controller was successful.
  if (!flutter_controller_->engine() || !flutter_controller_->view()) {
    return false;
  }
  RegisterPlugins(flutter_controller_->engine());
  passkey_bridge_ = std::make_unique<runner::PasskeyBridge>(
      flutter_controller_->engine()->messenger(), GetHandle());
  local_auth_bridge_ = std::make_unique<runner::LocalAuthBridge>(
      flutter_controller_->engine()->messenger(), GetHandle());
  window_control_channel_ = std::make_unique<
      flutter::MethodChannel<flutter::EncodableValue>>(
      flutter_controller_->engine()->messenger(), kWindowControlChannelName,
      &flutter::StandardMethodCodec::GetInstance());
  window_control_channel_->SetMethodCallHandler(
      [this](const auto& call, auto result) {
        if (call.method_name() != "moveToTray") {
          result->NotImplemented();
          return;
        }
        if (!MoveToTray()) {
          result->Error("tray_unavailable",
                        "无法创建系统托盘图标，请检查 Windows 托盘设置。");
          return;
        }
        result->Success(flutter::EncodableValue(true));
      });
  taskbar_created_message_ = RegisterWindowMessageW(L"TaskbarCreated");
  SetChildContent(flutter_controller_->view()->GetNativeWindow());

  flutter_controller_->engine()->SetNextFrameCallback([&]() {
    this->Show();
  });

  // Flutter can complete the first frame before the "show window" callback is
  // registered. The following call ensures a frame is pending to ensure the
  // window is shown. It is a no-op if the first frame hasn't completed yet.
  flutter_controller_->ForceRedraw();

  return true;
}

void FlutterWindow::OnDestroy() {
  if (window_control_channel_) {
    window_control_channel_->SetMethodCallHandler(nullptr);
    window_control_channel_.reset();
  }
  RemoveTrayIcon();
  is_in_tray_ = false;
  if (local_auth_bridge_) {
    local_auth_bridge_.reset();
  }
  if (passkey_bridge_) {
    passkey_bridge_.reset();
  }
  if (flutter_controller_) {
    flutter_controller_ = nullptr;
  }

  Win32Window::OnDestroy();
}

LRESULT
FlutterWindow::MessageHandler(HWND hwnd, UINT const message,
                              WPARAM const wparam,
                              LPARAM const lparam) noexcept {
  if (taskbar_created_message_ != 0 && message == taskbar_created_message_) {
    if (is_in_tray_) {
      tray_icon_added_ = false;
      AddTrayIcon();
    }
    return 0;
  }

  if (message == kTrayIconMessage) {
    switch (lparam) {
      case WM_LBUTTONUP:
        ShowFromTray();
        return 0;
      case WM_RBUTTONUP:
      case WM_CONTEXTMENU:
        ShowTrayMenu();
        return 0;
    }
  }

  // Give Flutter, including plugins, an opportunity to handle window messages.
  if (flutter_controller_) {
    std::optional<LRESULT> result =
        flutter_controller_->HandleTopLevelWindowProc(hwnd, message, wparam,
                                                      lparam);
    if (result) {
      return *result;
    }
  }

  switch (message) {
    case WM_FONTCHANGE:
      flutter_controller_->engine()->ReloadSystemFonts();
      break;
  }

  return Win32Window::MessageHandler(hwnd, message, wparam, lparam);
}

bool FlutterWindow::MoveToTray() {
  if (GetHandle() == nullptr || !AddTrayIcon()) {
    return false;
  }

  is_in_tray_ = true;
  ShowWindow(GetHandle(), SW_HIDE);
  return true;
}

bool FlutterWindow::AddTrayIcon() {
  if (tray_icon_added_) {
    return true;
  }

  HWND window = GetHandle();
  if (window == nullptr) {
    return false;
  }

  NOTIFYICONDATAW tray_icon{};
  tray_icon.cbSize = sizeof(tray_icon);
  tray_icon.hWnd = window;
  tray_icon.uID = kTrayIconId;
  tray_icon.uFlags = NIF_ICON | NIF_MESSAGE | NIF_TIP;
  tray_icon.uCallbackMessage = kTrayIconMessage;
  tray_icon.hIcon = static_cast<HICON>(LoadImageW(
      GetModuleHandleW(nullptr), MAKEINTRESOURCEW(IDI_APP_ICON), IMAGE_ICON, 0,
      0, LR_DEFAULTSIZE | LR_SHARED));
  wcscpy_s(tray_icon.szTip, L"Ksuser安全");

  if (tray_icon.hIcon == nullptr ||
      !Shell_NotifyIconW(NIM_ADD, &tray_icon)) {
    return false;
  }

  tray_icon_window_ = window;
  tray_icon_added_ = true;
  return true;
}

void FlutterWindow::RemoveTrayIcon() {
  if (!tray_icon_added_) {
    return;
  }

  NOTIFYICONDATAW tray_icon{};
  tray_icon.cbSize = sizeof(tray_icon);
  tray_icon.hWnd = tray_icon_window_;
  tray_icon.uID = kTrayIconId;
  Shell_NotifyIconW(NIM_DELETE, &tray_icon);
  tray_icon_window_ = nullptr;
  tray_icon_added_ = false;
}

void FlutterWindow::ShowFromTray() {
  HWND window = GetHandle();
  if (window == nullptr) {
    return;
  }

  is_in_tray_ = false;
  RemoveTrayIcon();
  ShowWindow(window, SW_RESTORE);
  SetForegroundWindow(window);
}

void FlutterWindow::ShowTrayMenu() {
  HMENU menu = CreatePopupMenu();
  if (menu == nullptr) {
    return;
  }

  AppendMenuW(menu, MF_STRING, kShowWindowCommand, L"显示主窗口");
  AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
  AppendMenuW(menu, MF_STRING, kQuitCommand, L"退出应用");

  POINT cursor{};
  GetCursorPos(&cursor);
  SetForegroundWindow(GetHandle());
  const UINT command = TrackPopupMenu(
      menu, TPM_RETURNCMD | TPM_NONOTIFY | TPM_RIGHTBUTTON, cursor.x,
      cursor.y, 0, GetHandle(), nullptr);
  DestroyMenu(menu);
  PostMessageW(GetHandle(), WM_NULL, 0, 0);

  if (command == kShowWindowCommand) {
    ShowFromTray();
  } else if (command == kQuitCommand) {
    PostMessageW(GetHandle(), WM_CLOSE, 0, 0);
  }
}
