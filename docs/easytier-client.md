# EasyTier Android 客户端实现

## 连接流程

1. 首页调用 `VpnService.prepare()`。系统授权完成后，页面通过 `AndroidConnectionRepository` 发送显式连接 Intent。
2. `EasyTierVpnService` 立即启动前台服务并显示常驻通知，然后从 Keystore 加密存储读取 profile。
3. 服务生成 EasyTier v2.6.4 TOML，在后台线程调用 JNI 启动网络实例，并轮询运行信息直到分配虚拟 IPv4。
4. 服务根据虚拟 IPv4、配置路由、peer 发布的 `proxy_cidrs` 和 Magic DNS 建立 TUN，再把文件描述符交给 EasyTier。
5. 服务每两秒读取运行信息。peer 连接状态变化会更新页面；路由集合变化时重新建立 TUN 路由并更新 EasyTier 文件描述符。
6. 应用或通知中的断开操作会先停止 EasyTier 实例，再关闭 TUN 并撤销前台通知。

首页分别展示 VPN TUN、EasyTier 内核和已连接 peer。内核已启动但 peer 数为零时仍显示内核状态，避免把服务已运行误报为组网成功。

## VPN 路由边界

- TUN MTU 为 1300。
- 只加入用户配置的 IPv4 CIDR、EasyTier 运行信息中 peer 发布的 `proxy_cidrs`，以及 Magic DNS 使用的 `100.100.100.101/32`。
- profile 校验拒绝前缀为 `/0` 的路由；运行时也会过滤默认路由。实现不添加 IPv4 或 IPv6 默认路由，也不建立 IPv6 地址。
- 使用 `VpnService.Builder.addDisallowedApplication()` 排除 EasytierKT 自身，避免 EasyTier 的传输 socket 被再次送回 TUN。
- Magic DNS 同时在 EasyTier `flags.accept_dns` 中开启，并向 Android VPN Builder 配置 `100.100.100.101` DNS。

路由变化通过新 `VpnService.Builder` 重新建立 TUN。Android 会替换旧 VPN 接口，因此切换期间可能有短暂丢包。

## 配置与密钥

单 profile 支持网络名、可选网络密钥、一个或多个 peer URL、DHCP 或静态 IPv4、IPv4 CIDR 路由和 Magic DNS。peer URL 接受 `tcp`、`udp`、`ws`、`wss` 和 `quic` scheme。保存配置后，下次连接生效。

整个 profile 使用 AES/GCM 加密后写入私有 SharedPreferences；AES-256 密钥保存在 Android Keystore。Android 备份与设备迁移规则排除 `easytier_profile.xml`，防止仅恢复密文而没有 Keystore 密钥。应用不会记录 TOML 或 profile 中的网络密钥。

## Service 和权限

Manifest 声明 `INTERNET`、`ACCESS_NETWORK_STATE` 和前台服务权限。`EasyTierVpnService` 声明 `BIND_VPN_SERVICE`，由于 Android 系统需要绑定 VPN 服务，组件为 exported；签名级系统权限限制绑定方。应用向该 Service 发送的连接 Intent 都是显式 Intent。

Android 14 及以上使用 `systemExempted` 前台服务类型。连接由用户在前台点按触发。服务使用 `START_STICKY`，系统回收进程后会通过空 Intent 恢复，并重新读取已保存配置。未注册开机广播；设备重启后由用户手动连接。

通知操作使用显式 Service 或 Activity PendingIntent，并设为 immutable。VPN 被系统撤销时，服务会停止内核实例并关闭 TUN。

## JNI 与原生库

JNI 接口类必须保持上游导出符号所要求的名称 `com.easytier.jni.EasyTierJNI`；对应 R8 keep 规则禁止混淆该类。EasyTier v2.6.4 需要以下两个共享库：

- `libeasytier_android_jni.so`
- `libeasytier_ffi.so`

上游 commit 固定为 `8428a89d2dabc94c97d370ec607c6ca142473626`。`tools/build-easytier-android.sh` 使用 Rust stable、`cargo-ndk 3.5.4` 和 Android NDK `27.2.12479018`，为 arm64、32 位 ARM、x86 和 x86_64 构建。脚本生成的库写入 `app/src/main/jniLibs/` 并被 Git 忽略。CI 在 Android 检查前构建这些库；本地构建前也必须运行脚本。

构建依赖 GitHub 上游源码和 crates.io。当前 Windows 开发建议在 WSL2 中执行脚本。没有原生库时，Android APK 可以通过 Kotlin 编译，但运行连接会显示缺少原生库错误。

## 已知范围

- 仅一个 profile；没有开机自启动、后台自动重连开关或连接历史。
- 当前 TUN 配置仅支持 IPv4；不支持 IPv6 路由、exit node 或默认路由。
- peer 状态和运行时发布路由通过两秒轮询获取。
- VPN 授权与真实组网必须在设备或模拟器上手动验证；CI 只构建原生库并运行 Android Lint、JVM 单元测试和 APK 构建。

## 第三方许可证

EasyTier v2.6.4 的仓库许可证为 LGPL-3.0（含上游许可证文本中的附加条款）。对应许可证副本和 GNU GPL-3.0 文本放在 `app/src/main/assets/licenses/`，会随应用打包。源码与构建脚本指向固定 commit，便于取得和重建对应版本。第三方声明见 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。
