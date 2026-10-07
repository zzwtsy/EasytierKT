# 架构说明

## 模块与包

项目当前使用单个 `:app` 模块。源码包根为 `com.github.zzwtsy.easytierkt`，按业务 feature 放置页面及其状态逻辑：

- `navigation/`：应用级 Navigation 3 路由、返回栈与页面装配。
- `feature/connection/`：连接状态 Route、Screen、UiState 和 ViewModel。
- `feature/settings/`：设置页 UI；业务设置出现后再增加状态与数据访问。
- `data/connection/`：连接 Repository 契约、状态类型及当前不可用实现。
- `ui/theme/`：项目主题。

当前不拆 Gradle 模块。Domain/UseCase、依赖注入框架和共享模块等抽象，只有出现真实复杂度或复用需求后再引入。

## 页面与状态

- `MainActivity` 负责系统窗口设置、主题入口和应用根 Composable。
- Navigation 3 的 `NavKey` 表示页面，应用根持有可保存的返回栈。
- Route 连接 ViewModel、`collectAsStateWithLifecycle` 和导航回调；Screen 仅接收状态、回调与 Modifier。
- ViewModel 对外暴露只读 `StateFlow`。组件内部的临时状态留在 Compose 层。

## 连接数据边界

`ConnectionRepository` 暴露只读 `StateFlow<ConnectionStatus>`，并接收连接、断开和 VPN 授权被拒绝的操作。`AndroidConnectionRepository` 通过显式 Intent 启动内部 `EasyTierVpnService`；页面和 ViewModel 不直接访问 JNI、VPN Builder 或原生网络接口。

`EasyTierVpnService` 是唯一负责 VPN 与内核生命周期的组件：它读取加密配置、启动 EasyTier JNI、等待虚拟 IPv4、建立 TUN、将文件描述符交给内核，并轮询运行信息更新 peer 与代理路由。进程被系统回收后，sticky service 会重新读取配置并启动；设备重启后不自动连接。

连接状态分别记录 VPN TUN、EasyTier 内核与已连接 peer。内核启动成功不等于 peer 已连通。VPN 只添加已配置路由、EasyTier 发布的 `proxy_cidrs` 和 Magic DNS 主机路由；用户配置会拒绝 `0.0.0.0/0`，也不会创建 IPv4 或 IPv6 默认路由。客户端流量通过 `addDisallowedApplication` 排除，避免 EasyTier 自身传输再次进入 TUN。

当前配置是单个 profile，由 `ConnectionProfileRepository` 读写。`EncryptedProfileStore` 使用 Android Keystore 的 AES/GCM 密钥加密整个 profile；Android 云备份与设备迁移规则排除对应 SharedPreferences 文件。配置模型、v2.6.4 TOML 序列化和输入校验位于 `data/profile/`，UI 不直接接触磁盘或 Keystore。

JNI 方法名依赖上游的 `com.easytier.jni.EasyTierJNI` 类名，R8 规则会保留它。原生 JNI 与 FFI 两个共享库按固定的 EasyTier v2.6.4 commit 构建，覆盖 `arm64-v8a`、`armeabi-v7a`、`x86` 和 `x86_64`。生成库位于 `app/src/main/jniLibs/`，不提交到 Git；`tools/build-easytier-android.sh` 与 CI 从固定上游源码构建。

## 导航

当前路由只有连接状态首页和设置页。导航 key 使用 Kotlin Serialization 支持返回栈恢复；ViewModel 由 Navigation 3 条目作用域管理。跨 feature 跳转继续由应用层装配。
