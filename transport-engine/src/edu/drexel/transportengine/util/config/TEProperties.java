package edu.drexel.transportengine.util.config;

public enum TEProperties {
    TE_COMPONENTS("te.components", "clientmgr,protocolmgr,persistencemgr,contentstore",
            "[String(s)] The components to load."),
    TE_EMULATED("te.is_emulated", "true", "[Boolean] If the TE is being run in emulation."),


    CLIENTMGR_LISTEN_IFACE("clientmgr.listen.iface", "lo", "[String] The network interface on which to listen for " +
            "clients."),
    CLIENTMGR_LISTEN_PORT("clientmgr.listen.port", "1998", "[Integer] The port on which to listen for clients."),
    CLIENTMGR_BUFFER_SIZE("clientmgr.buffer_size", "500", "[Integer] The number of messages UIDs to maintain for " +
            "each client for duplicate detection."),

    TRANSPORT_IPS("transport.ips", "224.1.128.1-224.1.255.254", "[IP Range(s)] Range(s) of IPs into which " +
            "destination addresses will be hashed."),

    CONTENTSTORE_PATH("contentstore.path", "/tmp", "[String] Path for storing sqlite databases."),
    CONTENTSTORE_RESET("contentstore.reset_database", "false", "[Boolean] Reset the database on startup."),
    CONTENTSTORE_CACHE_LIMIT("contentstore.cache_limit", "256", "[Integer] Maximum number of messages to cache in " +
            "memory."),

    PROTOCOL_UDP_PORT("protocol.udp.port", "3000", "[Integer] Port to use for UDP traffic."),
    PROTOCOL_UDP_TTL("protocol.udp.ttl", "32", "[Integer] TTL for UDP traffic."),

    PROTOCOL_NORM_PORT("protocol.norm.port", "3004", "[Integer] Port to use for NORM traffic."),
    PROTOCOL_NORM_BUFFER_SPACE("protocol.norm.buffer_space", "1048576", "[Integer] Buffer space for NORM sender."),
    PROTOCOL_NORM_SEGMENT_SIZE("protocol.norm.segment_size", "1400", "[Integer] Segment size for NORM sender."),
    PROTOCOL_NORM_BLOCK_SIZE("protocol.norm.block_size", "64", "[Integer] Block size for NORM sender."),
    PROTOCOL_NORM_NUM_PARITY("protocol.norm.num_parity", "16", "[Integer] Number of parity symbols for NORM sender."),
    PROTOCOL_NORM_RECV_BUFFER_SPACE("protocol.norm.recv_buffer_space", "1048576",
            "[Integer] Receive buffer space for NORM session."),

    ALGORITHM_MANIFEST_MANIFEST_LENGTH("algorithm.manifest.manifest_length", "25",
            "[Integer] Maximum number of messages in a manifest."),
    ALGORITHM_MANIFEST_SLEEP_MS("algorithm.manifest.sleep_ms", "1000", "[Integer] Time to sleep between each " +
            "broadcast in ms."),
    ALGORITHM_MANIFEST_DESTINATION("algorithm.manifest.destination", "manifest-channel",
            "[String] Destination of manifest traffic."),
    ALGORITHM_MANIFEST_RELIABLE("algorithm.manifest.reliable", "false", "[Boolean] If reliable transport should be " +
            "used."),

    ALGORITHM_TRICKLE_K("algorithm.trickle.k", "2", "[Integer] k-value for Trickle."),
    ALGORITHM_TRICKLE_TAUL("algorithm.trickle.taul", "1", "[Integer] Minimum Tau for Trickle."),
    ALGORITHM_TRICKLE_TAUH("algorithm.trickle.tauh", "10", "[Integer] Maximum Tau for Trickle."),
    ALGORITHM_TRICKLE_ADVERT_MS("algorithm.trickle.advert_ms", "5000", "[Integer] Advertisement interval in ms."),
    ALGORITHM_TRICKLE_MANIFEST_LENGTH("algorithm.trickle.manifest_length", "25",
            "[Integer] Maximum number of messages in a manifest."),
    ALGORITHM_TRICKLE_RELIABLE("algorithm.trickle.reliable", "false", "[Boolean] If reliable transport should be " +
            "used."),
    ALGORITHM_TRICKLE_DESTINATION("algorithm.trickle.destination", "trickle-channel",
            "[String] Destination of trickle traffic."),

    DDM_NUM_NODES("ddm.num_nodes", "10", "[Integer] Total number of nodes using DDM."),
    DDM_DENSE_THRESHOLD("ddm.dense_threshold", "2", "[Integer] Density threshold."),
    DDM_REFRESH_MS("ddm.refresh_ms", "500", "[Integer] Refresh interval in ms."),
    DDM_FRESH_PERIOD_MS("ddm.fresh_period_ms", "500", "[Integer] Maximum period to keep DDM observations in ms."),
    DDM_LAMBDA("ddm.lambda", "25", "[Integer] Weight of remote observations."),
    DDM_SECONDS_PER_UNIT("ddm.seconds_per_unit", "1000", "[Integer] Seconds in a DDM 'unit.'"),
    DDM_BYTES_PER_UNIT_THRESHOLD("ddm.bytes_threshold", "5000", "[Integer] Byte count threshold for high/low " +
            "bandwidth.");

    private String key;
    private String defaultValue;
    private String comment;

    TEProperties(String key, String defaultValue, String comment) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.comment = comment;
    }

    public String getKey() {
        return key;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getComment() {
        return comment;
    }

    public static TEProperties fromKey(String key) {
        for (TEProperties p : TEProperties.values()) {
            if (p.getKey().equals(key)) {
                return p;
            }
        }
        return null;
    }
}
