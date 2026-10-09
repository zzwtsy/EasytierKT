# UI 设计规范

本规范是 EasytierKT 当前 UI 的权威约定，基于 **Material Design 3 + Material 3 Expressive**，
实现于 Jetpack Compose `material3 1.5.0-beta01`（显式固定，覆盖 Compose BOM 的 1.4.0；
1.5 stable 发布后移除显式版本回归 BOM 管理）。新页面与组件必须遵循本规范。

- 设计语言调研与重构方案：`docs/ui-ux-redesign.md`
- 工程与架构约定：`AGENTS.md`、`docs/architecture.md`

## 1. 设计基线

| 原则 | 约定 |
|------|------|
| 状态信号 | 关键状态必须"颜色 + 图标 + 文字 + 操作标签"四重信号，不得只靠颜色 |
| 页面主角 | 每页最多一个主操作（filled button / Hero 开关），其余操作降权（tonal/outlined/text） |
| 瞬时反馈 | 一次性确认（如"已保存"）用 Snackbar；持续状态（如连接错误）内联展示并配图标 |
| 字段校验 | 错误落在对应字段（`isError` + `supportingText`），不用页面级全局错误 |
| 触控目标 | 可交互元素 ≥ 48dp；可点行整行响应（`toggleable` + `Role`） |
| 主题 | 动态取色优先（Android 12+），品牌色为回退；深色跟随系统 |

## 2. 颜色

定义：`ui/theme/Color.kt`、`ui/theme/Theme.kt`。

- **品牌色**：冷色科技蓝调色板（Blue / BlueGrey / Cyan 三角色），light/dark 两套 `ColorScheme`。
- **语义扩展色**：M3 色板之外的 `success` 系列（Green），经 `ExtendedColors` + `LocalExtendedColors`
  提供，用 `extendedColors.success` 访问；**仅用于"已连接"状态**，不得借用 primary 表达成功。
- 动态取色开启时（默认）以上颜色仅作低版本回退。

| 语义 | 取值 |
|------|------|
| 连接中/过渡 | `primary` / `primaryContainer` |
| 已连接 | `extendedColors.success` |
| 失败/错误 | `error` / `errorContainer` |
| 未连接/次要 | `onSurfaceVariant` / `surfaceVariant` |

## 3. 字体

定义：`ui/theme/Type.kt`（默认字阶 + material3 Emphasized 变体）。

| 用途 | 样式 |
|------|------|
| 页面核心状态主标题（如连接状态） | `headlineLargeEmphasized` |
| 表单分组标题 | `titleMediumEmphasized` + `primary` 色 |
| 卡片内标题 | `titleLarge` / `titleMedium` |
| 状态行的值 | `titleSmall` |
| 正文 | `bodyLarge` / `bodyMedium` |
| 辅助说明（生效说明、许可证） | `bodySmall` + `onSurfaceVariant` |

Emphasized 字阶只用于需要视觉焦点的位置，不用于长正文。

## 4. 形状

定义：`Theme.kt` 的 `AppShapes`。

| Token | 圆角 | 主要消费者 |
|-------|------|-----------|
| extraSmall | 12dp | OutlinedTextField |
| small | 16dp | 小组件 |
| medium | 20dp | Card |
| large | 28dp | — |
| extraLarge | 36dp | — |

Hero 连接开关单独约定：未连接为圆形（72dp 圆角），已连接 morph 为圆角方形（32dp 圆角）。

## 5. 动效

- 主题启用 `MotionScheme.expressive()`（`MaterialTheme(motionScheme = …)`）。
- 形状/尺寸/位移变化用 `motionScheme.defaultSpatialSpec()`；颜色/透明度用 `defaultEffectsSpec()`。
- 状态主标题切换用 `AnimatedContent` 交叉淡入淡出（effects spec）。
- 页面转场：`NavDisplay` 水平滑动 1/4 屏 + 淡入淡出（前进/后退镜像），见 `navigation/EasytierApp.kt`。
- 主操作按下配一次轻触感（`HapticFeedbackType.LongPress`）。
- 加载指示属于状态反馈而非装饰动画，不做"减少动态"降级。

## 6. 图标

- 统一使用 **Material Symbols Rounded** 的本地矢量定义：`ui/icons/AppIcons.kt`
  （`androidx.compose.material.icons` 官方已停更，项目不依赖任何图标库）。
- 现有图标：`PowerIcon`、`SettingsIcon`、`ArrowBackIcon`、`VisibilityIcon`、`VisibilityOffIcon`、
  `ErrorIcon`、`InfoIcon`、`VpnKeyIcon`、`MemoryIcon`、`LanIcon`、`RouterIcon`。
- 装饰性图标 `contentDescription = null`；承载操作的图标按钮必须有 `contentDescription`。
- **新增图标**：`python3 tools/convert-material-symbols.py <icon_name>`（从 fonts.gstatic.com 下载
  Rounded 24px SVG 并完成视口平移），把输出的 `val` 粘贴进 `AppIcons.kt` 并补全 KDoc 用途。

## 7. 布局与自适应

