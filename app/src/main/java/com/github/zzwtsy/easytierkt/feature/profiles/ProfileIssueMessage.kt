package com.github.zzwtsy.easytierkt.feature.profiles

import com.github.zzwtsy.easytierkt.R
import com.github.zzwtsy.easytierkt.data.profile.ProfileIssueCode

internal fun ProfileIssueCode.messageResource(): Int =
    when (this) {
        ProfileIssueCode.CIDR -> R.string.profile_issue_cidr
        ProfileIssueCode.ENDPOINT -> R.string.profile_issue_endpoint
        ProfileIssueCode.PORT_RANGE -> R.string.profile_issue_port_range
        ProfileIssueCode.NETWORK_NAME -> R.string.profile_issue_network_name
        ProfileIssueCode.NETWORK_SECRET -> R.string.profile_issue_network_secret
        ProfileIssueCode.HOSTNAME -> R.string.profile_issue_hostname
        ProfileIssueCode.STATIC_IPV4 -> R.string.profile_issue_static_ipv4
        ProfileIssueCode.IPV4_PREFIX -> R.string.profile_issue_ipv4_prefix
        ProfileIssueCode.VIRTUAL_IPV6 -> R.string.profile_issue_virtual_ipv6
        ProfileIssueCode.IPV6_PREFIX -> R.string.profile_issue_ipv6_prefix
        ProfileIssueCode.DUPLICATE_LISTENER -> R.string.profile_issue_duplicate_listener
        ProfileIssueCode.IPV6_ROUTE_WITHOUT_ADDRESS -> R.string.profile_issue_ipv6_route_without_address
        ProfileIssueCode.EXIT_NODE -> R.string.profile_issue_exit_node
        ProfileIssueCode.EMPTY_APPLICATIONS -> R.string.profile_issue_empty_applications
        ProfileIssueCode.APPLICATION -> R.string.profile_issue_application
        ProfileIssueCode.PRIVATE_KEY -> R.string.profile_issue_private_key
        ProfileIssueCode.PUBLIC_KEY -> R.string.profile_issue_public_key
        ProfileIssueCode.ENCRYPTION_REQUIRED -> R.string.profile_issue_encryption_required
        ProfileIssueCode.SECURE_HANDSHAKE_REQUIRED -> R.string.profile_issue_secure_handshake_required
        ProfileIssueCode.PEER_PIN -> R.string.profile_issue_peer_pin
        ProfileIssueCode.DUPLICATE_PEER_PIN -> R.string.profile_issue_duplicate_peer_pin
        ProfileIssueCode.DNS_SERVER -> R.string.profile_issue_dns_server
        ProfileIssueCode.DNS_ZONE -> R.string.profile_issue_dns_zone
        ProfileIssueCode.MTU -> R.string.profile_issue_mtu
        ProfileIssueCode.TRANSPORT_PROTOCOL -> R.string.profile_issue_transport_protocol
        ProfileIssueCode.ENCRYPTION_ALGORITHM -> R.string.profile_issue_encryption_algorithm
        ProfileIssueCode.THREAD_COUNT -> R.string.profile_issue_thread_count
        ProfileIssueCode.RECEIVE_LIMIT -> R.string.profile_issue_receive_limit
        ProfileIssueCode.RELAY_LIMIT -> R.string.profile_issue_relay_limit
        ProfileIssueCode.LAZY_P2P -> R.string.profile_issue_lazy_p2p
        ProfileIssueCode.STUN_SERVER -> R.string.profile_issue_stun_server
        ProfileIssueCode.BIND_ADDRESS -> R.string.profile_issue_bind_address
        ProfileIssueCode.BIND_PORT -> R.string.profile_issue_bind_port
        ProfileIssueCode.PORT_FORWARD -> R.string.profile_issue_port_forward
        ProfileIssueCode.BIND_CONFLICT -> R.string.profile_issue_bind_conflict
        ProfileIssueCode.DUPLICATE_CHAIN -> R.string.profile_issue_duplicate_chain
        ProfileIssueCode.GROUP -> R.string.profile_issue_group
        ProfileIssueCode.DUPLICATE_GROUP -> R.string.profile_issue_duplicate_group
        ProfileIssueCode.CHAIN_NAME -> R.string.profile_issue_chain_name
        ProfileIssueCode.RULE_NAME -> R.string.profile_issue_rule_name
        ProfileIssueCode.PRIORITY -> R.string.profile_issue_priority
        ProfileIssueCode.DUPLICATE_PRIORITY -> R.string.profile_issue_duplicate_priority
        ProfileIssueCode.RATE_LIMIT -> R.string.profile_issue_rate_limit
        ProfileIssueCode.BURST_LIMIT -> R.string.profile_issue_burst_limit
        ProfileIssueCode.ACL_ADDRESS -> R.string.profile_issue_acl_address
        ProfileIssueCode.ICMP_PORTS -> R.string.profile_issue_icmp_ports
    }
