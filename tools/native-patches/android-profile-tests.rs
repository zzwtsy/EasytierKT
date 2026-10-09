use easytier::common::config::{
    process_secure_mode_cfg, ConfigLoader, PeerConfig, TomlConfigLoader,
};
use easytier::proto::common::SecureModeConfig;
use easytier::{
    common::{
        acl_processor::{AclProcessor, PacketInfo},
        global_ctx::GlobalCtx,
    },
    peers::{
        create_packet_recv_chan,
        peer_manager::{PeerManager, RouteAlgoType},
    },
    proto::acl::{Action, ChainType, Protocol},
    tunnel::ring::create_ring_tunnel_pair,
};
use std::{sync::Arc, time::Duration};

// 显式 /32 保留主机路由，静态 IPv6 不改变 IPv4 的掩码；裸 IPv4 仍使用内核默认 /24。
#[test]
fn preserves_explicit_host_prefix_and_ipv6() {
    let config =
        TomlConfigLoader::new_from_str("ipv4 = '10.0.0.2/32'\nipv6 = 'fd00::2/64'").unwrap();
    assert_eq!(config.get_ipv4().unwrap().network_length(), 32);
    assert_eq!(config.get_ipv6().unwrap().network_length(), 64);
    let bare = TomlConfigLoader::new_from_str("ipv4 = '10.0.0.2'").unwrap();
    assert_eq!(bare.get_ipv4().unwrap().network_length(), 24);
}

// 缺少共享密钥的安全身份是凭据身份，显式空共享密钥保持摘要；派生身份可重复用于保存和重连。
#[test]
fn preserves_credential_identity_and_derives_keys() {
    let credential = TomlConfigLoader::new_from_str(
        "[network_identity]\nnetwork_name = 'test'\n[secure_mode]\nenabled = true",
    )
    .unwrap();
    assert!(credential.get_network_identity().network_secret.is_none());
    assert!(credential
        .get_network_identity()
        .network_secret_digest
        .is_none());
    let secure = credential.get_secure_mode().unwrap();
    assert_eq!(process_secure_mode_cfg(secure.clone()).unwrap(), secure);
    let shared = TomlConfigLoader::new_from_str("[network_identity]\nnetwork_name = 'test'\nnetwork_secret = ''\n[secure_mode]\nenabled = true").unwrap();
    assert_eq!(
        shared.get_network_identity().network_secret.as_deref(),
        Some("")
    );
    assert!(shared
        .get_network_identity()
        .network_secret_digest
        .is_some());
}

// 启动直接加载 TOML 时也执行密钥派生；公私钥不匹配与 TOML 语法错误不能泄露输入。
#[test]
fn rejects_invalid_keys_without_exposing_input() {
    let identity = process_secure_mode_cfg(SecureModeConfig {
        enabled: true,
        ..Default::default()
    })
    .unwrap();
    let other = process_secure_mode_cfg(SecureModeConfig {
        enabled: true,
        ..Default::default()
    })
    .unwrap();
    let config = format!(
        "[secure_mode]\nenabled = true\nlocal_private_key = '{}'\nlocal_public_key = '{}'",
        identity.local_private_key.unwrap(),
        other.local_public_key.unwrap()
    );
    let error = TomlConfigLoader::new_from_str(&config)
        .err()
        .unwrap()
        .to_string();
    assert_eq!(error, "invalid secure identity");
    let error = TomlConfigLoader::new_from_str(
        "[secure_mode]\nlocal_private_key = 'secret-marker'\nnot-valid-toml",
    )
    .err()
    .unwrap();
    assert!(!format!("{error:?}").contains("secret-marker"));
}

