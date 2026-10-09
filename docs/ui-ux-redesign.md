# UI/UX 重构设计（MD3 + MD3 Expressive）

> 本文档是重构的调研与方案记录（已完成实施）。当前生效的设计约定以 `docs/ui-design-guidelines.md` 为准。

> 本文档是完全重构的设计方案，不延续现有页面结构。仅设计，不含实施代码。
> 调研来源：developer.android.com（compose-material3 1.4.0/1.5 发布说明与 API 参考）、
> m3.material.io 指南、screensdesign.com 对 ProtonVPN/Surfshark/NordVPN/PIA 等 10 款 VPN 应用的 UX 拆解。

## 一、调研结论

### 1.1 MD3 Expressive 设计语言要点

| 维度 | 要点 | Compose 对应能力（material3 1.4.0 已可用） |
|------|------|--------------------------------------------|
| 动效 | 弹簧物理动效是 Expressive 的核心；hero 交互用 expressive，工具性交互用 standard | `MaterialTheme(motionScheme = MotionScheme.expressive())`，空间类（形状/尺寸）与效果类（颜色/透明度）动画分离 |
| 字体 | 关键信息用 Emphasized 字阶（更粗、更紧字距），制造视觉焦点 | `Typography.displaySmallEmphasized` / `headlineLargeEmphasized` / `titleMediumEmphasized` 等全套 |
| 形状 | 35 种新形状；形状变形（morph）传达状态变化；整体更大圆角 | `Shapes` 圆角基线调大；morph 需 `androidx.graphics:graphics-shapes`（可选增强） |
| 颜色 | 状态必须有语义色；强调层次靠角色分工而非阴影 | `ColorScheme` + 自定义 success 扩展色 |
| 组件 | ButtonGroup、SplitButton、波浪 LoadingIndicator、Flexible App Bar（滚动时标题缩放平移）、docked toolbar | `ButtonGroup`、`LoadingIndicator`、`ContainedLoadingIndicator`（`ExperimentalMaterial3ExpressiveApi`）；`MediumFlexibleTopAppBar` / `LargeFlexibleTopAppBar`（1.4 可用，1.5 转正） |
| 图标 | `androidx.compose.material.icons` 已停止更新，官方推荐 Material Symbols | 从 fonts.google.com/icons 导出 Material Symbols Rounded 为本地 vector drawable 使用 |

### 1.2 VPN 应用 UX 模式（对 10 款主流 VPN 的拆解）

1. **一个绝对醒目的连接开关**：主页主角只有一个 giant toggle（电源形或大圆形按钮），其他一切从属。
2. **状态必须" unmistakable "**：未连接/连接中/已连接/失败，靠文字 + 颜色 + 图标 + 按钮标签**四重信号**同时区分；只靠颜色不合格（无障碍）。
3. **连接中 = 进度 + 可取消**：无限进度指示与"取消"并存。
4. **已连接视图分层**：最上层是主状态与操作，其下是虚拟 IP、节点数等次要验证信息，技术细节最不显眼。
5. **失败状态给原因 + 恢复动作**：错误说明可能原因，提供"重试"或"去检查设置"的直接出口。
6. **系统 VPN 授权弹窗前做预告**：用与系统一致的语言解释"系统接下来会询问 VPN 配置权限"；被拒绝后回到稳定的未连接态并给出"重试"。
7. **状态以系统隧道为准**：回到前台时对账真实状态（本项目 `ConnectionRepository.status` 已是唯一事实源，天然符合）。

### 1.3 导航与结构
- 两个顶层目的地，不需要底部导航（符合 M3）；页面间转场应用 M3 转场模式（shared axis / fade through），Navigation 3 的 `NavDisplay` 支持自定义 `transitionSpec`，且系统预测性返回默认可用。

## 二、重构设计

### 2.1 主题基线（ui/theme）

