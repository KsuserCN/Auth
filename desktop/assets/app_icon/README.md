# App Icon Source

请把新的程序图标源文件放到下面这个固定位置：

- `assets/app_icon/source/app_icon.ico`

当前 macOS 实际使用的图标资源目录仍然是：

- `macos/Runner/Assets.xcassets/AppIcon.appiconset`

当你把 `.ico` 放好后，可以执行：

- `./scripts/update_macos_icon.sh`

脚本会把 `.ico` 转成 macOS 所需的多尺寸 PNG 并覆盖 `AppIcon.appiconset`。
