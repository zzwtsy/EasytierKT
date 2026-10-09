# 第三方声明

## EasyTier

- 组件：EasyTier Android JNI 与 FFI
- 版本：v2.6.4
- 源码 commit：`8428a89d2dabc94c97d370ec607c6ca142473626`
- 源码：[EasyTier/EasyTier](https://github.com/EasyTier/EasyTier/tree/8428a89d2dabc94c97d370ec607c6ca142473626)
- 许可证：LGPL-3.0，含上游仓库 `LICENSE` 文件中的附加条款
- 随应用包含的许可证文本：`app/src/main/assets/licenses/EasyTier-LICENSE.txt` 和 `app/src/main/assets/licenses/GPL-3.0.txt`

应用打包时包含由 EasyTier 上游构建的 `libeasytier_android_jni.so` 与 `libeasytier_ffi.so`。构建脚本 `tools/build-easytier-android.sh` 获取相同源码 commit 并生成这两个共享库。发行这些共享库时，应一并保留上述许可证文本及对应源码获取方式，并允许用户替换兼容的 LGPL 库版本。

## IPAddress、tomlkt 与 AndroidX DataStore

| 组件 | 版本 | 用途 | 源码与许可证 |
| --- | --- | --- | --- |
| IPAddress | 5.6.2 | 数字地址、CIDR、规范化和包含判断 | [seancfoley/IPAddress](https://github.com/seancfoley/IPAddress)，Apache-2.0 |
| tomlkt | 0.6.0 | Kotlin Serialization DTO 的 TOML 编码 | [eav-eav-eav/tomlkt](https://github.com/eav-eav-eav/tomlkt)，Apache-2.0 |
| AndroidX DataStore | 1.2.1 | 类型化配置存储与原子文件替换 | [AndroidX](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/datastore/)，Apache-2.0 |

Apache-2.0 许可证文本随应用包含于 `app/src/main/assets/licenses/Apache-2.0.txt`。版本以 `gradle/libs.versions.toml` 为准；Compose Foundation 继续使用仓库 Compose BOM 版本。
