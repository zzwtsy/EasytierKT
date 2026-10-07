# 测试说明

## 测试分层

- `src/test`：连接 ViewModel 的状态与操作委托、profile 输入校验及 EasyTier v2.6.4 TOML 序列化。
- `src/androidTest`：Compose 页面、Navigation 3 跳转、系统返回和 Activity 重建后的返回栈恢复。
- Preview 使用显式 UI 状态，不依赖真实内核、VPN 授权、网络或本地用户数据。
- VPN 授权、系统前台服务、真实 peer 连接及路由转发需要设备或模拟器人工验证；这些场景不在 JVM 测试中模拟。

当前不加入截图测试、Robolectric、MockK 或端到端框架。测试优先覆盖无需设备、可确定复现的配置规则和状态逻辑。

## 本地命令

构建 APK 前先生成 EasyTier v2.6.4 JNI 与 FFI 原生库。需要 Git、Rust stable、Android NDK `27.2.12479018`；脚本会安装固定版本的 `cargo-ndk 3.5.4` 和四个 Android Rust targets。Linux / macOS：

```bash
bash tools/build-easytier-android.sh
```

Windows 可在 WSL2 中进入仓库并运行同一命令。脚本校验 EasyTier commit `8428a89d2dabc94c97d370ec607c6ca142473626`，生成的 `.so` 放在 `app/src/main/jniLibs/`，由 Git 忽略。GitHub Actions 在 Gradle 检查前构建全部四种 ABI。

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
