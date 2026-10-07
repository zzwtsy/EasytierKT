# EasytierKT

EasytierKT 是基于 Kotlin、Jetpack Compose、Material 3 和 Navigation 3 的 Android 客户端。

## 开发环境

- JDK 25（Gradle Daemon 工具链由 `gradle/gradle-daemon-jvm.properties` 固定）
- Android SDK Platform 37
- Rust stable、Android NDK `30.0.16248370`（与 CI 一致，用于构建 EasyTier JNI/FFI 原生库）
- Protocol Buffers 编译器 `protoc` 及标准 `.proto` 文件（在宿主机上生成原生库所需的 Rust protobuf 代码，安装方式见 [测试说明](docs/testing.md#本地命令)）
- 使用仓库中的 Gradle Wrapper，不单独安装或切换 Gradle 版本

## 构建 EasyTier 原生库

原生实现固定在 EasyTier v2.6.4 commit `8428a89d2dabc94c97d370ec607c6ca142473626`。Linux / macOS 或 WSL2 中运行：

```bash
bash tools/build-easytier-android.sh
```

脚本会构建 `libeasytier_android_jni.so` 与 `libeasytier_ffi.so`，支持 `arm64-v8a`、`armeabi-v7a`、`x86` 和 `x86_64`。构建后的 `.so` 位于 `app/src/main/jniLibs/`，不会提交到 Git；GitHub Actions 会在 Gradle 构建前生成它们。没有对应 ABI 原生库的本地 APK 会显示缺少原生库错误，无法建立 EasyTier 连接。

## 构建与检查

Windows PowerShell：

```powershell
.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:ktlintCheck
```

macOS / Linux：

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:ktlintCheck
```

连接设备或启动模拟器后，可运行 Compose 导航测试：

```bash
./gradlew :app:connectedDebugAndroidTest
```

工程结构、状态边界和测试约定见 [架构说明](docs/architecture.md) 与 [测试说明](docs/testing.md)。
EasyTier VPN 生命周期、配置加密、路由策略与原生构建过程见 [EasyTier 客户端实现说明](docs/easytier-client.md)。第三方许可证副本位于 `app/src/main/assets/licenses/`。