// 实际内核读取传输开关、压缩枚举、空手动路由、端口转发和嵌套 ACL，避免仅验证 TOML 语法。
#[test]
fn reads_flags_forwarding_and_acl_semantics() {
    let config = TomlConfigLoader::new_from_str(
        r#"
routes = []
exit_nodes = ['10.0.0.3']
[[port_forward]]
bind_addr = '127.0.0.1:8080'
dst_addr = '10.0.0.3:80'
proto = 'tcp'
[flags]
mtu = 1300
multi_thread_count = 4
data_compress_algo = 2
disable_kcp_input = true
disable_quic_input = true
disable_relay_data = true
disable_tcp_hole_punching = true
instance_recv_bps_limit = 4096
[acl.acl_v1.group]
members = ['office']
[[acl.acl_v1.group.declares]]
group_name = 'office'
group_secret = 'synthetic-test-only'
[[acl.acl_v1.chains]]
name = 'in'
chain_type = 1
enabled = true
default_action = 2
[[acl.acl_v1.chains.rules]]
name = 'web'
priority = 100
enabled = true
protocol = 1
action = 1
ports = ['80', '443']
source_ips = ['10.0.0.0/24']
rate_limit = 10
burst_limit = 20
stateful = true
"#,
    )
    .unwrap();
    let flags = config.get_flags();
    assert_eq!(flags.mtu, 1300);
    assert_eq!(flags.multi_thread_count, 4);
    assert_eq!(flags.data_compress_algo, 2);
    assert!(
        flags.disable_kcp_input
            && flags.disable_quic_input
            && flags.disable_relay_data
            && flags.disable_tcp_hole_punching
    );
    assert_eq!(flags.instance_recv_bps_limit, 4096);
    assert_eq!(config.get_routes(), Some(vec![]));
    assert_eq!(config.get_exit_nodes().len(), 1);
    assert_eq!(config.get_port_forwards()[0].dst_addr.port(), 80);
    let acl = config.get_acl().unwrap().acl_v1.unwrap();
    assert_eq!(acl.group.unwrap().declares[0].group_name, "office");
    assert_eq!(acl.chains[0].default_action, 2);
    assert_eq!(acl.chains[0].rules[0].ports, vec!["80", "443"]);
    assert!(acl.chains[0].rules[0].stateful);
}

// 编码后的双栈 ACL CIDR 进入真实匹配器时，允许规则只匹配源和目标网段内的地址，不放行其他网段。
#[tokio::test]
async fn normalized_acl_conditions_restrict_actual_packets() {
    let config = TomlConfigLoader::new_from_str(
        r#"
[acl.acl_v1.group]
members = []
[[acl.acl_v1.chains]]
name = 'in'
chain_type = 1
enabled = true
default_action = 2
[[acl.acl_v1.chains.rules]]
name = 'restricted'
priority = 100
enabled = true
protocol = 5
action = 1
source_ips = ['10.0.0.0/24', 'fd00:0:0:0:0:0:0:0/64']
destination_ips = ['192.168.5.0/24', 'fd01:0:0:0:0:0:0:0/64']
"#,
    )
    .unwrap();
    let processor = AclProcessor::new(config.get_acl().unwrap());
    for (source, destination, expected) in [
        ("10.0.0.3", "192.168.5.7", Action::Allow),
        ("10.1.0.3", "192.168.5.7", Action::Drop),
        ("10.0.0.3", "192.168.6.7", Action::Drop),
        ("fd00::3", "fd01::7", Action::Allow),
        ("fd02::3", "fd01::7", Action::Drop),
        ("fd00::3", "fd02::7", Action::Drop),
    ] {
        let packet = PacketInfo {
            src_ip: source.parse().unwrap(),
            dst_ip: destination.parse().unwrap(),
            src_port: Some(12345),
            dst_port: Some(443),
            protocol: Protocol::Tcp,
            packet_size: 100,
            src_groups: Arc::new(vec![]),
            dst_groups: Arc::new(vec![]),
        };
        assert_eq!(
            processor.process_packet(&packet, ChainType::Inbound).action,
            expected,
            "{source} -> {destination}"
        );
    }
}

async fn routing_peer(ipv4: &str, ipv6: &str, exit_node: Option<&str>) -> Arc<PeerManager> {
    let config =
        TomlConfigLoader::new_from_str(&format!("ipv4 = '{ipv4}'\nipv6 = '{ipv6}'")).unwrap();
    config.set_exit_nodes(
        exit_node
            .into_iter()
            .map(|ip| ip.parse().unwrap())
            .collect(),
    );
    running_peer(config).await
}

async fn running_peer(config: TomlConfigLoader) -> Arc<PeerManager> {
    let (sender, _receiver) = create_packet_recv_chan();
    let peer = Arc::new(PeerManager::new(
        RouteAlgoType::Ospf,
        Arc::new(GlobalCtx::new(config)),
        sender,
    ));
    peer.run().await.unwrap();
    peer
}