1. **颜色**：选定品牌种子色（建议冷色科技蓝/青，与用户确认后）用 Material Theme Builder 生成完整 light/dark `ColorScheme` 替换模板紫；动态取色保持优先。新增 `success` 语义扩展色（`CompositionLocal` 扩展，如 `LocalExtendedColors`），用于"已连接"（绿系），避免借用 primary 造成语义混乱。
2. **字体**：状态主标题用 `headlineLargeEmphasized`、页分区标题用 `titleMediumEmphasized`，其余沿用默认字阶。
3. **形状**：圆角基线调大——卡片 24dp、输入框 16dp（OutlinedTextField 默认形状覆盖）、主按钮全圆角。
4. **动效**：`MaterialTheme(motionScheme = MotionScheme.expressive())`；状态切换时形状/尺寸变化用 spatial spec，颜色/透明度用 effects spec。
5. **图标**：迁移到 Material Symbols Rounded 本地 vector drawable，移除 `material-icons-core` 依赖。需要的图标：`power_settings_new`、`settings`、`arrow_back`、`visibility` / `visibility_off`、`check_circle`、`error`、`info`、`vpn_key`、`lan`/`public`、`route`、`dns`。

### 2.2 连接主页（全部重排）

```
┌──────────────────────────────────┐
│ EasyTier                    ⚙    │  ← SmallTopAppBar（标题 + 设置 action）
│                                  │
│            ◯◯◯◯◯◯◯◯             │
│          ◯  ┌──────┐  ◯          │  ← Hero：~144dp 圆形连接开关
│         ◯   │  ⏻   │   ◯         │     connecting 时外圈波浪进度环绕
│          ◯  └──────┘  ◯          │
│            ◯◯◯◯◯◯◯◯             │
│                                  │
│          已 连 接                │  ← headlineLargeEmphasized + 语义色
│      demo-network · 3 个节点     │  ← bodyLarge / onSurfaceVariant 副文案
│                                  │
│  ┌────────────────────────────┐  │
│  │ 🛡 VPN 隧道          运行中 │  │  ← 详情卡片（ListItem 行 + 分隔线）
│  │ ⚙ EasyTier 内核      运行中 │  │     仅连接中/已连接/失败时显示
│  │ ⬡ 已连接节点        3 个节点│  │
│  │ ⌁ 虚拟 IPv4   10.144.144.1 │  │
│  └────────────────────────────┘  │
└──────────────────────────────────┘
```

**Hero 连接开关**（页面唯一主操作）：
- ~144dp 圆形按钮，中央 Material Symbols `power_settings_new` 图标（~56dp）。
- 视觉随四态变化（四重信号）：
  - 未连接：`surfaceVariant` 底 + `onSurfaceVariant` 图标；按下连接。
  - 连接中：按钮本身保持可点（=取消），外围套波浪形无限 `LoadingIndicator` 环绕动画；下方按钮标签语义并入状态文案"正在连接…点按取消"。
  - 已连接：`success` 扩展色填充 + 白色图标；按下断开。
  - 失败：`errorContainer` + `onErrorContainer` 图标；按下重试。
- 按下/状态切换时做形状 morph（圆 ↔ 大圆角方）与颜色 effects 过渡（expressive spatial/effects spec），并配一次轻触感反馈。
- 无障碍：`contentDescription` 随状态变化（"连接 VPN"/"断开连接，当前已连接"），`Role.Button` + `stateDescription` 播报状态。

**状态文案**：主标题 `headlineLargeEmphasized` + 语义色；副文案一行：已连接显示"网络名 · N 个节点"，未连接显示引导语。

**详情卡片**：仅连接中/已连接/失败显示；`ListItem` 风格行（leading 图标 + 标签 + 尾部值），行间 `HorizontalDivider`，值用 `titleSmall`。

**两个特殊状态**：
- **未配置空态**（未连接且无有效 profile）：详情卡片位置替换为引导卡片——`info` 图标 + "先在设置中完成网络配置" + FilledTonalButton"前往配置"。需要 UiState 新增 `hasProfile`（由 `ConnectionProfileRepository` 暴露只读标志，ViewModel 合并进 `ConnectionUiState`）。
- **失败态**：`errorContainer` 卡片 = `error` 图标 + 原因文案 + 两个操作（Button"重试" / TextButton"检查设置"）。VPN 权限被拒时文案即预告恢复路径。
- **授权预告**：首次点连接且系统弹窗前，可用 Snackbar/对话式说明预告系统即将请求 VPN 权限（精简实现：沿用现有拒绝后错误文案；完整实现：权限请求前底部说明条）。

**明确移除**：页面内不再出现"打开设置"按钮与常驻 setup hint 文字（设置只从 app bar 进入；引导职责移交空态卡片）。

