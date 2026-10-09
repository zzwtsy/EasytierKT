# 架构说明

## 模块与包

项目当前使用单个 `:app` 模块。源码包根为 `com.github.zzwtsy.easytierkt`，按业务 feature 放置页面及其状态逻辑：

- `navigation/`：应用级 Navigation 3 路由、返回栈与页面装配。
- `feature/connection/`：连接状态 Route、Screen、UiState 和 ViewModel。
- `feature/profiles/`：配置列表、新增与编辑页面及其 ViewModel。
- `data/connection/`：按配置 ID 的连接控制、状态类型与 VPN/内核生命周期。
- `data/profile/`：多配置集合、类型化选项、配置校验与编码、加密存储、身份派生、应用查询和会话恢复 ID。
- `ui/theme/`：项目主题（颜色、字体、形状、动效与语义扩展色）。
- `ui/icons/`：Material Symbols Rounded 本地图标。

UI 的颜色、字体、形状、动效、组件模式与自适应约定见 `docs/ui-design-guidelines.md`。

当前不拆 Gradle 模块。Domain/UseCase、依赖注入框架和共享模块等抽象，只有出现真实复杂度或复用需求后再引入。

## 页面与状态

- `EasytierApplication` 持有应用级 `AppContainer`，Activity 与 VPN 服务共享配置 Repository 和会话预留。
- `MainActivity` 负责系统窗口设置、主题入口和应用根 Composable。
- Navigation 3 的 `NavKey` 表示页面，应用根持有可保存的返回栈。
- Route 连接 ViewModel、`collectAsStateWithLifecycle` 和导航回调；Screen 仅接收状态、回调与 Modifier。
- ViewModel 对外暴露只读 `StateFlow`。组件内部的临时状态留在 Compose 层。
- 面向用户的标题、标签、说明和错误文案统一放在字符串资源中；动态文案使用资源参数。ViewModel 返回类型化错误，由 UI 映射资源；表单错误定位按 `ProfileSection` 判断，不依赖翻译后的分组标题。

## 连接数据边界

`ConnectionRepository` 暴露只读 `StateFlow<ConnectionStatus>`，并接收 `connect(profileId)`、断开和 VPN 授权被拒绝的操作。`AndroidConnectionRepository` 通过显式 Intent 启动内部 `EasyTierVpnService`；页面和 ViewModel 不直接访问 JNI、VPN Builder 或原生网络接口。

`VpnSessionController` 在应用作用域以单事件队列维护会话状态，独占内核及 TUN 资源。`AndroidConnectionRepository` 只转发控制请求；`EasyTierVpnService` 只处理 Android 生命周期、及时前台通知、Builder 和设备环境校验。控制器读取配置、启动内核、等待虚拟 IPv4、完成 FD 交接并轮询路由；内核、服务分发和 TUN 建立通过可注入端口隔离。进程被系统回收后，sticky service 按原恢复配置 ID 读取最新保存参数并启动；不会回退到当前选中项。没有恢复 ID 时停止。设备重启或单独启动 Activity 不自动连接。

连接状态分别记录 VPN TUN、EasyTier 内核与已连接 peer，附带虚拟 IPv4/IPv6。内核启动成功不等于 peer 已连通。`VpnPlanBuilder` 独立生成双栈地址、必要节点路由、自动/手动业务网段、IPv4 出口与 IPv6 阻断/绕过、DNS、MTU 和应用范围；服务负责执行 Builder 及设备环境校验。仅选中应用与排除应用 API 不混用，EasyTier 本身始终不进入 TUN。详细边界见 `docs/easytier-client.md`。

`ConnectionProfileRepository` 提供只读配置状态和新增、编辑、选择、删除操作。每份 `SavedProfile` 包含稳定 UUID、独立显示名称和现有 `ConnectionProfile` 网络参数；同一网络允许保存多份方案。首份保存自动选中，删除选中项后要求重新选择。应用只运行一份配置；会话预留存在时禁止切换和删除运行配置，仍可编辑保存或删除其他配置。

`ProfileDocument` schema 4 保存配置集合、选中 ID 和恢复 ID。配置修改与会话预留共用 Repository 的 Mutex，持久化成功后才发布新状态；读取失败时禁止变更并允许重试。运行身份独立于持久化恢复记录，因此过期恢复 ID 不会锁住页面。服务读取配置后使用内存参数，编辑不热更新；下一次手动连接或系统恢复使用最新参数。

`EncryptedProfileStore` 使用类型化 DataStore 1.2.1，文件为 `files/datastore/profiles_v4.bin`。Serializer 使用独立 Keystore AES-256/GCM 密钥：版本字节、12 字节随机 IV、密文与认证标签构成二进制信封，固定 AAD 绑定 schema 4 协议，无 Base64。DataStore 负责原子文件替换，`ProfileStore` 提供挂起读写；Repository 的 Mutex 仍维护业务规则，写入和状态发布在小段 NonCancellable 事务内完成。只在文件不存在时返回空文档；密文损坏、认证失败、密钥丢失或 schema 错误均阻止变更。旧 SharedPreferences 与旧文件不读取、不迁移、不删除。备份与设备迁移排除 `datastore/`，继续排除旧 SharedPreferences。