// 双方共享同一密钥并启用安全握手时，无绑定和正确绑定均可连接，错误绑定必须拒绝，不能被密钥验证短路。
#[tokio::test]
async fn shared_secret_cannot_bypass_mismatched_peer_pin() {
    for pin_mode in ["none", "correct", "wrong"] {
        let secure_config = "[network_identity]\nnetwork_name = 'pin-regression'\nnetwork_secret = 'synthetic-pin-test'\n[secure_mode]\nenabled = true";
        let client = running_peer(TomlConfigLoader::new_from_str(secure_config).unwrap()).await;
        let server = running_peer(TomlConfigLoader::new_from_str(secure_config).unwrap()).await;
        let server_key = server
            .get_global_ctx()
            .config
            .get_secure_mode()
            .unwrap()
            .local_public_key
            .unwrap();
        let (client_tunnel, server_tunnel) = create_ring_tunnel_pair();
        if pin_mode != "none" {
            let key = if pin_mode == "correct" {
                server_key
            } else {
                process_secure_mode_cfg(SecureModeConfig {
                    enabled: true,
                    ..Default::default()
                })
                .unwrap()
                .local_public_key
                .unwrap()
            };
            let uri = client_tunnel
                .info()
                .unwrap()
                .remote_addr
                .unwrap()
                .url
                .parse()
                .unwrap();
            client.get_global_ctx().config.set_peers(vec![PeerConfig {
                uri,
                peer_public_key: Some(key),
            }]);
        }
        let (client_result, server_result) = tokio::time::timeout(Duration::from_secs(10), async {
            tokio::join!(
                client.add_client_tunnel(client_tunnel, false),
                server.add_tunnel_as_server(server_tunnel, true),
            )
        })
        .await
        .unwrap();
        if pin_mode == "wrong" {
            assert!(client_result
                .unwrap_err()
                .to_string()
                .contains("pinned remote static pubkey mismatch"));
        } else {
            assert!(client_result.is_ok(), "{pin_mode}: {client_result:?}");
            assert!(server_result.is_ok(), "{pin_mode}: {server_result:?}");
        }
    }
}

async fn connect_routing_peers(client: &Arc<PeerManager>, server: &Arc<PeerManager>) {
    let (client_tunnel, server_tunnel) = create_ring_tunnel_pair();
    let (client_result, server_result) = tokio::join!(
        client.add_client_tunnel(client_tunnel, false),
        server.add_tunnel_as_server(server_tunnel, true),
    );
    client_result.unwrap();
    server_result.unwrap();
}

// 三个内存隧道节点使用 /32 和 /128 时，单播只选择目标节点，公网 IPv4 只选择指定出口并标记出口转发。
#[tokio::test]
async fn host_prefixes_preserve_unicast_and_exit_selection() {
    let client = routing_peer("10.0.0.2/32", "fd00::2/128", Some("10.0.0.3")).await;
    let exit = routing_peer("10.0.0.3/32", "fd00::3/128", None).await;
    let other = routing_peer("10.0.0.4/32", "fd00::4/128", None).await;
    connect_routing_peers(&client, &exit).await;
    connect_routing_peers(&client, &other).await;
    let route = client.get_route();
    tokio::time::timeout(Duration::from_secs(15), async {
        while route
            .get_peer_id_by_ipv4(&"10.0.0.3".parse().unwrap())
            .await
            != Some(exit.my_peer_id())
            || route
                .get_peer_id_by_ipv4(&"10.0.0.4".parse().unwrap())
                .await
                != Some(other.my_peer_id())
            || route.get_peer_id_by_ipv6(&"fd00::4".parse().unwrap()).await
                != Some(other.my_peer_id())
        {
            tokio::time::sleep(Duration::from_millis(50)).await;
        }
    })
    .await
    .unwrap();
    assert_eq!(
        client
            .get_msg_dst_peer_ipv4(&"10.0.0.4".parse().unwrap())
            .await,
        (vec![other.my_peer_id()], false)
    );
    assert_eq!(
        client
            .get_msg_dst_peer_ipv6(&"fd00::4".parse().unwrap())
            .await,
        (vec![other.my_peer_id()], false)
    );
    assert_eq!(
        client
            .get_msg_dst_peer_ipv4(&"8.8.8.8".parse().unwrap())
            .await,
        (vec![exit.my_peer_id()], true)
    );
}

