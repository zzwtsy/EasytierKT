# 测试说明

## 测试分层

- `src/test`：配置集合增删改、会话预留限制、并发写入、存储错误保护、迁移顺序、原配置最新参数恢复、编辑 ViewModel、授权 ID 保留及 EasyTier v2.6.4 TOML 序列化。
- `src/androidTest`：多配置新增、选择和删除、运行期间操作禁用、错误重试、未保存返回确认及 Activity 重建后的编辑草稿恢复；另用独立 SharedPreferences 与 Keystore key alias 验证真实加密存储与 v1 迁移。
- `ProfileTestRunner` 注入测试 Application，UI 与导航测试只使用内存配置及连接替身，不读写用户配置、不启动真实 VPN。加密测试清理自己的文件和 key alias。
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

连接功能还需要在设备上确认以下系统行为：首次点击连接时的 VPN 授权；VPN 服务、内核和 peer 状态分别更新；已配置与 peer 发布的 IPv4 路由可达；未创建默认路由；点击应用内或通知中的断开操作会关闭隧道；系统回收进程后 sticky service 按原配置 ID 的最新参数恢复；用户重启设备后不会自动连接。

多配置设备验收还包括：连接 A 时禁止切换或删除 A，但允许编辑保存 A；主动断开后可选择并连接 B；删除选中配置后要求重新选择；实际连接、常驻通知和首页显示相同配置名称。真实进程回收恢复、系统 VPN 授权与 A/B 网络可达性不能由内存 UI 测试替代。

编辑草稿只承诺 Activity 重建期间保留；进程回收后重新读取已保存参数，不恢复未保存的密钥或草稿。

GitHub Actions 固定构建 EasyTier 原生库，然后执行格式检查、Android Lint、单元测试和 Debug 构建。设备测试在连接设备后本地运行。发布构建也应运行 `:app:assembleRelease`。

## 多配置实现验证记录（2026-10-09）

使用仓库 Wrapper、缓存的 JDK 25 与只读 API 37 模拟器执行：

```bash
./gradlew :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest --offline
```

全部任务通过：23 项 JVM 单元测试、17 项设备测试。设备测试包含真实 Keystore 加密读写、旧配置迁移，以及使用连接／存储替身的配置管理、Activity 重建和连接分发失败处理。Lint 通过不代表零警告。

未实测真实 A/B 网络可达性、系统回收后的 VPN 恢复、系统授权撤销、设备重启及 TalkBack；本次自动测试不启动真实 VPN。深色、大字体和平板状态提供 Preview，尚未完成这些设置下的人工视觉与键盘检查。
