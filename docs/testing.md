# 测试说明

## 测试分层

- `src/test`：Repository 状态和 ViewModel 状态映射等 JVM 单元测试。
- `src/androidTest`：Compose 页面、Navigation 3 跳转、系统返回和 Activity 重建后的返回栈恢复测试。
- Preview 使用显式 UI 状态，不依赖真实内核、网络或本地用户数据。

暂不加入截图测试、Robolectric、MockK 或端到端框架。连接业务与内核尚未接入，不测试连接操作。

## 本地命令

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

GitHub Actions 首轮执行格式检查、Android Lint、单元测试和 Debug 构建；设备测试在本地连接设备后运行。
