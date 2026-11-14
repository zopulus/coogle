# Google Lens 17.75.23 适配

日期：2026-11-14。ADB 设备：PLK110。Google 实读版本 `17.75.23.ve.arm64 / 301836418`。

旧扫描器在当前 APK 中找到共同判断 `chmv.b(boolean)`，但 Talk 构造器变为四个引用、12 个 boolean、一个末尾引用，共 17 参数，旧规则不接受，因此整个配对返回空。

仅加入已检查的 17 参数结构，保留类型、模式 getter、唯一性和两个入口配对校验。Create 实际为 `cvtu.c()`，Talk 实际为 `dtvy.g()`。缓存版本从 `lens.controllers-v5` 变为 `lens.controllers-v6`，避免同一 APK 继续复用旧未匹配结果。

版本统一为 Coogle `1.3.10 / 140`；附属模块只改 module.prop 版本信息，payloadRevision 仍为 3，controlVersion 仍为 1。

验证：

- 新增 JVM 回归先复现 17 参数不被识别，修复后通过。删除新增结构支持的隔离变异被测试拒绝。
- 26 个合成 DEX 场景通过，覆盖新结构、错误前缀/布尔位/末尾类型、未知结构及歧义控制器。
- 当前 17.75.23 与旧 17.73.15 实际 APK 均找到两个入口及调用者，旧阴性缓存失效、成功缓存复读通过。
- 标准 build.ps1 策略测试、Gradle 生命周期测试、release 构建和 lintVital 通过；附属模块 35 项 Python 测试通过。
- APK 版本与签名已核对，内置附属 ZIP 与独立 ZIP 字节一致。1.3.10 / 140 测试包已安装，Google 已重新加载。
- 用户实机确认：**两个入口均恢复且能打开**。
- 详细诊断与测试日志在忽略目录 `build/lens-diagnostic-2026-11-14/`。

本次仅适配、更新版本和生成测试包；未授权发布新版本。
