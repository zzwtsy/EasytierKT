package com.github.zzwtsy.easytierkt.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Base64

class CredentialKeyReaderTest {
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { 7 })

    /** 正常 UTF-8 私钥文件带末尾换行时，读取后只保留 Base64 内容。 */
    @Test
    fun trimsWhitespaceAroundKey() {
        assertEquals(key, CredentialKeyReader.read(ByteArrayInputStream("$key\n".toByteArray())))
    }

    /** 超过 4 KiB、空文件、非法 UTF-8 和长度错误的 Base64 均失败，不能触发生成新身份。 */
    @Test
    fun rejectsOversizeAndInvalidFiles() {
        listOf(ByteArray(4097) { 32 }, byteArrayOf(), byteArrayOf(0xc3.toByte()), "aGVsbG8=".toByteArray()).forEach { bytes ->
            assertTrue(runCatching { CredentialKeyReader.read(ByteArrayInputStream(bytes)) }.isFailure)
        }
    }
}
