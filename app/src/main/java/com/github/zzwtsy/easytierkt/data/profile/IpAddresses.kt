package com.github.zzwtsy.easytierkt.data.profile

import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import java.net.InetAddress

/** 数字地址的统一边界；限制库的扩展语法，任何路径都不解析主机名。 */
internal object IpAddresses {
    fun parse(value: String): IPAddress? {
        val text = value.trim()
        if (':' in text) {
            if (!text.matches(Regex("[0-9a-fA-F:]+"))) return null
        } else if (!text.matches(Regex("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}"))) {
            return null
        }
        val ip = IPAddressString(text).address ?: return null
        val bytes = ip.bytes
        if (bytes.size == 16 &&
            bytes.take(10).all { it == 0.toByte() } &&
            bytes[10] == (-1).toByte() &&
            bytes[11] == (-1).toByte()
        ) {
            return null
        }
        return ip
    }

    fun network(value: String): IPAddress? {
        val parts = value.trim().split('/')
        if (parts.size != 2 || !parts[1].matches(Regex("[0-9]+"))) return null
        val address = parse(parts[0]) ?: return null
        val prefix = parts[1].toIntOrNull()?.takeIf { it in 0..address.bitCount } ?: return null
        return address.toPrefixBlock(prefix)
    }

    fun contains(
        cidr: String,
        address: String,
    ): Boolean {
        val network = network(cidr) ?: return false
        val target = parse(address) ?: return false
        return network.contains(target)
    }
}

fun numericIp(value: String): InetAddress? = IpAddresses.parse(value)?.let { InetAddress.getByAddress(it.bytes) }

/** CIDR 只用于网络；接口地址仍保留主机位，IPv6 使用小写压缩形式。 */
fun normalizedCidr(value: String): String? = IpAddresses.network(value)?.toCompressedString()

internal fun normalizedAclAddress(value: String): String? =
    normalizedCidr(if ('/' in value) value else "$value/${if (':' in value) 128 else 32}")
