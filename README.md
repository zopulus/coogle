# Coogle

让国行版ColorOS更加Google，仅适配ColorOS17。

## 功能

- **Hey Google**：补全国行系统的 Google 语音唤醒支持。
- **Google 智能镜头**：补全 Google 智能镜头功能入口。
- **系统导航方式**：补全设置中的导航选项，支持圈定即搜和左/右下角向内滑动呼出数字助理。
- **Google 搜索框**：启用桌面底部 Google 搜索框。
- **Google 钱包**：支持双击电源键呼出 Google 钱包卡片，需将 Google 钱包设置为默认付款应用。
- **锁屏快捷入口**：补全 Google 数字助理、电子钱包、扫描二维码和家居控制。
- **控制中心**：补全原生钱包、二维码扫描入口。
- **移除 GMS 对国行的使用限制**。
- **设置底部显示 Google 服务**。

## Coogle 附属模块

整合 Google 系统应用固化、Hotword 注册组件、DSP 唤醒支持和圈定即搜实时翻译配置。

开启 Hey Google 时自动检查附属模块。

Google 固化与圈定即搜实时翻译配置默认开启，语音组件由 APK 开关同步控制；关闭 Hey Google 后，重启即停止挂载语音注册/DSP 配置，并跳过模型初始化。首次安装或从旧菜单版升级时，语音支持默认关闭，避免未与 APK 开关同步就执行初始化。

## 致谢

部分代码参考 [ONextBox](https://github.com/MiToverG422/ONextBox) 和 [LuckyTool](https://github.com/luckyzyx/LuckyTool)。

UI 设计参考 [KernelSU](https://github.com/tiann/KernelSU) 和 [LSPosed](https://github.com/LSPosed/LSPosed)，COUI 风格界面使用 [COUI](https://github.com/suqi8/coui) 组件库。

感谢以上项目的开发者与贡献者。

作者：**zopulus**\
开源协议：**GPL-3.0**