| 约定 | 值 |
|------|---|
| 页面水平边距 | 24dp |
| 表单/详情最大宽度 | 600dp（配置编辑）、480dp（连接详情卡片），居中 |
| 双栏阈值 | 屏幕宽度 ≥ 840dp：连接页左 Hero、右详情 |
| Edge-to-edge | `enableEdgeToEdge()` + Scaffold `contentPadding`，关键控件避开系统栏 |
| 键盘 | manifest `adjustResize`；底部操作栏加 `Modifier.imePadding()` |
| 深色启动 | `values-night/themes.xml` 使用暗色父主题，避免启动闪白 |

## 8. 组件模式

### 8.1 页面骨架
- 顶层页：small `TopAppBar`（标题 + 1 个 action 图标按钮，如设置）。
- 表单/设置类子页：`MediumFlexibleTopAppBar` + `exitUntilCollapsedScrollBehavior`（大标题滚动收缩）
  + `navigationIcon` 返回箭头（`ArrowBackIcon`，contentDescription `navigate_back`）。
- 返回一律用 navigationIcon，禁止正文内"返回首页"类按钮。

### 8.2 Hero 连接开关（连接页独有）
- 环形区 192dp / 按钮 144dp / 图标 56dp；`STARTING` 时外圈 `CircularWavyProgressIndicator` 环绕。
- 四态容器/内容色：未连接 `surfaceVariant`，过渡 `primaryContainer`，已连接 `success`，失败 `errorContainer`。
- 连接中保持可点（=取消）；`STOPPING` 禁用。
- 是连接页唯一主操作；页面内不放"打开设置"类按钮。

### 8.3 卡片
- **状态明细卡**：ListItem 式行（leading 图标 24dp `onSurfaceVariant` + `bodyLarge` 标签 + `titleSmall` 值），
  行间 `HorizontalDivider`（左右 20dp 内缩），卡片内边距 0。
- **空态引导卡**：`InfoIcon`（primary）+ `titleMediumEmphasized` 标题 + 说明 + `FilledTonalButton` 出口。
- **错误卡**：`errorContainer`/`onErrorContainer`，`ErrorIcon` + 原因 + 恢复动作（Button 重试 / TextButton 检查设置）。

### 8.4 表单
- 分组：`titleMediumEmphasized` 组标题 + 20dp 圆角 Card（组内边距 20dp、字段间距 16dp）。
- 字段：`OutlinedTextField`，`singleLine` 或 `minLines/maxLines`；校验错误字段级展示。
- 密钥/密码字段：trailing 可见性切换（`VisibilityIcon`/`VisibilityOffIcon` + contentDescription）。
- 条件字段：`AnimatedVisibility` 展开收起。
- Switch 设置项：整行 `toggleable(role = Role.Switch)`，内部 `Switch(onCheckedChange = null)` 避免 TalkBack 重复播报。
- 主操作固定底部栏：`Scaffold.bottomBar` + `Surface(tonalElevation = 3.dp)` + 内边距 24/16dp + `imePadding()`。

### 8.5 按钮与加载
- 权重：`Button`（主）> `FilledTonalButton`（次，如空态出口）> `OutlinedButton` > `TextButton`。
- 进行中操作在按钮内嵌小号进度（`CircularProgressIndicator` 18dp / strokeWidth 2dp / `LocalContentColor`）。
- 页面级加载用 Expressive `LoadingIndicator()` 居中。

### 8.6 反馈
- 一次性确认：Snackbar（`SnackbarHostState` + `LaunchedEffect(uiState.xxx)`，展示后回调 ViewModel 复位标志，
  见配置列表的 `showSaved` 消费回调）。
- 持续错误：内联图标 + 错误色文字/卡片。

## 9. 连接状态设计

`ConnectionPhase` 五态在主页的表达（参考 `feature/connection/ConnectionScreen.kt`）：

| 阶段 | Hero 容器 | 主标题色 | 副文案 | 详情区 |
|------|-----------|----------|--------|--------|
| DISCONNECTED（有配置） | surfaceVariant | onSurfaceVariant | 就绪提示 | 无 |
| DISCONNECTED（无配置） | surfaceVariant | onSurfaceVariant | 引导语 | 空态引导卡 |
| STARTING | primaryContainer + 波浪环 | primary | "点按可取消" | 状态明细卡 |
| STOPPING | primaryContainer（禁用） | primary | "正在断开连接" | 状态明细卡 |
| CONNECTED | success + 形状 morph | success | 连接时长（30s 刷新） | 状态明细卡 |
| ERROR | errorContainer | error | 失败提示 | 错误卡（重试/检查设置） |

连接时长来自 `ConnectionStatus.connectedAtEpochMs`（服务侧保留首次进入 CONNECTED 的时刻）。

## 10. 无障碍

- 图标按钮、Hero 开关必须有随状态变化的 `contentDescription`。
- 可点行整行响应且 ≥ 48dp；Hero 开关 144dp。
- 大字体（1.3x）：标题允许换行居中，状态行的值允许换行。
- 状态不以颜色为唯一信号（见 §1）。

## 11. Preview 与验证

- 关键页面提供 Phone / Phone Dark / Tablet 三形态 Preview，另加关键业务状态
  （连接页：无配置、已连接、错误；配置列表：空列表、读取错误、运行中；编辑页：校验错误）。
- 修改后执行：
  `./gradlew :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest :app:assembleDebug`
- 有设备时执行 `./gradlew :app:connectedDebugAndroidTest` 验证关键 UI 流程。
- 手动核对：深色模式、大字体、横屏/平板宽度、TalkBack 播报。
