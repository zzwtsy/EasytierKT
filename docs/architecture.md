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

`ConnectionRepository` 当前只暴露 `Flow<ConnectionStatus>`。唯一状态 `Unavailable` 表示 EasyTier 内核尚未接入；`UnavailableConnectionRepository` 作为应用启动时的实现。

连接状态页通过 ViewModel 读取状态，不直接访问 Android Service、JNI、网络或平台 API。接入真实内核时，再依据其实际生命周期、错误模型和配置接口扩展 Repository；在此之前不定义连接参数、权限、后台服务或连接/断开操作。

## 导航

当前路由只有连接状态首页和设置页。导航 key 使用 Kotlin Serialization 支持返回栈恢复；ViewModel 由 Navigation 3 条目作用域管理。跨 feature 跳转继续由应用层装配。
