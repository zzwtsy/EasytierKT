# 测试说明

## 测试分层

- `src/test`：配置集合增删改、会话预留限制、并发写入、存储错误保护、schema 4 约束、原配置最新参数恢复、编辑 ViewModel、授权 ID 保留及 EasyTier v2.6.4 TOML 序列化。
- `src/androidTest`：多配置新增、选择和删除、运行期间操作禁用、错误重试、未保存返回确认及 Activity 重建后的编辑草稿恢复；另用独立 DataStore 文件与 Keystore key alias 验证真实加密存储、密文损坏、密钥丢失和原子写入保护。
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

每个 ABI 同次生成 JNI 与 FFI，统一依赖特性并共享核心库构建缓存，再为 JNI 补充 FFI 动态链接。设备 JNI 测试会检查实际 Android 动态加载。

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

连接功能还需要在设备上确认以下系统行为：首次点击连接时的 VPN 授权；VPN 服务、内核和 peer 状态分别更新；已配置与 peer 发布的 IPv4/IPv6 路由可达；普通分流未创建默认路由，IPv4 出口模式默认阻断公网 IPv6；点击应用内或通知中的断开操作会关闭隧道；系统回收进程后 sticky service 按原配置 ID 的最新参数恢复；用户重启设备后不会自动连接。

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


## 完整配置页验证

先构建带仓库补丁的四个 ABI，然后运行原生配置语义和 Android 检查：

