package com.github.zzwtsy.easytierkt.feature.profiles

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.mutableStateMapOf
import com.github.zzwtsy.easytierkt.data.profile.AclRule
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.PortForward
import com.github.zzwtsy.easytierkt.data.profile.ProfileField

internal data class CompiledProfileDraft(
    val profile: ConnectionProfile,
    val displayName: String,
    val invalidNumbers: Set<ProfileField>,
)

/** 原始输入只存在于 TextFieldState；页面模型和错误均由同一个编译器派生，保存时同步重读。 */
class ProfileDraft(
    initial: ConnectionProfile = ConnectionProfile(),
    name: String = "",
) {
    private val fields = mutableStateMapOf<ProfileField, TextFieldState>()
    private val defaultForward = PortForward(id = "")
    private val defaultRule = AclRule(id = "")
    private var structure = initial
    private var fallback = project(initial, name)
    private var original = fallback
    val applicationSearch = TextFieldState()

    init {
        reconcile(fallback)
    }

    fun field(path: String): ProfileField = ProfileField.fromPath(path, structure)

    fun state(path: String): TextFieldState = requireNotNull(fields[field(path)]) { "Missing draft field: $path" }

    fun rawValues(): Map<ProfileField, String> = fields.mapValues { it.value.text.toString() }

    fun isDirty(): Boolean = rawValues() != original || compile().profile != initialStructure

    private var initialStructure = initial

    internal fun reset(
        profile: ConnectionProfile,
        name: String,
    ) {
        structure = profile
        initialStructure = profile
        fallback = project(profile, name)
        original = fallback
        reconcile(fallback, replaceAll = true)
    }

    /** 开关、枚举和条目增删来自页面模型；只同步实际改变的文本，保留未修改的非法数值。 */
    internal fun update(
        profile: ConnectionProfile,
        previous: ConnectionProfile,
    ) {
        val next = project(profile, state("displayName").text.toString())
        reconcile(next, previous = project(previous, state("displayName").text.toString()))
        structure = profile
    }

    private fun reconcile(
        next: Map<ProfileField, String>,
        previous: Map<ProfileField, String> = emptyMap(),
        replaceAll: Boolean = false,
    ) {
        fields.keys.retainAll(next.keys)
        next.forEach { (field, value) ->
            val state = fields[field]
            if (state == null) {
                fields[field] = TextFieldState(value)
            } else if (replaceAll || previous[field] != value) {
                state.setTextAndPlaceCursorAtEnd(value)
            }
        }
    }

    internal fun compile(): CompiledProfileDraft {
        val p = structure
        val invalid = mutableSetOf<ProfileField>()

        fun text(path: String): String = state(path).text.toString()

        fun number(
            path: String,
            default: Number?,
            int: Boolean = false,
            active: Boolean = true,
            optional: Boolean = false,
        ): Long? {
            val raw = text(path)
            if (optional && raw.isBlank()) return null
            val parsed = raw.toLongOrNull()?.takeIf { !int || it in Int.MIN_VALUE..Int.MAX_VALUE }
            if (parsed != null) return parsed
            if (active) invalid += field(path)
            return fallback[ProfileField.fromPath(path, p)]?.toLongOrNull() ?: default?.toLong()
        }
        val profile =
            p.copy(
                networkName = text("networkName"),
                networkSecret = text("networkSecret"),
                peerAddresses = text("peerAddresses"),
                ipv4Address = text("ipv4Address"),
                routes = text("routes"),
                hostname = text("hostname"),
                virtualIpv6 = text("virtualIpv6"),
                listeners = text("listeners"),
                mappedListeners = text("mappedListeners"),
                ipv4Prefix = number("ipv4Prefix", 24, int = true, active = !p.useDhcp)!!.toInt(),
                ipv6Prefix = number("ipv6Prefix", 64, int = true, active = text("virtualIpv6").isNotBlank())!!.toInt(),
                security =
                    p.security.copy(
                        privateKey = text("security.privateKey"),
                        publicKey = text("security.publicKey"),
                        peerKeys =
                            p.security.peerKeys.map { e ->
                                e.copy(
                                    uri = text("peerKey.${e.id}.uri"),
                                    publicKey = text("peerKey.${e.id}.publicKey"),
                                )
                            },
                    ),
                routing =
                    p.routing.copy(
                        ipv6Routes = text("routing.ipv6Routes"),
                        exitNodes = text("routing.exitNodes"),
                    ),
                dns =
                    p.dns.copy(
                        servers = text("dns.servers"),
                        zone = text("dns.zone"),
                    ),
                transport =
                    p.transport.copy(
                        defaultProtocol = text("transport.defaultProtocol"),
                        encryptionAlgorithm = text("transport.encryptionAlgorithm"),
                        relayWhitelist = text("transport.relayWhitelist"),
                        stunServers = text("transport.stunServers"),
                        stunServersV6 = text("transport.stunServersV6"),
                        mtu = number("transport.mtu", 1300, int = true, active = true, optional = false)!!.toInt(),
                        threadCount =
                            number(
                                "transport.threadCount",
                                2,
                                int = true,
                                active = p.transport.multiThread,
                                optional = false,
                            )!!.toInt(),
                        receiveBytesPerSecond =
                            number(
                                "transport.receiveBytesPerSecond",
                                null,
                                int = false,
                                active = true,
                                optional = true,
                            ),
                        foreignRelayBytesPerSecond =
                            number(
                                "transport.foreignRelayBytesPerSecond",
                                null,
                                int = false,
                                active = true,
                                optional = true,
                            ),
                    ),
                localProxy =
                    p.localProxy.copy(
                        bindAddress = text("localProxy.bindAddress"),
                        port = number("localProxy.port", 1080, int = true, active = p.localProxy.socks5, optional = false)!!.toInt(),
                        forwards =
                            p.localProxy.forwards.map { e ->
                                e.copy(
                                    protocol = text("forward.${e.id}.protocol"),
                                    bindAddress = text("forward.${e.id}.bindAddress"),
                                    destination = text("forward.${e.id}.destination"),
                                    bindPort = number("forward.${e.id}.bindPort", defaultForward.bindPort, int = true)!!.toInt(),
                                    destinationPort =
                                        number(
                                            "forward.${e.id}.destinationPort",
                                            defaultForward.destinationPort,
                                            int = true,
                                        )!!.toInt(),
                                )
                            },
                    ),
                acl =
                    p.acl.copy(
                        members = text("acl.members"),
                        declares =
                            p.acl.declares.map { e ->
                                e.copy(
                                    name = text("group.${e.id}.name"),
                                    secret = text("group.${e.id}.secret"),
                                )
                            },
                        chains =
                            p.acl.chains.map { e ->
                                e.copy(
                                    name = text("chain.${e.id}.name"),
                                    rules =
                                        e.rules.map { r ->
                                            r.copy(
                                                name = text("rule.${r.id}.name"),
                                                description = text("rule.${r.id}.description"),
                                                sourceIps = text("rule.${r.id}.sourceIps"),
                                                destinationIps = text("rule.${r.id}.destinationIps"),
                                                sourcePorts = text("rule.${r.id}.sourcePorts"),
                                                destinationPorts = text("rule.${r.id}.destinationPorts"),
                                                sourceGroups = text("rule.${r.id}.sourceGroups"),
                                                destinationGroups = text("rule.${r.id}.destinationGroups"),
                                                priority =
                                                    number(
                                                        "rule.${r.id}.priority",
                                                        defaultRule.priority,
                                                        int = true,
                                                        active = p.acl.enabled,
                                                    )!!.toInt(),
                                                rateLimit =
                                                    number(
                                                        "rule.${r.id}.rateLimit",
                                                        defaultRule.rateLimit,
                                                        int = false,
                                                        active = p.acl.enabled,
                                                    )!!,
                                                burstLimit =
                                                    number(
                                                        "rule.${r.id}.burstLimit",
                                                        defaultRule.burstLimit,
                                                        int = false,
                                                        active = p.acl.enabled,
                                                    )!!,
                                            )
                                        },
                                )
                            },
                    ),
            )
        return CompiledProfileDraft(profile, text("displayName"), invalid)
    }
}

