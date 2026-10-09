package com.github.zzwtsy.easytierkt.data.profile

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** 只读取最多 4 KiB 的 UTF-8 Base64 私钥；调用方负责关闭 SAF 流，错误不包含文件内容。 */
internal object CredentialKeyReader {
    fun read(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (output.size() <= 4096) {
            val count = input.read(buffer, 0, minOf(buffer.size, 4097 - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        require(output.size() <= 4096) { "Private key file too large" }
        val key =
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output.toByteArray()))
                .toString()
                .trim()
        require(validKey(key)) { "Invalid private key" }
        return key
    }
}
