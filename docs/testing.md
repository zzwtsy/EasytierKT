# 测试说明

## 测试分层

- `src/test`：连接 ViewModel 的状态与操作委托、profile 输入校验及 EasyTier v2.6.4 TOML 序列化。
- `src/androidTest`：Compose 页面、Navigation 3 跳转、系统返回和 Activity 重建后的返回栈恢复。
- Preview 使用显式 UI 状态，不依赖真实内核、VPN 授权、网络或本地用户数据。
- VPN 授权、系统前台服务、真实 peer 连接及路由转发需要设备或模拟器人工验证；这些场景不在 JVM 测试中模拟。

当前不加入截图测试、Robolectric、MockK 或端到端框架。测试优先覆盖无需设备、可确定复现的配置规则和状态逻辑。

## 本地命令

原生库构建还需要宿主机上的 Protocol Buffers 编译器 `protoc` 及标准 `.proto` 文件。Ubuntu / Debian / WSL2 Ubuntu 使用 `--no-install-recommends` 时必须显式安装提供标准定义的 `libprotobuf-dev`：

```bash
sudo apt-get update
sudo apt-get install --no-install-recommends --yes protobuf-compiler libprotobuf-dev
protoc --version
protoc --descriptor_set_out=/dev/null google/protobuf/duration.proto google/protobuf/timestamp.proto
```

其他系统需安装对应的宿主机版本及标准定义，并将其加入 `PATH`，或通过 `PROTOC` 环境变量指定可执行文件路径。自定义安装目录可通过 `PROTOC_INCLUDE` 指定包含 `google/protobuf/` 的目录。脚本在下载源码前检查工具版本和标准定义能否编译；GitHub Actions 会在原生库构建前安装这两项依赖。

构建 APK 前先生成 EasyTier v2.6.4 JNI 与 FFI 原生库。需要 Git、Rust stable、Android NDK `30.0.16248370`（与 CI 一致）；脚本会安装固定版本的 `cargo-ndk 4.1.2` 和四个 Android Rust targets。Linux / macOS：

```bash
bash tools/build-easytier-android.sh
```

Windows 可在 WSL2 中进入仓库并运行同一命令。脚本校验 EasyTier commit `8428a89d2dabc94c97d370ec607c6ca142473626`，生成的 `.so` 放在 `app/src/main/jniLibs/`，由 Git 忽略。GitHub Actions 在 Gradle 检查前构建全部四种 ABI。

原生构建的 Android API 固定为 24，与 `app` 的 `minSdk` 一致。NDK 30 要求带 API 版本的 Clang target；脚本同时为 C 编译和 bindgen 指定对应 target，补齐 `cargo-ndk 4.1.2` 默认 bindgen 参数中缺少的 API 版本。

每个 ABI 在同一次 Cargo 构建中生成 JNI 和 FFI，统一依赖特性并共享 EasyTier 核心库的编译结果。

Windows PowerShell：

```powershell
.\gradlew.bat :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

macOS / Linux：

```bash
./gradlew :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

使用已连接设备或运行中的模拟器验证 UI：

```bash
./gradlew :app:connectedDebugAndroidTest
```

连接功能还需要在设备上确认以下系统行为：首次点击连接时的 VPN 授权；VPN 服务、内核和 peer 状态分别更新；已配置与 peer 发布的 IPv4 路由可达；未创建默认路由；点击应用内或通知中的断开操作会关闭隧道；系统回收进程后 sticky service 可以恢复；用户重启设备后不会自动连接。

GitHub Actions 固定构建 EasyTier 原生库，然后执行格式检查、Android Lint、单元测试和 Debug 构建。设备测试在连接设备后本地运行。发布构建也应运行 `:app:assembleRelease`。
