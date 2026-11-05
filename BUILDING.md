# 构建

APK：JDK 17，Android SDK Platform 37.0 / Build Tools 36.0.0。配置 JAVA_HOME 和 ANDROID_HOME（或 local.properties），运行 ./build.ps1。仅构建 arm64-v8a，release 开启 R8 与资源压缩。

附属模块：Python 3，配置上述 JAVA_HOME、ANDROID_HOME 与本机 debug keystore，运行 python companion/build_combined.py；该命令先编译原生资源覆盖，再生成 build/Coogle-Companion-<版本号>.zip。更新后复制到 app/src/main/assets/coogle-companion.zip 再构建 APK。

APK 当前使用本机 Android debug keystore 签名，不同计算机的默认签名不相同。

Lens 原生定位回归测试：先完成普通构建，在已连接的 arm64 Android 设备上运行 `python tests/run_lens_dex_test.py --adb <adb路径> --serial <设备序列号> --verify-regression`。使用 JAVA_HOME、ANDROID_HOME 和 GRADLE_USER_HOME（默认 ~/.gradle）中的已缓存依赖；覆盖旧/新事件编号、锚点歧义、错误控制器结构和缺失目标，并确认旧数字限制会使新版样例失败。只在设备临时目录运行合成 DEX，结束后清理，不安装应用或修改模块。

附属模块回归：生成 ZIP 后运行 `python -m unittest discover -s companion -p 'test_*.py'`。APK 的 `:app:testHookLifecycle` 包含旧 1.3.2 升级、payload 修订号、开关同步和更新选择保留测试；NoMount 模式切换、驱动未加载、标记失败和 Google SYSTEM 状态测试在附属套件中运行，不需要 Root 或修改实机模块。

相机原生资格定位：`python tests/run_camera_dex_test.py --adb <adb路径> --serial <设备序列号> <相机APK路径> [另一版相机APK路径]`。在设备临时目录检查生产 DexKit 定位、反射绑定与缓存，不安装应用或修改系统设置。实际 UI、锁屏与解锁后的恢复需另行安装测试包验证。