### 2.3 设置页（全部重排）

```
┌──────────────────────────────────┐
│ ←  设置                          │  ← MediumFlexibleTopAppBar
│    （大标题，滚动时收缩）         │     （exitUntilCollapsed）
│ 配置一个 EasyTier 网络…          │
│                                  │
│ 网络凭据                         │  ← titleMediumEmphasized 组标题
│ ╭──────────────────────────────╮ │
│ │ 网络名        [____________] │ │  ← 大圆角(24dp)分组卡片
│ │ 网络密钥      [________] 👁  │ │
│ ╰──────────────────────────────╯ │
│ 节点与路由                       │
│ ╭──────────────────────────────╮ │
│ │ 对等节点地址  [____________] │ │
│ │ 使用 DHCP 获取虚拟 IPv4   ◯─ │ │  ← 整行可点 Switch 行
│ │ IPv4 路由     [____________] │ │
│ ╰──────────────────────────────╯ │
│ 其他                             │
│ ╭──────────────────────────────╮ │
│ │ 启用 Magic DNS            ─◯ │ │
│ ╰──────────────────────────────╯ │
│ 保存的配置会在下次连接时生效。    │  ← bodySmall 辅助说明
│ EasyTier v2.6.4 使用 LGPL-3.0…  │
│ ┌──────────────────────────────┐ │
│ │            保存配置           │ │  ← Scaffold bottomBar 固定主按钮
│ └──────────────────────────────┘ │     (不随内容滚动, imePadding)
└──────────────────────────────────┘
```

- **分组卡片**：每组一张 24dp 圆角 `Card`（组间距 24dp），组标题用 `titleMediumEmphasized` + primary 色；组内字段 16dp 间距。`OutlinedTextField` 形状覆盖为 16dp 圆角。
- **字段级校验**：沿用（错误落在字段 `isError` + `supportingText`）。
- **密钥字段**：visibility 切换 trailing 图标（Material Symbols）。
- **DHCP 关闭时静态 IPv4 字段**：`AnimatedVisibility` 展开/收起（expressive spatial spec）。
- **保存机制**：固定底部主按钮（`Scaffold.bottomBar`），保存中内嵌进度；成功 Snackbar（沿用）。
  备选：自动保存 + Snackbar 确认（M3 系统设置主流），但需 ViewModel 防抖与冲突处理，列为后续增强。
- **加载态**：整页 `ContainedLoadingIndicator` / 居中 `LoadingIndicator` 替代文字。

### 2.4 导航转场
- `NavDisplay` 增加 M3 转场：前进/后退用 shared axis X 方向（slide + fade 组合），时长/插值器对齐 `MotionScheme.standard()` 的空间 spec。
- 系统预测性返回无需额外配置（Navigation 3 支持）。

### 2.5 自适应与无障碍
- 手机竖屏如上；**大屏/横屏**：主页改双栏（左 Hero 开关与状态，右详情卡片），设置页表单限宽 600dp 居中。
- 大字体（1.3x+）：Hero 区允许标题换行、开关尺寸固定不裁切；列表行值允许换行。
- 颜色对比：语义色 + 图标 + 文字四重信号已覆盖色盲；减少动态（系统 animator duration scale=0 时）波浪动画降级为静态环。
- 触控目标：开关 ≥ 144dp，Switch 行 ≥ 48dp，图标按钮 ≥ 48dp。

### 2.6 数据层影响（仅新增只读状态）
1. `ConnectionUiState` 增加 `hasProfile: Boolean`（来源：`ConnectionProfileRepository` 暴露只读 `StateFlow<Boolean>` 或一次性查询合并）。
2. （可选增强）`ConnectionStatus` 增加 `connectedAt` 以显示连接时长——本期不做，列入待办。

### 2.7 设计元素 → Compose API 映射

