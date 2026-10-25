# Ksuser 安全 · HarmonyOS

使用 ArkTS / ArkUI 实现的鸿蒙原生应用。目前为 **UI 与本地示范数据阶段**，默认进入已登录的演示账号，不连接后端。

## 预览

使用 DevEco Studio 打开本目录，完成工程同步后，在 Previewer、模拟器或真机运行 `entry` 模块。工程要求目标 SDK **26.0.0**，最低兼容 **HarmonyOS 6.1.1 / API 24**。

真机或模拟器安装需要在 DevEco Studio 的 Signing Configs 中配置本机签名。仓库不包含签名凭据；未配置签名时，命令行可完成编译并生成 unsigned HAP。

使用 DevEco Studio 自带工具构建（macOS 默认安装路径）：

```sh
cd mobile/harmonyos
DEVECO_SDK_HOME=/Applications/DevEco-Studio.app/Contents/sdk \
  /Applications/DevEco-Studio.app/Contents/tools/node/bin/node \
  /Applications/DevEco-Studio.app/Contents/tools/hvigor/bin/hvigorw.js \
  --mode module -p product=default -p module=entry@default assembleHap --no-daemon
```

输出：`entry/build/default/outputs/default/entry-default-unsigned.hap`。

## 页面与交互

延续 iOS 的五个主栏目、金色品牌色、分组卡片和内容层级，使用鸿蒙原生 `Navigation`、`NavDestination`、`Tabs`、`SymbolGlyph`、`Toggle`、`Search`、`Select`、`TextInput`、`TextArea`、Sheet 与系统确认弹窗。

| 页面 | 可体验内容 |
| --- | --- |
| 概览 | 欢迎卡、安全状态、服务入口、资料完整度、最近活动 |
| 资料 | 姓名、用户名、地区、简介的弹层编辑与本地保存 |
| 安全 | 双重验证、异地检测、敏感提醒开关；验证方式与账号安全入口 |
| 会话 | 设备详情、移除示范设备、退出其他设备、空状态 |
| 日志 | 关键词搜索、成功/失败筛选、按日分组、活动详情 |
| 授权应用 | 权限详情、取消本地示范授权与空状态 |
| 扫码授权 | 扫码界面示意、模拟扫码、确认/取消登录与完成状态 |
| 通行密钥 / 验证器 | 示范密钥列表、六位验证码输入与格式校验 |
| 邮箱 / 密码 / 登录预览 | 原生表单、输入校验与演示反馈 |
| 关于与设置 | 跟随系统 / 浅色 / 深色外观、隐私说明、登录界面入口 |

所有账号、会话、日志、授权均为硬编码示例。验证码只校验格式；扫码不启用摄像头；通行密钥、登录、邮件、推送与密码修改均不会调用系统能力或接口。输入演示内容即可，无需使用真实凭据。

资料和保护开关通过 `AppStorage` 在页面之间同步，授权列表保留在当前应用进程中，其他页面状态保存在组件内。重新启动应用会恢复示范数据；本版本不持久化数据。

## 源码位置

- `entry/src/main/ets/pages/Index.ets`：主导航、五个 Tab、二级路由。
- `entry/src/main/ets/components/AppComponents.ets`：原生图标、列表行、标题、状态标签。
- `entry/src/main/ets/views/`：各页面和表单演示。
- `entry/src/main/resources/base/element/color.json`：浅色语义色。
- `entry/src/main/resources/dark/element/color.json`：深色语义色。

后续接入时，可替换页面里的示范数据与本地回调，同时保留现有展示组件。

## 当前验证

- `assembleHap` 编译和打包通过；未配置签名时生成 unsigned HAP。
- 使用本机鸿蒙 Previewer 实际渲染五个主页面与深色概览，检查中文、系统图标、卡片与底部导航的布局。
- 已在原生预览中检查 Tab 切换、设置二级导航，以及安全开关返回首页后的状态同步。

本阶段尚未进行真机或后端联调。
