import Cocoa
import FlutterMacOS

@main
class AppDelegate: FlutterAppDelegate {
  private let appDisplayName =
    (Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String) ??
    (Bundle.main.object(forInfoDictionaryKey: kCFBundleNameKey as String) as? String) ??
    "Ksuser认证中心"
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

  func updateMenuBarState(authenticated: Bool, displayName: String?) {
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
    alert.informativeText = """
    - 登录页默认读取当前环境配置，不再暴露 API 或环境切换。
    - Passkey 会自动拉起默认浏览器完成验证。
    - 常用菜单：
      • Command-, 打开设置
      • Command-R 刷新数据
      • Command-1~5 快速切换工作台模块
      • Shift-Command-L 退出登录
    """
    alert.alertStyle = .informational
    alert.addButton(withTitle: "知道了")
    alert.runModal()
  }

  private func dispatchMenuCommand(_ command: String) {
    (mainFlutterWindow as? MainFlutterWindow)?.dispatchMenuCommand(command)
  }

  private func configureMainMenu() {
    guard let mainMenu = NSApp.mainMenu else {
      return
    }

    if let appMenuItem = mainMenu.item(at: 0) {
      appMenuItem.title = appDisplayName
      configureApplicationMenu(appMenuItem.submenu)
    }

    if let editItem = mainMenu.items.first(where: { $0.title == "Edit" }) {
      editItem.title = "编辑"
    }

    if let viewItem = mainMenu.items.first(where: { $0.title == "View" }) {
      viewItem.title = "显示"
      configureViewMenu(viewItem.submenu)
    }

    if let windowItem = mainMenu.items.first(where: { $0.title == "Window" }) {
      windowItem.title = "窗口"
      configureWindowMenu(windowItem.submenu)
    }

    if let helpItem = mainMenu.items.first(where: { $0.title == "Help" }) {
      helpItem.title = "帮助"
      configureHelpMenu(helpItem.submenu)
    }

    if mainMenu.items.first(where: { $0.title == "文件" }) == nil {
      let fileItem = NSMenuItem(title: "文件", action: nil, keyEquivalent: "")
      let fileMenu = NSMenu(title: "文件")
      fileItem.submenu = fileMenu
      mainMenu.insertItem(fileItem, at: 1)
      configureFileMenu(fileMenu)
    }

    if mainMenu.items.first(where: { $0.title == "账户" }) == nil {
      let accountItem = NSMenuItem(title: "账户", action: nil, keyEquivalent: "")
      let accountMenu = NSMenu(title: "账户")
      accountItem.submenu = accountMenu
      mainMenu.insertItem(accountItem, at: 2)
      configureAccountMenu(accountMenu)
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
      let displayName = menuBarDisplayName.isEmpty ? "已登录" : "已登录：\(menuBarDisplayName)"
      let accountItem = NSMenuItem(title: displayName, action: nil, keyEquivalent: "")
      accountItem.isEnabled = false
      menu.addItem(accountItem)

      let showItem = NSMenuItem(title: "显示主窗口", action: #selector(showMainWindow(_:)), keyEquivalent: "")
      showItem.target = self
      menu.addItem(showItem)
    } else {
      let loginItem = NSMenuItem(title: "点击登录", action: #selector(showMainWindow(_:)), keyEquivalent: "")
      loginItem.target = self
      menu.addItem(loginItem)
    }

    menu.addItem(NSMenuItem.separator())

    let refreshItem = NSMenuItem(title: "刷新数据", action: #selector(refreshFromMenu(_:)), keyEquivalent: "")
    refreshItem.target = self

    let settingsItem = NSMenuItem(title: "设置...", action: #selector(openSettingsFromMenu(_:)), keyEquivalent: "")
    settingsItem.target = self

    let quitItem = NSMenuItem(title: "退出应用", action: #selector(terminateFromStatusItem(_:)), keyEquivalent: "")
    quitItem.target = self

    menu.addItem(refreshItem)
    menu.addItem(settingsItem)
    if menuBarAuthenticated {
      let logoutItem = NSMenuItem(title: "退出登录", action: #selector(logoutFromMenu(_:)), keyEquivalent: "")
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

    if let aboutItem = menu.items.first {
      aboutItem.title = "关于 \(appDisplayName)"
      aboutItem.target = self
      aboutItem.action = #selector(openAboutFromMenu(_:))
    }

    if let preferencesItem = menu.items.first(where: { $0.keyEquivalent == "," }) {
      preferencesItem.title = "设置..."
      preferencesItem.target = self
      preferencesItem.action = #selector(openSettingsFromMenu(_:))
      preferencesItem.keyEquivalent = ","
      preferencesItem.keyEquivalentModifierMask = [.command]
    }

    let refreshItem = NSMenuItem(title: "刷新数据", action: #selector(refreshFromMenu(_:)), keyEquivalent: "r")
    refreshItem.keyEquivalentModifierMask = [.command]
    refreshItem.target = self

    let logoutItem = NSMenuItem(title: "退出登录", action: #selector(logoutFromMenu(_:)), keyEquivalent: "l")
    logoutItem.keyEquivalentModifierMask = [.command, .shift]
    logoutItem.target = self

    menu.insertItem(NSMenuItem.separator(), at: 2)
    menu.insertItem(refreshItem, at: 3)
    menu.insertItem(logoutItem, at: 4)
  }

  private func configureFileMenu(_ menu: NSMenu) {
    menu.removeAllItems()

    let showWindowItem = NSMenuItem(title: "显示主窗口", action: #selector(showMainWindow(_:)), keyEquivalent: "0")
    showWindowItem.keyEquivalentModifierMask = [.command]
    showWindowItem.target = self

    let refreshItem = NSMenuItem(title: "刷新数据", action: #selector(refreshFromMenu(_:)), keyEquivalent: "r")
    refreshItem.keyEquivalentModifierMask = [.command]
    refreshItem.target = self

    let logoutItem = NSMenuItem(title: "退出登录", action: #selector(logoutFromMenu(_:)), keyEquivalent: "l")
    logoutItem.keyEquivalentModifierMask = [.command, .shift]
    logoutItem.target = self

    let menuBarItem = NSMenuItem(title: "收起到菜单栏", action: #selector(moveMainWindowToMenuBarFromMenu(_:)), keyEquivalent: "m")
    menuBarItem.keyEquivalentModifierMask = [.command]
    menuBarItem.target = self

    let closeItem = NSMenuItem(title: "关闭窗口", action: #selector(NSWindow.performClose(_:)), keyEquivalent: "w")

    menu.addItem(showWindowItem)
    menu.addItem(refreshItem)
    menu.addItem(logoutItem)
    menu.addItem(NSMenuItem.separator())
    menu.addItem(menuBarItem)
    menu.addItem(closeItem)
  }

  private func configureAccountMenu(_ menu: NSMenu) {
    menu.removeAllItems()

    let refreshItem = NSMenuItem(title: "刷新数据", action: #selector(refreshFromMenu(_:)), keyEquivalent: "r")
    refreshItem.keyEquivalentModifierMask = [.command]
    refreshItem.target = self

    let settingsItem = NSMenuItem(title: "设置...", action: #selector(openSettingsFromMenu(_:)), keyEquivalent: ",")
    settingsItem.keyEquivalentModifierMask = [.command]
    settingsItem.target = self

    let logoutItem = NSMenuItem(title: "退出登录", action: #selector(logoutFromMenu(_:)), keyEquivalent: "l")
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

    menu.addItem(makeSectionItem(title: "账户概览", action: #selector(showOverview(_:)), key: "1"))
    menu.addItem(makeSectionItem(title: "个人资料", action: #selector(showProfile(_:)), key: "2"))
    menu.addItem(makeSectionItem(title: "安全中心", action: #selector(showSecurity(_:)), key: "3"))
    menu.addItem(makeSectionItem(title: "设备会话", action: #selector(showDevices(_:)), key: "4"))
    menu.addItem(makeSectionItem(title: "敏感日志", action: #selector(showActivity(_:)), key: "5"))
    menu.addItem(NSMenuItem.separator())

    let fullScreenItem = NSMenuItem(title: "进入全屏", action: #selector(NSWindow.toggleFullScreen(_:)), keyEquivalent: "f")
    fullScreenItem.keyEquivalentModifierMask = [.command, .control]
    menu.addItem(fullScreenItem)
  }

  private func configureWindowMenu(_ menu: NSMenu?) {
    guard let menu else {
      return
    }

    if let minimizeItem = menu.items.first(where: { $0.keyEquivalent == "m" }) {
      minimizeItem.title = "最小化"
    }
    if let zoomItem = menu.items.first(where: { $0.title == "Zoom" }) {
      zoomItem.title = "缩放"
    }
    if let frontItem = menu.items.first(where: { $0.title == "Bring All to Front" }) {
      frontItem.title = "全部移到前台"
    }
  }

  private func configureHelpMenu(_ menu: NSMenu?) {
    guard let menu else {
      return
    }

    menu.removeAllItems()

    let aboutItem = NSMenuItem(title: "关于 \(appDisplayName)", action: #selector(openAboutFromMenu(_:)), keyEquivalent: "")
    aboutItem.target = self

    let helpItem = NSMenuItem(title: "使用说明", action: #selector(showUsageHelp(_:)), keyEquivalent: "?")
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