/** 明确列举各业务字段，不使用反射或通用表单 DSL。 */
private fun project(
    p: ConnectionProfile,
    name: String,
): Map<ProfileField, String> =
    buildMap {
        fun add(
            path: String,
            value: Any?,
        ) {
            put(ProfileField.fromPath(path, p), value?.toString().orEmpty())
        }
        add("displayName", name)
        add("networkName", p.networkName)
        add("networkSecret", p.networkSecret)
        add("peerAddresses", p.peerAddresses)
        add("ipv4Address", p.ipv4Address)
        add("routes", p.routes)
        add("hostname", p.hostname)
        add("virtualIpv6", p.virtualIpv6)
        add("listeners", p.listeners)
        add("mappedListeners", p.mappedListeners)
        add("security.privateKey", p.security.privateKey)
        add("security.publicKey", p.security.publicKey)
        add("routing.ipv6Routes", p.routing.ipv6Routes)
        add("routing.exitNodes", p.routing.exitNodes)
        add("dns.servers", p.dns.servers)
        add("dns.zone", p.dns.zone)
        add("transport.defaultProtocol", p.transport.defaultProtocol)
        add("transport.encryptionAlgorithm", p.transport.encryptionAlgorithm)
        add("transport.relayWhitelist", p.transport.relayWhitelist)
        add("transport.stunServers", p.transport.stunServers)
        add("transport.stunServersV6", p.transport.stunServersV6)
        add("localProxy.bindAddress", p.localProxy.bindAddress)
        add("acl.members", p.acl.members)
        add("ipv4Prefix", p.ipv4Prefix)
        add("ipv6Prefix", p.ipv6Prefix)
        add("transport.mtu", p.transport.mtu)
        add("transport.threadCount", p.transport.threadCount)
        add("transport.receiveBytesPerSecond", p.transport.receiveBytesPerSecond)
        add("transport.foreignRelayBytesPerSecond", p.transport.foreignRelayBytesPerSecond)
        add("localProxy.port", p.localProxy.port)
        p.security.peerKeys.forEach { e ->
            add("peerKey.${e.id}.uri", e.uri)
            add("peerKey.${e.id}.publicKey", e.publicKey)
        }
        p.localProxy.forwards.forEach { e ->
            add("forward.${e.id}.protocol", e.protocol)
            add("forward.${e.id}.bindAddress", e.bindAddress)
            add("forward.${e.id}.destination", e.destination)
            add("forward.${e.id}.bindPort", e.bindPort)
            add("forward.${e.id}.destinationPort", e.destinationPort)
        }
        p.acl.declares.forEach { e ->
            add("group.${e.id}.name", e.name)
            add("group.${e.id}.secret", e.secret)
        }
        p.acl.chains.forEach { e ->
            add("chain.${e.id}.name", e.name)
            e.rules.forEach { r ->
                add("rule.${r.id}.name", r.name)
                add("rule.${r.id}.description", r.description)
                add("rule.${r.id}.sourceIps", r.sourceIps)
                add("rule.${r.id}.destinationIps", r.destinationIps)
                add("rule.${r.id}.sourcePorts", r.sourcePorts)
                add("rule.${r.id}.destinationPorts", r.destinationPorts)
                add("rule.${r.id}.sourceGroups", r.sourceGroups)
                add("rule.${r.id}.destinationGroups", r.destinationGroups)
                add("rule.${r.id}.priority", r.priority)
                add("rule.${r.id}.rateLimit", r.rateLimit)
                add("rule.${r.id}.burstLimit", r.burstLimit)
            }
        }
    }
