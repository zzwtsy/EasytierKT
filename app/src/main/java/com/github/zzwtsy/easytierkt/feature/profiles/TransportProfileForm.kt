package com.github.zzwtsy.easytierkt.feature.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.profile.ConnectionProfile
import com.github.zzwtsy.easytierkt.data.profile.P2pMode
import com.github.zzwtsy.easytierkt.data.profile.ProfileField
import com.github.zzwtsy.easytierkt.data.profile.ProfileSection

@Composable
internal fun TransportProfileForm(
    p: ConnectionProfile,
    enabled: Boolean,
    draft: ProfileDraft,
    invalidNumbers: Set<ProfileField>,
    issues: List<com.github.zzwtsy.easytierkt.data.profile.ProfileIssue>,
    focusedIssue: ProfileField?,
    onChange: (ConnectionProfile) -> Unit,
) {
    FormSection(stringResource(R.string.profile_section_transport), reveal = focusedIssue?.section == ProfileSection.TRANSPORT) {
        FormChoice(stringResource(R.string.profile_p2p_mode), p.transport.p2pMode, P2pMode.entries, enabled) {
            onChange(p.copy(transport = p.transport.copy(p2pMode = it, lazyP2p = false)))
        }
        ProfileTextField(
            state = draft.state("transport.defaultProtocol"),
            enabled = enabled,
            issue = issues.issueAt(draft.field("transport.defaultProtocol")),
            label = stringResource(R.string.profile_default_protocol),
        )
        ProfileTextField(
            state = draft.state("transport.encryptionAlgorithm"),
            enabled = enabled,
            issue = issues.issueAt(draft.field("transport.encryptionAlgorithm")),
            label = stringResource(R.string.profile_encryption_algorithm),
        )
        ProfileTextField(
            state = draft.state("transport.relayWhitelist"),
            enabled = enabled,
            issue = issues.issueAt(draft.field("transport.relayWhitelist")),
            label = stringResource(R.string.profile_relay_whitelist),
        )
        ProfileTextField(
            state = draft.state("transport.stunServers"),
            enabled = enabled,
            issue = issues.issueAt(draft.field("transport.stunServers")),
            label = stringResource(R.string.profile_stun_servers),
        )
        ProfileTextField(
            state = draft.state("transport.stunServersV6"),
            enabled = enabled,
            issue = issues.issueAt(draft.field("transport.stunServersV6")),
            label = stringResource(R.string.profile_stun_servers_v6),
        )
        ProfileNumberField(
            state = draft.state("transport.mtu"),
            invalid = draft.field("transport.mtu") in invalidNumbers,
            enabled = enabled,
            issue = issues.issueAt(draft.field("transport.mtu")),
            label = stringResource(R.string.profile_mtu),
        )
        ProfileNumberField(
            state = draft.state("transport.threadCount"),
            invalid = draft.field("transport.threadCount") in invalidNumbers,
            enabled = enabled,
            issue = issues.issueAt(draft.field("transport.threadCount")),
            label = stringResource(R.string.profile_thread_count),
        )
        ProfileNumberField(
            state = draft.state("transport.receiveBytesPerSecond"),
            invalid =
                draft.field("transport.receiveBytesPerSecond") in invalidNumbers,
            enabled = enabled,
            issue = issues.issueAt(draft.field("transport.receiveBytesPerSecond")),
            label = stringResource(R.string.profile_receive_rate),
        )
        ProfileNumberField(
            state = draft.state("transport.foreignRelayBytesPerSecond"),
            invalid =
                draft.field("transport.foreignRelayBytesPerSecond") in invalidNumbers,
            enabled = enabled,
            issue = issues.issueAt(draft.field("transport.foreignRelayBytesPerSecond")),
            label = stringResource(R.string.profile_foreign_relay_rate),
        )
        ProfileToggle(
            enabled,
            stringResource(R.string.profile_ipv6_transport),
            p.transport.ipv6Transport,
        ) { onChange(p.copy(transport = p.transport.copy(ipv6Transport = it))) }
        ProfileToggle(enabled, stringResource(R.string.profile_lazy_p2p), p.transport.lazyP2p) {
            onChange(p.copy(transport = p.transport.copy(lazyP2p = it)))
        }
        ProfileToggle(enabled, stringResource(R.string.profile_need_p2p), p.transport.needP2p) {
            onChange(p.copy(transport = p.transport.copy(needP2p = it)))
        }
        ProfileToggle(enabled, stringResource(R.string.profile_latency_first), p.transport.latencyFirst) {
            onChange(p.copy(transport = p.transport.copy(latencyFirst = it)))
        }
        ProfileToggle(
            enabled,
            stringResource(R.string.profile_tcp_hole_punching),
            p.transport.tcpHolePunching,
        ) { onChange(p.copy(transport = p.transport.copy(tcpHolePunching = it))) }
        ProfileToggle(
            enabled,
            stringResource(R.string.profile_udp_hole_punching),
            p.transport.udpHolePunching,
        ) { onChange(p.copy(transport = p.transport.copy(udpHolePunching = it))) }
        ProfileToggle(
            enabled,
            stringResource(R.string.profile_symmetric_hole_punching),
            p.transport.symmetricHolePunching,
        ) { onChange(p.copy(transport = p.transport.copy(symmetricHolePunching = it))) }
        ProfileToggle(enabled, stringResource(R.string.profile_upnp), p.transport.upnp) {
            onChange(p.copy(transport = p.transport.copy(upnp = it)))
        }
        ProfileToggle(enabled, stringResource(R.string.profile_bind_device), p.transport.bindDevice) {
            onChange(p.copy(transport = p.transport.copy(bindDevice = it)))
        }
        ProfileToggle(enabled, stringResource(R.string.profile_encryption), p.transport.encryption) {
            onChange(p.copy(transport = p.transport.copy(encryption = it)))
        }
        ProfileToggle(enabled, stringResource(R.string.profile_compression), p.transport.compression) {
            onChange(p.copy(transport = p.transport.copy(compression = it)))
        }
        ProfileToggle(enabled, stringResource(R.string.profile_kcp_proxy), p.transport.kcpProxy) {
            onChange(p.copy(transport = p.transport.copy(kcpProxy = it)))
        }
        ProfileToggle(enabled, stringResource(R.string.profile_kcp_input), p.transport.kcpInput) {
            onChange(p.copy(transport = p.transport.copy(kcpInput = it)))
        }
        ProfileToggle(enabled, stringResource(R.string.profile_quic_proxy), p.transport.quicProxy) {
            onChange(p.copy(transport = p.transport.copy(quicProxy = it)))
        }
        ProfileToggle(enabled, stringResource(R.string.profile_quic_input), p.transport.quicInput) {
            onChange(p.copy(transport = p.transport.copy(quicInput = it)))
        }
        ProfileToggle(enabled, stringResource(R.string.profile_relay_data), p.transport.relayData) {
            onChange(p.copy(transport = p.transport.copy(relayData = it)))
        }
        ProfileToggle(
            enabled,
            stringResource(R.string.profile_relay_peer_rpc),
            p.transport.relayPeerRpc,
        ) { onChange(p.copy(transport = p.transport.copy(relayPeerRpc = it))) }
        ProfileToggle(enabled, stringResource(R.string.profile_relay_kcp), p.transport.relayKcp) {
            onChange(p.copy(transport = p.transport.copy(relayKcp = it)))
        }
        ProfileToggle(enabled, stringResource(R.string.profile_relay_quic), p.transport.relayQuic) {
            onChange(p.copy(transport = p.transport.copy(relayQuic = it)))
        }
        ProfileToggle(enabled, stringResource(R.string.profile_foreign_kcp), p.transport.foreignKcp) {
            onChange(p.copy(transport = p.transport.copy(foreignKcp = it)))
        }
        ProfileToggle(enabled, stringResource(R.string.profile_foreign_quic), p.transport.foreignQuic) {
            onChange(p.copy(transport = p.transport.copy(foreignQuic = it)))
        }
        ProfileToggle(enabled, stringResource(R.string.profile_private_mode), p.transport.privateMode) {
            onChange(p.copy(transport = p.transport.copy(privateMode = it)))
        }
        ProfileToggle(enabled, stringResource(R.string.profile_udp_broadcast), p.transport.udpBroadcast) {
            onChange(p.copy(transport = p.transport.copy(udpBroadcast = it)))
        }
        ProfileToggle(enabled, stringResource(R.string.profile_multi_thread), p.transport.multiThread) {
            onChange(p.copy(transport = p.transport.copy(multiThread = it)))
        }
    }
}