| 设计元素 | API | 备注 |
|----------|-----|------|
| Expressive 动效基线 | `MaterialTheme(motionScheme = MotionScheme.expressive())` | 1.4 stable |
| 状态主标题 | `MaterialTheme.typography.headlineLargeEmphasized` | 1.4 stable |
| 组标题 | `titleMediumEmphasized` | 1.4 stable |
| 波浪无限进度（开关外圈/加载） | `LoadingIndicator()` / `ContainedLoadingIndicator()` | `ExperimentalMaterial3ExpressiveApi` |
| 主页 app bar | `TopAppBar` + settings `IconButton` | 已落地 |
| 设置页大标题栏 | `MediumFlexibleTopAppBar` + `exitUntilCollapsedScrollBehavior` | `ExperimentalMaterial3ExpressiveApi`（1.5 转正） |
| 圆形 Hero 开关 | `Surface`/`Button` + `CircleShape`，动画用 `animateShapeAsState` 或 `Modifier.animateContentSize` + 自定义 shape | morph 完整版可选 `androidx.graphics:graphics-shapes` |
| 底部固定保存栏 | `Scaffold(bottomBar = { Surface { Button } })` + `imePadding` | — |
| 条件展开字段 | `AnimatedVisibility` | spec 取自 `MaterialTheme.motionScheme` |
| 页面转场 | `NavDisplay(transitionSpec / popTransitionSpec)` | shared axis 风格 |
| 图标 | 本地 vector drawable（Material Symbols Rounded） | 删除 `material-icons-core` |

## 三、分阶段实施计划

| 阶段 | 内容 | 风险/依赖 |
|------|------|-----------|
| 1 主题基线 | 品牌色 ColorScheme + success 扩展色、Emphasized 字体、`MotionScheme.expressive()`、Material Symbols 本地图标（移除 icons-core） | 需确认品牌种子色 |
| 2 连接页重构 | Hero 圆形开关（四态 + 波浪进度）、状态文案、详情卡片、空态/失败卡片、`hasProfile` 只读状态接入 | 数据层新增只读标志 |
| 3 设置页重构 | `MediumFlexibleTopAppBar`、分组卡片、底部固定保存栏、`AnimatedVisibility` 字段 | — |
| 4 动效与转场 | 开关 morph、状态 crossfade、NavDisplay shared axis、触感反馈 | — |
| 5 打磨 | 大屏双栏、减少动态降级、截图测试（Phone/Dark/Tablet） | 需设备或截图基线 |

每阶段验证：`./gradlew :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest :app:assembleDebug`；有设备时加 `:app:connectedDebugAndroidTest`。

## 四、待确认的决策点（已全部确认，2026-10-09）

1. **品牌种子色**：冷色科技蓝 ✅（已实现）
2. **保存机制**：显式底部保存按钮 ✅（已实现）
3. **形状 morph**：接受 `graphics-shapes` 依赖 ✅（实施时改用动画圆角实现，未引入该依赖，见下）
4. **连接时长显示**：显示 ✅（已实现，见下）

## 五、实施记录（2026-10-09，阶段 1~4 已完成）

### 阶段 1 主题基线 ✅
- `ui/theme/Color.kt`：冷色科技蓝调色板（蓝/蓝灰/青三角色）替换模板紫；新增 Green success 系列扩展语义色。
- `ui/theme/Theme.kt`：`ExtendedColors` + `LocalExtendedColors`（`extendedColors` 访问器）；圆角基线调大（xs 12 / s 16 / m 20 / l 28 / xl 36）；`MaterialTheme(motionScheme = MotionScheme.expressive())`。
- `ui/icons/AppIcons.kt`：11 个 Material Symbols Rounded 本地图标（脚本从 fonts.gstatic.com 导出并做视口平移）；**删除 `material-icons-core` 依赖**。

### 阶段 2 连接页重构 ✅
- Hero：144dp 圆形电源开关（`PowerIcon`），四态容器/内容色（未连接 surfaceVariant、过渡 primaryContainer、已连接 success、失败 errorContainer）；STARTING 时外圈 `CircularWavyProgressIndicator` 环绕；按下触感反馈；contentDescription 随状态变化；STOPPING 禁用。
- 状态主标题 `headlineLargeEmphasized` + 颜色 effects 动画 + `AnimatedContent` 交叉淡入；副文案含连接时长（`connectedAtEpochMs`，每 30 秒刷新，分钟/小时粒度）。
- 详情卡片（ListItem 行 + 分隔线，图标：vpn_key/memory/lan/router）仅 STARTING/STOPPING/CONNECTED 显示。
- 未配置空态卡片（`hasProfile=false`，info 图标 + “前往配置”）；失败错误卡片（errorContainer，原因 + 重试/检查设置）。
- 数据层：`ConnectionStatus.connectedAtEpochMs`（监控循环保留首次连接时刻）；`ConnectionProfileRepository.hasConnectableProfile()`；ViewModel 以函数注入查询、页面 resumed 时刷新；`ConnectionUiState.hasProfile`。

