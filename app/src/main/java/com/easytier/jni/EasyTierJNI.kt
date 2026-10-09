package com.easytier.jni

/** EasyTier v2.6.4 的 JNI 绑定依赖本对象的包名和类名，R8 规则会保留该类。 */
object EasyTierJNI {
    init {
        System.loadLibrary("easytier_ffi")
        System.loadLibrary("easytier_android_jni")
    }

    @JvmStatic
    external fun parseConfig(config: String): Int

    @JvmStatic
    external fun runNetworkInstance(config: String): Int

    /** 空输入生成身份，非空输入派生公钥；失败抛出不含密钥的错误。 */
    @JvmStatic
    external fun prepareSecureIdentity(privateKey: String): String

    @JvmStatic
    external fun setTunFd(
        instanceName: String,
        fd: Int,
    ): Int

    @JvmStatic
    external fun retainNetworkInstance(instanceNames: Array<String>?): Int

    @JvmStatic
    external fun collectNetworkInfos(): String?

    @JvmStatic
    external fun getLastError(): String?

    @JvmStatic
    fun stopAllInstances(): Int = retainNetworkInstance(null)
}
