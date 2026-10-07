# EasytierKT

EasytierKT 是基于 Kotlin、Jetpack Compose、Material 3 和 Navigation 3 的 Android 客户端。

## 开发环境

- JDK 25（Gradle Daemon 工具链由 `gradle/gradle-daemon-jvm.properties` 固定）
- Android SDK Platform 37
- 使用仓库中的 Gradle Wrapper，不单独安装或切换 Gradle 版本

## 构建与检查

Windows PowerShell：

```powershell
.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:ktlintCheck
```

macOS / Linux：

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:ktlintCheck
```

连接设备或启动模拟器后，可运行 Compose 导航测试：

```bash
./gradlew :app:connectedDebugAndroidTest
```

工程结构、状态边界和测试约定见 [架构说明](docs/architecture.md) 与 [测试说明](docs/testing.md)。
