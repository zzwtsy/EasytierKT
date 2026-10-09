# Android 工程约定

## 项目基线
- 使用 Kotlin、Jetpack Compose、Material 3 和 Navigation 3。
- 依赖与插件版本以 gradle/libs.versions.toml 为准。
- Gradle 版本以仓库中的 Gradle Wrapper 为准。
- 开始修改前阅读 docs/architecture.md、相关代码和测试。
- 参考功能为 app/src/main/java/com/github/zzwtsy/easytierkt/feature/connection/。
- 连接 Repository 暴露只读状态并接收按配置 ID 的连接控制；配置管理经共享 Repository 访问，UI 不直接访问存储、JNI 或 VPN 服务协议。

## 目录与架构
- 按业务 feature 组织页面及其相关代码。
- Route 连接 ViewModel、生命周期状态收集与导航回调。
- Screen 接收状态和回调，负责渲染界面。
- 可复用 UI 组件只接收自身需要的数据、回调和 Modifier。
- ViewModel 组织页面状态，业务数据通过 Repository 访问。
- Repository 管理数据访问、来源协调和数据变更规则。
- 复杂或跨 ViewModel 复用的业务逻辑再提取 UseCase。
- 新增抽象需要有当前复用、测试隔离或复杂度方面的理由。
- 共享代码在出现真实共享需求后再提取。

## 状态与异步
- ViewModel 对外暴露只读 StateFlow。
- Android UI 使用 collectAsStateWithLifecycle 收集页面状态。
- UI 状态放在需要它的最低公共层级。
- 根据状态寿命选择 remember、rememberSaveable、
  SavedStateHandle 或数据层持久化。
- 业务结果优先更新为 UI 状态。
- 副作用有明确触发点、生命周期和取消行为。
- 使用结构化协程，保留 CancellationException 的取消语义。
- 错误、重试、并发操作和重复点击应有明确处理方式。

## Compose
- 使用项目统一主题与已有组件。
- 为页面适用的加载、内容、空数据和错误状态提供实现。
- 为关键页面状态提供可控数据和 Preview。
- 列表使用稳定业务 ID 作为 key。
- 保留合理语义、无障碍描述和大字体适配。
- 性能修改应有具体问题与测量依据。

## 数据与依赖
- 页面和 ViewModel 不直接访问 DAO、DataStore 或业务 HTTP API。
- 数据库结构变化同步更新 schema、migration 和相关测试。
- 新增依赖说明用途，并检查现有能力是否足够。
- 工具链升级与普通功能修改分开。
- 不把真实凭据、签名密钥或用户数据写入源码与测试数据。

## 验证
- 无 flavor 的单 app 模块基础检查：
  ./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
- Kotlin 格式检查：
  ./gradlew :app:ktlintCheck
- 已连接设备或启动模拟器后，验证关键 UI 流程：
  ./gradlew :app:connectedDebugAndroidTest
- 多模块修改需要运行相关模块的测试。
- variant、模块或任务变化时同步更新验证说明。
- 不通过删除测试或关闭检查来掩盖失败。

## 完成条件
- 说明用户可观察到的行为变化。
- 列出实际执行的检查与结果。
- 明确未验证的场景及原因。
- 对重要边界和依赖变化同步更新文档。
