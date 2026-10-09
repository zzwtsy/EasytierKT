# 架构说明

## 模块与包

项目当前使用单个 `:app` 模块。源码包根为 `com.github.zzwtsy.easytierkt`，按业务 feature 放置页面及其状态逻辑：

- `navigation/`：应用级 Navigation 3 路由、返回栈与页面装配。
- `feature/connection/`：连接状态 Route、Screen、UiState 和 ViewModel。
- `feature/profiles/`：配置列表、新增与编辑页面及其 ViewModel。
- `data/connection/`：按配置 ID 的连接控制、状态类型与 VPN/内核生命周期。
- `data/profile/`：多配置集合、加密存储和会话恢复 ID。
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

## 连接数据边界

`ConnectionRepository` 暴露只读 `StateFlow<ConnectionStatus>`，并接收 `connect(profileId)`、断开和 VPN 授权被拒绝的操作。`AndroidConnectionRepository` 通过显式 Intent 启动内部 `EasyTierVpnService`；页面和 ViewModel 不直接访问 JNI、VPN Builder 或原生网络接口。

`EasyTierVpnService` 是唯一负责 VPN 与内核生命周期的组件：它读取加密配置、启动 EasyTier JNI、等待虚拟 IPv4、建立 TUN、将文件描述符交给内核，并轮询运行信息更新 peer 与代理路由。进程被系统回收后，sticky service 按原恢复配置 ID 读取最新保存参数并启动；不会回退到当前选中项。没有恢复 ID 时停止。设备重启或单独启动 Activity 不自动连接。

连接状态分别记录 VPN TUN、EasyTier 内核与已连接 peer。内核启动成功不等于 peer 已连通。VPN 只添加已配置路由、EasyTier 发布的 `proxy_cidrs` 和 Magic DNS 主机路由；用户配置会拒绝 `0.0.0.0/0`，也不会创建 IPv4 或 IPv6 默认路由。客户端流量通过 `addDisallowedApplication` 排除，避免 EasyTier 自身传输再次进入 TUN。

`ConnectionProfileRepository` 提供只读配置状态和新增、编辑、选择、删除操作。每份 `SavedProfile` 包含稳定 UUID、独立显示名称和现有 `ConnectionProfile` 网络参数；同一网络允许保存多份方案。首份保存自动选中，删除选中项后要求重新选择。应用只运行一份配置；会话预留存在时禁止切换和删除运行配置，仍可编辑保存或删除其他配置。

`ProfileDocument` schema 4 保存配置集合、选中 ID 和恢复 ID。配置修改与会话预留共用 Repository 的 Mutex，持久化成功后才发布新状态；读取失败时禁止变更并允许重试。运行身份独立于持久化恢复记录，因此过期恢复 ID 不会锁住页面。服务读取配置后使用内存参数，编辑不热更新；下一次手动连接或系统恢复使用最新参数。

`EncryptedProfileStore` 使用类型化 DataStore 1.2.1，文件为 `files/datastore/profiles_v4.bin`。Serializer 使用独立 Keystore AES-256/GCM 密钥：版本字节、12 字节随机 IV、密文与认证标签构成二进制信封，固定 AAD 绑定 schema 4 协议，无 Base64。DataStore 负责原子文件替换，`ProfileStore` 提供挂起读写；Repository 的 Mutex 仍维护业务规则，写入和状态发布在小段 NonCancellable 事务内完成。只在文件不存在时返回空文档；密文损坏、认证失败、密钥丢失或 schema 错误均阻止变更。旧 SharedPreferences 与旧文件不读取、不迁移、不删除。备份与设备迁移排除 `datastore/`，继续排除旧 SharedPreferences。

JNI 方法名依赖上游的 `com.easytier.jni.EasyTierJNI` 类名，R8 规则会保留它。原生 JNI 与 FFI 两个共享库按固定的 EasyTier v2.6.4 commit 构建，覆盖 `arm64-v8a`、`armeabi-v7a`、`x86` 和 `x86_64`。生成库位于 `app/src/main/jniLibs/`，不提交到 Git；`tools/build-easytier-android.sh` 与 CI 从固定上游源码构建。

## 导航

路由为连接首页、配置列表和 `ProfileEditorKey(profileId)`；ID 为空表示新增。旧 `SettingsKey` 恢复为配置列表。导航 key 使用 Kotlin Serialization 支持返回栈恢复，不携带密钥或完整参数；ViewModel 由 Navigation 3 条目作用域管理。

编辑草稿保存在 ViewModel，Activity 重建保留草稿；进程回收后仅恢复路由 ID，并重新读取已保存内容，新增页恢复为空。未保存修改通过系统返回或工具栏返回均需确认放弃。VPN 授权期间只在 SavedStateHandle 保存待连接 ID，结果返回后使用该 ID。