```bash
bash tools/build-easytier-android.sh
bash tools/test-easytier-config.sh
./gradlew :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

原生测试覆盖凭据与空共享密钥身份区别、密钥派生/匹配、错误脱敏、显式 /32、IPv6、传输开关、空手动路由、转发和 ACL。JVM 测试覆盖多字段校验、序列化、自动/手动路由、IPv4 出口及 IPv6 策略、DNS 路由、4 KiB/UTF-8 私钥文件边界、数值草稿、身份保存失败重试与应用查询重试。设备测试包含高级数值草稿 Activity 重建、双栈表单保存、schema 4 加密 DataStore 存储和实际 JNI 身份/双栈运行信息；原生测试不建立真实 VPN。回归测试还覆盖 ACL 双栈地址规范化与实际匹配范围、公钥绑定的安全握手约束、同一共享密钥下正确绑定可连接而错误绑定被拒绝，以及三个内存隧道节点使用 `/32` 和 `/128` 时的单播和 IPv4 出口选择、本子网广播与组播。

真实网络验收仍需两台或多台设备：IPv4/IPv6 互通、不同前缀与节点主机路由、自动与手动子网、IPv4 出口的公网地址及 IPv6 阻断/绕过、Magic DNS/自定义 DNS、含无启动器应用的分应用范围、SOCKS5 与 TCP/UDP 转发、凭据授权和错误公钥拒绝、ACL 默认动作/优先级/有状态/限速/组匹配。还需系统 VPN 授权、撤销与进程回收恢复，以及大字体/横屏/平板、键盘和 TalkBack 人工检查。

本轮新存储不读取或迁移旧配置，升级后从空配置集合开始，旧文件保留；验证新功能时重新添加配置。


## 完整配置页实现验证记录（2026-10-09）

本地使用已缓存的 JDK 25、Wrapper、API 37 模拟器与四 ABI 原生库验证：

- `bash tools/build-easytier-android.sh`：四个 ABI 构建通过；JNI 均含 FFI 动态依赖，上游检出保持干净。
- `bash tools/test-easytier-config.sh --offline`：4 项原生配置契约测试通过。
- `:app:ktlintCheck :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest --offline`：全部通过，44 项 JVM 测试与 24 项设备测试，零失败；Lint 存在既有警告。
- `bash -n` 与 `git diff --check`：通过。

设备测试实际加载新 JNI/FFI，并确认身份生成/派生、凭据配置解析、错误脱敏、显式 /32 与静态 IPv6 运行信息。高级表单覆盖 ACL 编辑、非法优先级、负数限速、数值草稿重建和双栈配置保存。schema 3 存储验证包括旧版本省略 schema 字段时仍拒绝读取。

本地没有配置跨设备对端、授权凭据服务端或出口服务器，因此尚未验证真实双栈互通、公网出口、ACL 流量过滤/限速/组匹配和转发效果。系统 VPN 撤销、进程回收恢复、TalkBack、真实设备的大字体/平板/键盘场景仍需人工验收。CI 工作流已接入补丁缓存与原生契约测试，尚未在远端运行。

## 配置审查修复验证记录（2026-10-10）

本次修复 ACL 双栈地址条件规范化、公钥绑定必须启用安全握手、共享密钥不能绕过错误绑定公钥，以及 `/32`、`/128` 和异网段地址的广播误判。

- `bash tools/test-easytier-config.sh --offline`：8 项原生测试通过，包含实际 ACL 匹配、真实安全握手及三个内存隧道节点的单播/出口选择。
- 在旧补丁的独立构建副本中运行新增回归用例，成功复现 `/32` 单播群发及共享密钥绕过错误公钥绑定；完整修复补丁中同一用例通过。
- 四种 ABI 的 JNI/FFI Release 构建通过。两种 ARM 使用仓库脚本完成；x86 与 x86_64 使用脚本相同的 NDK、bindgen、Cargo 包和 JNI 动态链接参数，在独立 Cargo 目录并行完成。上游检出保持干净。
- 四 ABI 的产物与 `jniLibs` 文件一致，JNI 均声明 FFI 动态依赖；Debug APK 中的 8 个库文件与构建产物逐一一致。
- `:app:ktlintCheck :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest --offline`：全部通过，48 项 JVM 测试、26 项设备测试，零失败。设备测试加载更新后的原生库；Lint 和原生编译仍有上游或既有警告。
- `bash -n`、`git diff --check` 与 `git diff --cached --check`：通过。

实际跨设备 VPN、公网出口及真实业务流量的 ACL 过滤仍未验证：本地未配置跨设备对端或出口服务器。原生回归覆盖的是内核实际匹配、内存隧道握手和路由选择，不能替代真实网络验收。本次不提交 Git。


## 编码器与上游解析器契约

JVM `ProfileTomlContractTest` 使用实际 Android 编码器生成三个只含合成数据的 fixture，覆盖三个 ACL 链、各链重复规则字段、转义文本、双栈、多个 peer/转发、手动空路由、自动省略路由和凭据省略共享密钥。上游 Rust 测试读取这些文件后核对完整语义，不依赖 tomlkt 自己的解码器。

```bash
./gradlew :app:testDebugUnitTest
bash tools/test-easytier-config.sh --android-fixtures --offline
```

fixture 位于 `app/build/generated-contracts/`，不会提交。未提供 `--android-fixtures` 时保留已有原生测试流程，新增桥接测试标记为 ignored；使用该选项会明确要求 fixture 存在并执行桥接测试。重新修改编码器后应先运行 JVM 测试再执行桥接检查。

## 实现优化验证记录（2026-10-10）

在已有四 ABI 原生库、仓库 Wrapper、缓存 JDK 25 和 API 37 x86_64 模拟器上执行：

```bash
./gradlew :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest --offline
bash tools/test-easytier-config.sh --android-fixtures --offline
```

- 全部 Gradle 任务通过：69 项 JVM 测试、27 项设备测试，零失败与跳过。Lint 无错误，有 19 项警告。
- 上游原生测试 9 项全部通过，无跳过；其中新增桥接测试确认真实 Android 编码器产生的三个 ACL 链、嵌套规则、转义文本和多条转发未丢失，自动/手动路由与凭据身份语义保持正确。
- JVM 回归覆盖固定 JNI 数值 DTO、数字地址限制、同一帧编辑/切换/保存、动态字段订阅及删除、禁用数值的加载值回退、会话清理顺序、过期回调、明确失败与瞬时读取错误、TUN 交接、DNS 等待和停止失败保留预留。
- 设备测试实际加载现有 JNI/FFI，验证双栈及 `/32`、凭据解析、加密 DataStore 重建、密文篡改、密钥丢失（含已缓存文档）、旧文件保留，以及 Compose 编辑和 Activity 重建。
- Debug APK 从 108,695,958 bytes 增至 109,876,209 bytes，增加 1,180,251 bytes（约 1.13 MiB，1.09%）。比较包含本次依赖、代码和许可证变化，未做 Release/R8 体积测量。
- `bash -n tools/test-easytier-config.sh`、资源 XML 解析及 `git diff --check` 通过。`git diff --cached --check` 仍报告原已暂存 `tools/native-patches/android-profile.patch` 的 10 行上下文空白；该补丁本轮没有修改，未改写用户暂存内容。

本轮沿用已有原生库，没有重新编译四 ABI，也没有升级工具链、提交或推送。当前 CI 保留原生测试的既有执行顺序；新增编码器桥接测试须按上述顺序生成 fixture 后显式执行 `--android-fixtures`。

仍未验证真实跨设备 VPN、公网出口、真实业务流量 ACL/转发、系统 VPN 撤销和进程回收后的真实服务恢复，也未进行 API 24/ARM 真机、Release/R8、TalkBack、大字体、平板和键盘的人工验收。当前环境只有单机 API 37 模拟器，没有对端或出口服务器；控制器替身测试不替代这些系统及网络场景。