// 普通 /24 保留本子网广播、有限广播及组播；异网段 .255、/31 目标和 IPv6 子网末地址不能被当成广播。
#[tokio::test]
async fn broadcast_is_limited_to_local_ipv4_subnet_and_multicast() {
    let client = routing_peer("10.0.0.2/24", "fd00::2/64", None).await;
    let other = routing_peer("10.0.0.3/24", "fd00::3/64", None).await;
    connect_routing_peers(&client, &other).await;
    tokio::time::timeout(Duration::from_secs(15), async {
        while client
            .get_route()
            .get_peer_id_by_ipv4(&"10.0.0.3".parse().unwrap())
            .await
            != Some(other.my_peer_id())
        {
            tokio::time::sleep(Duration::from_millis(50)).await;
        }
    })
    .await
    .unwrap();
    for address in ["10.0.0.255", "255.255.255.255", "224.0.0.1"] {
        let (peers, is_exit) = client
            .get_msg_dst_peer_ipv4(&address.parse().unwrap())
            .await;
        assert_eq!(peers, vec![other.my_peer_id()], "{address}");
        assert!(!is_exit);
    }
    assert!(client
        .get_msg_dst_peer_ipv4(&"192.168.5.255".parse().unwrap())
        .await
        .0
        .is_empty());
    assert!(client
        .get_msg_dst_peer_ipv6(&"fd00::ffff:ffff:ffff:ffff".parse().unwrap())
        .await
        .0
        .is_empty());
    assert!(client
        .get_msg_dst_peer_ipv6(&"ff02::1".parse().unwrap())
        .await
        .0
        .contains(&other.my_peer_id()));
    client
        .get_global_ctx()
        .set_ipv4(Some("10.0.0.2/31".parse().unwrap()));
    assert_eq!(
        client
            .get_msg_dst_peer_ipv4(&"10.0.0.3".parse().unwrap())
            .await,
        (vec![other.my_peer_id()], false)
    );
    assert!(client
        .get_msg_dst_peer_ipv4(&"192.168.5.7".parse().unwrap())
        .await
        .0
        .is_empty());
}

// JVM 真实编码器产生三链和重复规则字段后，上游解析器必须保留各表作用域、列表、转义及认证语义。
#[test]
#[ignore = "需要先运行 ProfileTomlContractTest，再使用 --android-fixtures"]
fn parses_android_encoder_fixtures_without_losing_nested_tables() {
    let directory = std::path::PathBuf::from(std::env::var("ANDROID_TOML_FIXTURE_DIR").unwrap());
    let load = |name: &str| {
        TomlConfigLoader::new_from_str(
            &std::fs::read_to_string(directory.join(format!("{name}.toml"))).unwrap(),
        )
        .unwrap()
    };
    let full = load("full");
    assert_eq!(full.get_inst_name(), "easytier-android");
    assert_eq!(full.get_network_identity().network_secret.as_deref(), Some("shared\"\\key"));
    assert_eq!(full.get_ipv4().unwrap().network_length(), 32);
    assert_eq!(full.get_ipv6().unwrap().to_string(), "fd00::2/64");
    assert_eq!(full.get_routes(), Some(vec![]));
    assert_eq!(full.get_listeners(), Some(vec![]));
    assert_eq!(full.get_peers().len(), 2);
    assert_eq!(full.get_port_forwards().len(), 2);
    assert_eq!(full.get_port_forwards()[0].dst_addr.port(), 80);
    assert_eq!(full.get_port_forwards()[1].dst_addr.port(), 53);
    let flags = full.get_flags();
    assert!(!flags.multi_thread && !flags.enable_encryption);
    assert_eq!(flags.data_compress_algo, 2);
    assert_eq!(flags.instance_recv_bps_limit, 4096);
    let acl = full.get_acl().unwrap().acl_v1.unwrap();
    assert_eq!(acl.group.unwrap().declares[0].group_name, "office");
    assert_eq!(acl.chains.len(), 3);
    for (index, chain) in acl.chains.iter().enumerate() {
        assert_eq!(chain.chain_type, index as i32 + 1);
        assert_eq!(chain.default_action, 2);
        assert_eq!(chain.rules.len(), 2);
        assert_eq!(chain.rules[0].name, "second");
        assert_eq!(chain.rules[0].protocol, 2);
        assert_eq!(chain.rules[1].name, "first");
        assert_eq!(chain.rules[1].protocol, 1);
        assert_eq!(chain.rules[1].description, "line1\nline2\t\"\\");
        assert_eq!(chain.rules[1].ports, vec!["80", "443"]);
        assert_eq!(chain.rules[1].source_ips, vec!["10.0.0.0/24", "fd00::/64"]);
        assert_eq!(chain.rules[1].rate_limit, 10);
        assert_eq!(chain.rules[1].burst_limit, 20);
        assert!(chain.rules[1].stateful);
    }
    let automatic = load("automatic");
    assert_eq!(automatic.get_routes(), None);
    assert!(automatic.get_peers().is_empty() && automatic.get_port_forwards().is_empty());
    assert!(automatic.get_acl().is_none());
    let credential = load("credential");
    assert!(credential.get_network_identity().network_secret.is_none());
    assert!(credential.get_secure_mode().unwrap().enabled);
}