### 阶段 3 设置页重构 ✅
- `MediumFlexibleTopAppBar` + `exitUntilCollapsedScrollBehavior` 大标题滚动收缩。
- 三组分组卡片（titleMediumEmphasized 组标题 + 20dp 圆角 Card）；`AnimatedVisibility` 条件展示静态 IPv4 字段。
- 底部固定保存栏（`Scaffold.bottomBar` + tonalElevation + `imePadding`），保存中内嵌进度。
- 加载态改为 `LoadingIndicator()`（Expressive 波浪指示）。

### 阶段 4 动效与转场 ✅
- Hero 形状 morph：已连接时 圆(72dp 圆角) → 圆角方形(32dp) 的 `animateDpAsState`（expressive spatial spec）。**因此未引入 `graphics-shapes`**：动画圆角已达成设计效果，避免为用不到的 API 加依赖。
- NavDisplay 前进/后退 shared axis 风格转场（水平滑动 1/4 屏 + 淡入淡出）。

### 阶段 5 打磨（部分完成）
- 大屏双栏：≥840dp 时左 Hero 右详情 ✅；设置表单限宽 600dp ✅。
- 各状态 Preview（空态/已连接/错误 × Phone/Dark/Tablet）✅；截图测试框架未引入。
- 减少动态降级未做：波浪环属加载指示而非装饰动画，保持常开。

### 依赖变化
- **material3 1.4.0 → 显式固定 1.5.0-beta01**（覆盖 BOM）：Expressive API（`MotionScheme`、Emphasized 字阶、`MediumFlexibleTopAppBar`、`CircularWavyProgressIndicator`、`LoadingIndicator`）在 1.4.0 stable 仍为 internal，1.5.0-beta01 才公开。后续 1.5 stable 发布后应移除显式版本回归 BOM 管理。
- 移除 `material-icons-core`（官方已停更，图标迁移 Material Symbols）。

### 已执行检查
- `:app:ktlintCheck`、`:app:lintDebug`、`:app:testDebugUnitTest`（含新增 hasProfile 用例）、`:app:assembleDebug` 全部通过。
- 初次实现时未验证：当时无连接设备，androidTest 与真机 UI 流程（VPN 授权 → 连接 → 时长刷新 → 保存返回空态消失）未跑。后续 UI 修复的验证结果见下节。

## 六、UI 审查问题修复（2026-10-09）

- 保存栏在 Surface 内部使用 `navigationBarsPadding().imePadding()`，由 inset 消费机制避免重复累加；表单应用并消费 Scaffold 的 `contentPadding`，底栏背景继续覆盖屏幕底部。
- 返回图标的起点从 `m 313 -440` 修正为 `m 313 520`，消除错误坐标造成的竖线和箭头畸变。
- ≥840dp 的连接页双栏分别持有滚动状态；短内容垂直居中，超高内容可独立滚动到末尾。新增未配置、已连接、错误三种状态在 840×360dp、1.0/2.0 字体倍率下的 Preview。
- 使用本机 JDK 25 执行 `:app:ktlintCheck :app:lintDebug :app:testDebugUnitTest :app:assembleDebug`，全部通过；API 37 模拟器上的 `:app:connectedDebugAndroidTest` 通过。
- 可控 Preview 实机渲染确认六组矮窗口状态/字体组合的滚动可达性，以及 1280×800dp 平板的内容居中；真实设置页确认浅色/深色返回箭头显示正常，点击可回到连接页。
- 手势导航与三键导航分别检查键盘收起、普通软键盘展开及关闭：保存按钮完整位于系统导航栏或键盘上方，网络名与路由输入框可滚动进入可视区，未见重复底部留白。检查仅填写临时表单内容，未保存 profile；显示尺寸、密度、字体、主题、导航模式及临时输入设置均已恢复。
- VPN 授权、真实连接及设备厂商差异未在本轮验证；已连接和错误布局使用可控 Preview 数据，不依赖真实内核或网络。