`ConnectionProfile` 持有类型化嵌套配置。网络、路由、安全、DNS、传输、代理和 ACL 校验汇总 `ProfileIssue(ProfileField, ProfileIssueCode)`；动态规则的字段身份包含所属链，中文错误消息由 UI 资源映射。IPAddress 5.6.2 统一数字地址、CIDR、规范化与包含判断，前置语法限制拒绝 DNS、前导零 IPv4、zone ID、映射 IPv6 及通配范围；接口地址保留主机位，网络地址清除主机位，IPv6 使用压缩小写形式。

TOML 由领域模型显式映射到 `@Serializable` 协议 DTO，ACL 协议编号通过穷尽 when 固定映射，语法、转义和表作用域交给 tomlkt 0.6.0。凭据身份省略共享密钥，自动路由省略根 routes，手动空路由保留空数组。版本固定为 0.6.0 以保持现有 Kotlin/Serialization 基线；仅用于编码，嵌套多链语义由真实 EasyTier 解析器验证。JNI JSON 使用 kotlinx.serialization 的固定数值地址 DTO，不接受旧字符串形态或静默默认前缀；区分 Ready、NotReady 与 Error，未知附加字段允许忽略。

`ProfileEditorViewModel` 持有 `ProfileDraft` 中所有 `TextFieldState`，开关、枚举及列表结构与原始文本共同编译成页面模型；没有第二份可独立修改的数字字符串草稿。snapshotFlow 更新派生校验，保存入口同步读取最新文本并重新校验，同一帧输入不会保存旧值。dirty 包含非法文本；禁用选项的非法数值采用加载值或新模型默认值，重新启用仍校验原文本；删除条目移除其输入状态。表单按节点、路由、DNS、安全、传输、代理和 ACL 拆分，可复用输入组件只接收自身状态及错误。密钥使用安全文本框，草稿不进入 Saver 或 SavedStateHandle；仅 Activity 重建保留，进程回收重新读取已保存配置。

`SecureIdentityProvider` 隔离原生身份派生，SAF URI 仅在 Route 回调交给有大小与编码限制的读取器。远端公钥绑定必须启用安全握手，内核在共享密钥验证前检查绑定公钥。

## 会话清理与模式选择

控制器是显式状态机与单消费者事件模型，端口采用 Adapter 与可注入策略，配置变更仍采用 Repository。没有增加通用 UseCase 层、表单 DSL、依赖注入框架或新 Gradle 模块。

- 每次连接持有递增会话令牌；异步任务与显式 Intent 携带令牌，销毁回调绑定服务 host 身份。旧服务和过期任务不能更新新会话；服务清理使用对应 startId，不能停止后续启动请求。
- 主动停止或系统撤销先清除恢复 ID，再取消并等待工作任务、停止内核、关闭 TUN，最后释放配置预留；非主动销毁保留恢复 ID。应用作用域负责清理，不依赖已销毁服务的协程作用域。
- TUN 更新先建立新接口，交接 FD 成功后才关闭旧接口；交接失败关闭新接口并清理会话。内核停止失败保留预留，允许再次断开，防止两个内核并存。
- 启动最多轮询 60 次、间隔 500 ms；已连接后每 2 s 监控。瞬时读取失败继续轮询，内核明确 Error 则终止并清理。CONNECTED 仍只表示内核与 TUN 就绪。

JNI 方法名依赖上游的 `com.easytier.jni.EasyTierJNI` 类名，R8 规则会保留它。原生 JNI 与 FFI 两个共享库按固定的 EasyTier v2.6.4 commit 构建，覆盖 `arm64-v8a`、`armeabi-v7a`、`x86` 和 `x86_64`。生成库位于 `app/src/main/jniLibs/`，不提交到 Git；`tools/build-easytier-android.sh` 与 CI 从固定上游源码加仓库补丁构建，使用 commit/补丁哈希隔离的源码副本和 Cargo 缓存。

## 导航

路由为连接首页、配置列表和 `ProfileEditorKey(profileId)`；ID 为空表示新增。旧 `SettingsKey` 恢复为配置列表。导航 key 使用 Kotlin Serialization 支持返回栈恢复，不携带密钥或完整参数；ViewModel 由 Navigation 3 条目作用域管理。

编辑草稿保存在 ViewModel，Activity 重建保留草稿；进程回收后仅恢复路由 ID，并重新读取已保存内容，新增页恢复为空。未保存修改通过系统返回或工具栏返回均需确认放弃。VPN 授权期间只在 SavedStateHandle 保存待连接 ID，结果返回后使用该 ID。
