import Cocoa
import FlutterMacOS

@main
class AppDelegate: FlutterAppDelegate {
  private var appDisplayName: String { desktopNativeText("Ksuser 统一认证中心") }
  private var menuTemplate: NSMenu?
  private var statusItem: NSStatusItem?
  private var isMenuBarVisible = false
  private var menuBarAuthenticated = false
  private var menuBarDisplayName = ""

  override func applicationDidFinishLaunching(_ notification: Notification) {
    super.applicationDidFinishLaunching(notification)
    NSApp.setActivationPolicy(.regular)
    configureMainMenu()
  }

  override func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
    return !isMenuBarVisible
  }

  override func applicationSupportsSecureRestorableState(_ app: NSApplication) -> Bool {
    return true
  }

  override func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool {
    if !flag {
      showMainWindow(sender)
    }
    return true
  }

  @objc private func showMainWindow(_ sender: Any?) {
    restoreRegularApplicationMode()
    hideStatusItem()
    NSApp.activate(ignoringOtherApps: true)
    mainFlutterWindow?.makeKeyAndOrderFront(sender)
  }

  func moveMainWindowToMenuBar() {
    guard let window = mainFlutterWindow else {
      return
    }
    enterMenuBarApplicationMode()
    showStatusItem()
    window.resignKey()
    window.orderOut(nil)
  }

  @objc private func moveMainWindowToMenuBarFromMenu(_ sender: Any?) {
    moveMainWindowToMenuBar()
  }

  @objc private func terminateFromStatusItem(_ sender: Any?) {
    hideStatusItem()
    NSApp.terminate(sender)
  }

  func updateMenuBarState(authenticated: Bool, displayName: String?, locale: String?) {
    let nextLocale = locale == "zh-Hans" ? "zh-Hans" : "en"
    if DesktopNativeLocalization.languageTag != nextLocale {
      DesktopNativeLocalization.languageTag = nextLocale
      configureMainMenu()
      mainFlutterWindow?.title = appDisplayName
      statusItem?.button?.toolTip = appDisplayName
    }
    menuBarAuthenticated = authenticated
    menuBarDisplayName = displayName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    rebuildStatusMenu()
  }

  func showMenuBarMessage(_ message: String, success: Bool) {
    let trimmed = message.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else {
      return
    }

    guard let button = statusItem?.button else {
      return
    }

    let viewController = NSViewController()
    let textField = NSTextField(labelWithString: trimmed)
    textField.textColor = success ? NSColor.labelColor : NSColor.systemRed
    textField.font = NSFont.systemFont(ofSize: 13, weight: .medium)
    textField.maximumNumberOfLines = 2
    textField.lineBreakMode = .byWordWrapping

    let container = NSView(frame: NSRect(x: 0, y: 0, width: 220, height: 54))
    textField.frame = NSRect(x: 14, y: 15, width: 192, height: 24)
    container.addSubview(textField)
    viewController.view = container

    let popover = NSPopover()
    popover.behavior = .transient
    popover.contentSize = container.frame.size
    popover.contentViewController = viewController
    popover.show(relativeTo: button.bounds, of: button, preferredEdge: .minY)

    DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) {
      if popover.isShown {
        popover.performClose(nil)
      }
    }
  }

  @objc private func refreshFromMenu(_ sender: Any?) {
    dispatchMenuCommand("refresh")
  }

  @objc private func openSettingsFromMenu(_ sender: Any?) {
    dispatchMenuCommand("openSettings")
  }

  @objc private func openAboutFromMenu(_ sender: Any?) {
    dispatchMenuCommand("openAbout")
  }

  @objc private func logoutFromMenu(_ sender: Any?) {
    dispatchMenuCommand("logout")
  }

  @objc private func showOverview(_ sender: Any?) {
    dispatchMenuCommand("showOverview")
  }

  @objc private func showProfile(_ sender: Any?) {
    dispatchMenuCommand("showProfile")
  }

  @objc private func showSecurity(_ sender: Any?) {
    dispatchMenuCommand("showSecurity")
  }

  @objc private func showDevices(_ sender: Any?) {
    dispatchMenuCommand("showDevices")
  }

  @objc private func showActivity(_ sender: Any?) {
    dispatchMenuCommand("showActivity")
  }

  @objc private func showUsageHelp(_ sender: Any?) {
    let alert = NSAlert()
    alert.messageText = appDisplayName
    alert.informativeText = desktopNativeText("- 登录页默认读取当前环境配置，不再暴露 API 或环境切换。\n    - Passkey 使用系统原生服务完成验证。\n    - 常用菜单：\n      • Command-, 打开设置\n      • Command-R 刷新数据\n      • Command-1~5 快速切换工作台模块\n      • Shift-Command-L 退出登录")
    alert.alertStyle = .informational
    alert.addButton(withTitle: desktopNativeText("知道了"))
    alert.runModal()
  }

  private func dispatchMenuCommand(_ command: String) {
    (mainFlutterWindow as? MainFlutterWindow)?.dispatchMenuCommand(command)
  }

  private func configureMainMenu() {
    // Keep an unmodified template with stable tags. Language changes rebuild
    // menus from this template rather than searching translated titles.
    if menuTemplate == nil, let original = NSApp.mainMenu?.copy() as? NSMenu {
      let tags = ["Edit": 1001, "View": 1002, "Window": 1003, "Help": 1004]
      for item in original.items { item.tag = tags[item.title] ?? 0 }
      menuTemplate = original
    }
    guard let mainMenu = menuTemplate?.copy() as? NSMenu else { return }
    NSApp.mainMenu = mainMenu
    if let appMenuItem = mainMenu.items.first {
      appMenuItem.title = appDisplayName
      configureApplicationMenu(appMenuItem.submenu)
    }
    if let editItem = mainMenu.items.first(where: { $0.tag == 1001 }) {
      editItem.title = desktopNativeText("编辑")
      localizeStandardMenu(editItem.submenu)
    }
    if let viewItem = mainMenu.items.first(where: { $0.tag == 1002 }) {
      viewItem.title = desktopNativeText("显示")
      configureViewMenu(viewItem.submenu)
    }
    if let windowItem = mainMenu.items.first(where: { $0.tag == 1003 }) {
      windowItem.title = desktopNativeText("窗口")
      configureWindowMenu(windowItem.submenu)
    }
    if let helpItem = mainMenu.items.first(where: { $0.tag == 1004 }) {
      helpItem.title = desktopNativeText("帮助")
      configureHelpMenu(helpItem.submenu)
    }
    let fileItem = NSMenuItem(title: desktopNativeText("文件"), action: nil, keyEquivalent: "")
    fileItem.tag = 1005
    let fileMenu = NSMenu(title: fileItem.title)
    fileItem.submenu = fileMenu
    mainMenu.insertItem(fileItem, at: 1)
    configureFileMenu(fileMenu)
    let accountItem = NSMenuItem(title: desktopNativeText("账户"), action: nil, keyEquivalent: "")
    accountItem.tag = 1006
    let accountMenu = NSMenu(title: accountItem.title)
    accountItem.submenu = accountMenu
    mainMenu.insertItem(accountItem, at: 2)
    configureAccountMenu(accountMenu)
  }

  private func localizeStandardMenu(_ menu: NSMenu?) {
    let titles = ["undo:": "撤销", "redo:": "重做", "cut:": "剪切", "copy:": "复制", "paste:": "粘贴", "selectAll:": "全选", "hide:": "隐藏", "hideOtherApplications:": "隐藏其他应用", "unhideAllApplications:": "显示全部", "terminate:": "退出应用"]
    let originalTitles: [String: String] = ["Paste and Match Style": "粘贴并匹配样式", "Delete": "删除", "Find": "查找", "Find…": "查找…", "Find and Replace…": "查找并替换…", "Find Next": "查找下一个", "Find Previous": "查找上一个", "Use Selection for Find": "使用所选内容查找", "Jump to Selection": "跳转到所选内容", "Spelling and Grammar": "拼写和语法", "Show Spelling and Grammar": "显示拼写和语法", "Check Document Now": "立即检查文稿", "Check Spelling While Typing": "键入时检查拼写", "Check Grammar With Spelling": "同时检查语法和拼写", "Correct Spelling Automatically": "自动纠正拼写", "Substitutions": "替换", "Show Substitutions": "显示替换", "Smart Copy/Paste": "智能拷贝/粘贴", "Smart Quotes": "智能引号", "Smart Dashes": "智能破折号", "Smart Links": "智能链接", "Data Detectors": "数据检测", "Text Replacement": "文本替换", "Transformations": "转换", "Make Upper Case": "转换为大写", "Make Lower Case": "转换为小写", "Capitalize": "首字母大写", "Speech": "语音", "Start Speaking": "开始朗读", "Stop Speaking": "停止朗读"]
    for item in menu?.items ?? [] {
      if let action = item.action, let key = titles[NSStringFromSelector(action)] {
        item.title = desktopNativeText(key)
      }
      if item.title == "Services" { item.title = desktopNativeText("服务") }
      if let chinese = originalTitles[item.title] { item.title = desktopNativeText(chinese) }
      item.submenu?.title = item.title
      localizeStandardMenu(item.submenu)
    }
  }

  private func configureStatusItemIfNeeded() {
    if statusItem != nil {
      return
    }

    let item = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
    if let button = item.button {
      let statusImage = (NSImage(named: "MenuBarIcon") ?? NSImage(named: NSImage.Name("MenuBarIcon")))?.copy() as? NSImage
      statusImage?.isTemplate = true
      button.image = statusImage
      button.image?.isTemplate = true
      button.imageScaling = .scaleProportionallyDown
      button.title = ""
      button.imagePosition = .imageOnly
      button.toolTip = appDisplayName
    }

    statusItem = item
    rebuildStatusMenu()
  }

  private func showStatusItem() {
    configureStatusItemIfNeeded()
    isMenuBarVisible = true
  }

  private func hideStatusItem() {
    if let item = statusItem {
      NSStatusBar.system.removeStatusItem(item)
      statusItem = nil
    }
    isMenuBarVisible = false
  }

  private func enterMenuBarApplicationMode() {
    NSApp.setActivationPolicy(.accessory)
  }

  private func restoreRegularApplicationMode() {
    NSApp.setActivationPolicy(.regular)
  }

  private func rebuildStatusMenu() {
    guard let item = statusItem else {
      return
    }

    let menu = NSMenu()

    if menuBarAuthenticated {
      let displayName = menuBarDisplayName.isEmpty ? desktopNativeText("已登录") : desktopNativeText("已登录：{menuBarDisplayName}", [String(menuBarDisplayName)])
      let accountItem = NSMenuItem(title: displayName, action: nil, keyEquivalent: "")
      accountItem.isEnabled = false
      menu.addItem(accountItem)

      let showItem = NSMenuItem(title: desktopNativeText("显示主窗口"), action: #selector(showMainWindow(_:)), keyEquivalent: "")
      showItem.target = self
      menu.addItem(showItem)
    } else {
      let loginItem = NSMenuItem(title: desktopNativeText("点击登录"), action: #selector(showMainWindow(_:)), keyEquivalent: "")
      loginItem.target = self
      menu.addItem(loginItem)
    }

    menu.addItem(NSMenuItem.separator())

    let refreshItem = NSMenuItem(title: desktopNativeText("刷新数据"), action: #selector(refreshFromMenu(_:)), keyEquivalent: "")
    refreshItem.target = self

    let settingsItem = NSMenuItem(title: desktopNativeText("设置..."), action: #selector(openSettingsFromMenu(_:)), keyEquivalent: "")
    settingsItem.target = self

    let quitItem = NSMenuItem(title: desktopNativeText("退出应用"), action: #selector(terminateFromStatusItem(_:)), keyEquivalent: "")
    quitItem.target = self

    menu.addItem(refreshItem)
    menu.addItem(settingsItem)
    if menuBarAuthenticated {
      let logoutItem = NSMenuItem(title: desktopNativeText("退出登录"), action: #selector(logoutFromMenu(_:)), keyEquivalent: "")
      logoutItem.target = self
      menu.addItem(logoutItem)
    }
    menu.addItem(NSMenuItem.separator())
    menu.addItem(quitItem)

    item.menu = menu
  }

  private func configureApplicationMenu(_ menu: NSMenu?) {
    guard let menu else {
      return
    }

    localizeStandardMenu(menu)
    if let aboutItem = menu.items.first {
      aboutItem.title = desktopNativeText("关于 {appDisplayName}", [String(appDisplayName)])
      aboutItem.target = self
      aboutItem.action = #selector(openAboutFromMenu(_:))
    }

    if let preferencesItem = menu.items.first(where: { $0.keyEquivalent == "," }) {
      preferencesItem.title = desktopNativeText("设置...")
      preferencesItem.target = self
      preferencesItem.action = #selector(openSettingsFromMenu(_:))
      preferencesItem.keyEquivalent = ","
      preferencesItem.keyEquivalentModifierMask = [.command]
    }

    let refreshItem = NSMenuItem(title: desktopNativeText("刷新数据"), action: #selector(refreshFromMenu(_:)), keyEquivalent: "r")
    refreshItem.keyEquivalentModifierMask = [.command]
    refreshItem.target = self

    let logoutItem = NSMenuItem(title: desktopNativeText("退出登录"), action: #selector(logoutFromMenu(_:)), keyEquivalent: "l")
    logoutItem.keyEquivalentModifierMask = [.command, .shift]
    logoutItem.target = self

    menu.insertItem(NSMenuItem.separator(), at: 2)
    menu.insertItem(refreshItem, at: 3)
    menu.insertItem(logoutItem, at: 4)
  }

  private func configureFileMenu(_ menu: NSMenu) {
    menu.removeAllItems()

    let showWindowItem = NSMenuItem(title: desktopNativeText("显示主窗口"), action: #selector(showMainWindow(_:)), keyEquivalent: "0")
    showWindowItem.keyEquivalentModifierMask = [.command]
    showWindowItem.target = self

    let refreshItem = NSMenuItem(title: desktopNativeText("刷新数据"), action: #selector(refreshFromMenu(_:)), keyEquivalent: "r")
    refreshItem.keyEquivalentModifierMask = [.command]
    refreshItem.target = self

    let logoutItem = NSMenuItem(title: desktopNativeText("退出登录"), action: #selector(logoutFromMenu(_:)), keyEquivalent: "l")
    logoutItem.keyEquivalentModifierMask = [.command, .shift]
    logoutItem.target = self

    let menuBarItem = NSMenuItem(title: desktopNativeText("收起到菜单栏"), action: #selector(moveMainWindowToMenuBarFromMenu(_:)), keyEquivalent: "m")
    menuBarItem.keyEquivalentModifierMask = [.command]
    menuBarItem.target = self

    let closeItem = NSMenuItem(title: desktopNativeText("关闭窗口"), action: #selector(NSWindow.performClose(_:)), keyEquivalent: "w")

    menu.addItem(showWindowItem)
    menu.addItem(refreshItem)
    menu.addItem(logoutItem)
    menu.addItem(NSMenuItem.separator())
    menu.addItem(menuBarItem)
    menu.addItem(closeItem)
  }

  private func configureAccountMenu(_ menu: NSMenu) {
    menu.removeAllItems()

    let refreshItem = NSMenuItem(title: desktopNativeText("刷新数据"), action: #selector(refreshFromMenu(_:)), keyEquivalent: "r")
    refreshItem.keyEquivalentModifierMask = [.command]
    refreshItem.target = self

    let settingsItem = NSMenuItem(title: desktopNativeText("设置..."), action: #selector(openSettingsFromMenu(_:)), keyEquivalent: ",")
    settingsItem.keyEquivalentModifierMask = [.command]
    settingsItem.target = self

    let logoutItem = NSMenuItem(title: desktopNativeText("退出登录"), action: #selector(logoutFromMenu(_:)), keyEquivalent: "l")
    logoutItem.keyEquivalentModifierMask = [.command, .shift]
    logoutItem.target = self

    menu.addItem(refreshItem)
    menu.addItem(settingsItem)
    menu.addItem(NSMenuItem.separator())
    menu.addItem(logoutItem)
  }

  private func configureViewMenu(_ menu: NSMenu?) {
    guard let menu else {
      return
    }

    menu.removeAllItems()

    menu.addItem(makeSectionItem(title: desktopNativeText("账号总览"), action: #selector(showOverview(_:)), key: "1"))
    menu.addItem(makeSectionItem(title: desktopNativeText("账号资料"), action: #selector(showProfile(_:)), key: "2"))
    menu.addItem(makeSectionItem(title: desktopNativeText("安全设置"), action: #selector(showSecurity(_:)), key: "3"))
    menu.addItem(makeSectionItem(title: desktopNativeText("设备管理"), action: #selector(showDevices(_:)), key: "4"))
    menu.addItem(makeSectionItem(title: desktopNativeText("操作日志"), action: #selector(showActivity(_:)), key: "5"))
    menu.addItem(NSMenuItem.separator())

    let fullScreenItem = NSMenuItem(title: desktopNativeText("进入全屏"), action: #selector(NSWindow.toggleFullScreen(_:)), keyEquivalent: "f")
    fullScreenItem.keyEquivalentModifierMask = [.command, .control]
    menu.addItem(fullScreenItem)
  }

  private func configureWindowMenu(_ menu: NSMenu?) {
    guard let menu else {
      return
    }

    if let minimizeItem = menu.items.first(where: { $0.keyEquivalent == "m" }) {
      minimizeItem.title = desktopNativeText("最小化")
    }
    if let zoomItem = menu.items.first(where: { $0.action == #selector(NSWindow.performZoom(_:)) }) {
      zoomItem.title = desktopNativeText("缩放")
    }
    if let frontItem = menu.items.first(where: { $0.action == #selector(NSApplication.arrangeInFront(_:)) }) {
      frontItem.title = desktopNativeText("全部移到前台")
    }
  }

  private func configureHelpMenu(_ menu: NSMenu?) {
    guard let menu else {
      return
    }

    menu.removeAllItems()

    let aboutItem = NSMenuItem(title: desktopNativeText("关于 {appDisplayName}", [String(appDisplayName)]), action: #selector(openAboutFromMenu(_:)), keyEquivalent: "")
    aboutItem.target = self

    let helpItem = NSMenuItem(title: desktopNativeText("使用说明"), action: #selector(showUsageHelp(_:)), keyEquivalent: "?")
    helpItem.keyEquivalentModifierMask = [.command, .shift]
    helpItem.target = self
    menu.addItem(aboutItem)
    menu.addItem(NSMenuItem.separator())
    menu.addItem(helpItem)
  }

  private func makeSectionItem(title: String, action: Selector, key: String) -> NSMenuItem {
    let item = NSMenuItem(title: title, action: action, keyEquivalent: key)
    item.keyEquivalentModifierMask = [.command]
    item.target = self
    return item
  }
}

/// Native labels use the same effective locale as Flutter, including overrides.
enum DesktopNativeLocalization {
  static var languageTag = Locale.preferredLanguages.first?.lowercased().hasPrefix("zh") == true ? "zh-Hans" : "en"
  static let english: [String: String] = [
    "Ksuser 统一认证中心": "Ksuser Authentication Center",
    "- 登录页默认读取当前环境配置，不再暴露 API 或环境切换。\n    - Passkey 使用系统原生服务完成验证。\n    - 常用菜单：\n      • Command-, 打开设置\n      • Command-R 刷新数据\n      • Command-1~5 快速切换工作台模块\n      • Shift-Command-L 退出登录": "- The sign-in page uses the current environment configuration.\n- Passkey verification uses the system service.\n- Keyboard shortcuts:\n  • Command-, Open settings\n  • Command-R Refresh data\n  • Command-1–5 Switch workspace sections\n  • Shift-Command-L Sign out",
    "知道了": "OK",
    "编辑": "Edit",
    "显示": "View",
    "窗口": "Window",
    "帮助": "Help",
    "文件": "File",
    "账户": "Account",
    "已登录": "Signed in",
    "已登录：{menuBarDisplayName}": "Signed in: {menuBarDisplayName}",
    "显示主窗口": "Show main window",
    "点击登录": "Click to sign in",
    "刷新数据": "Refresh data",
    "设置...": "Settings...",
    "退出应用": "Quit app",
    "退出登录": "Sign out",
    "关于 {appDisplayName}": "About {appDisplayName}",
    "收起到菜单栏": "Minimize to menu bar",
    "关闭窗口": "Close window",
    "账号总览": "Account overview",
    "账号资料": "Account profile",
    "安全设置": "Security settings",
    "设备管理": "Device management",
    "操作日志": "Activity logs",
    "进入全屏": "Enter full screen",
    "最小化": "Minimize",
    "缩放": "Zoom",
    "全部移到前台": "Bring all to front",
    "使用说明": "User guide",
    "未配置 macOS Apple 登录 Client ID": "macOS Sign in with Apple client ID is not configured",
    "已有进行中的 Apple 登录请求": "A Sign in with Apple request is already in progress",
    "Apple 登录 challenge 参数缺失": "Apple sign-in challenge is missing",
    "请返回应用后重新发起 Apple 登录": "Return to the app and start Sign in with Apple again",
    "Apple 返回的登录凭据无效": "Apple returned invalid sign-in credentials",
    "Apple 返回的登录信息不完整或 state 校验失败": "Apple returned incomplete sign-in information or state verification failed",
    "已取消 Apple 登录": "Sign in with Apple canceled",
    "菜单栏状态参数缺失": "Menu bar state parameters are missing",
    "菜单栏提示参数缺失": "Menu bar message parameters are missing",
    "当前未找到可收起的窗口": "No window is available to minimize",
    "请先验证身份": "Verify your identity first",
    "当前设备不支持本地认证": "This device does not support local authentication",
    "用户取消了本地认证": "Local authentication canceled",
    "本地认证失败": "Local authentication failed",
    "Passkey 需要 macOS 13.5 或更高版本": "Passkeys require macOS 13.5 or later",
    "已有进行中的 Passkey 请求": "A passkey request is already in progress",
    "Passkey 参数缺失": "Passkey parameters are missing",
    "challenge 不能为空": "Challenge cannot be empty",
    "rpId 不能为空": "RP ID cannot be empty",
    "Passkey origin 未配置，请检查 FLUTTER_PASSKEY_ORIGIN": "Passkey origin is not configured. Check FLUTTER_PASSKEY_ORIGIN",
    "challenge 格式无效": "Invalid challenge format",
    "userId 不能为空": "User ID cannot be empty",
    "userId 格式无效": "Invalid user ID format",
    "未获取到有效的 Passkey 断言": "No valid passkey assertion was received",
    "用户取消了 Passkey 验证": "Passkey verification canceled",
    "Passkey 返回了无效响应": "Passkey returned an invalid response",
    "系统未处理当前 Passkey 请求": "The system did not handle this passkey request",
    "系统当前无法展示 Passkey 验证界面": "The system cannot display passkey verification now",
    "Passkey 验证失败": "Passkey verification failed",
    "未获取到有效的 Passkey 注册凭证": "No valid passkey registration credential was received",
    "用户取消了 Passkey 创建": "Passkey creation canceled",
    "Passkey 返回了无效注册响应": "Passkey returned an invalid registration response",
    "系统未处理当前 Passkey 创建请求": "The system did not handle this passkey creation request",
    "系统当前无法展示 Passkey 创建界面": "The system cannot display passkey creation now",
    "Passkey 创建失败": "Could not create passkey",
    "撤销": "Undo",
    "重做": "Redo",
    "剪切": "Cut",
    "复制": "Copy",
    "粘贴": "Paste",
    "全选": "Select all",
    "隐藏": "Hide",
    "隐藏其他应用": "Hide other apps",
    "显示全部": "Show all",
    "服务": "Services",
    "粘贴并匹配样式": "Paste and Match Style",
    "删除": "Delete",
    "查找": "Find",
    "查找…": "Find…",
    "查找并替换…": "Find and Replace…",
    "查找下一个": "Find Next",
    "查找上一个": "Find Previous",
    "使用所选内容查找": "Use Selection for Find",
    "跳转到所选内容": "Jump to Selection",
    "拼写和语法": "Spelling and Grammar",
    "显示拼写和语法": "Show Spelling and Grammar",
    "立即检查文稿": "Check Document Now",
    "键入时检查拼写": "Check Spelling While Typing",
    "同时检查语法和拼写": "Check Grammar With Spelling",
    "自动纠正拼写": "Correct Spelling Automatically",
    "替换": "Substitutions",
    "显示替换": "Show Substitutions",
    "智能拷贝/粘贴": "Smart Copy/Paste",
    "智能引号": "Smart Quotes",
    "智能破折号": "Smart Dashes",
    "智能链接": "Smart Links",
    "数据检测": "Data Detectors",
    "文本替换": "Text Replacement",
    "转换": "Transformations",
    "转换为大写": "Make Upper Case",
    "转换为小写": "Make Lower Case",
    "首字母大写": "Capitalize",
    "语音": "Speech",
    "开始朗读": "Start Speaking",
    "停止朗读": "Stop Speaking",
  ]
}

func desktopNativeText(_ chinese: String, _ arguments: [String] = []) -> String {
  var text = DesktopNativeLocalization.languageTag == "zh-Hans" ? chinese : (DesktopNativeLocalization.english[chinese] ?? chinese)
  let regex = try! NSRegularExpression(pattern: "\\{([^}]+)\\}")
  let source = chinese as NSString
  let placeholders = regex.matches(in: chinese, range: NSRange(location: 0, length: source.length)).map { source.substring(with: $0.range(at: 1)) }
  for (index, name) in placeholders.enumerated() where index < arguments.count {
    text = text.replacingOccurrences(of: "{\(name)}", with: arguments[index])
  }
  return text
}
