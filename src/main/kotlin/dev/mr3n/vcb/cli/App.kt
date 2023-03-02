package dev.mr3n.vcb.cli

import com.akuleshov7.ktoml.Toml
import kotlinx.cli.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

private val serverListRegex = Regex("(,*((.+?)=(.+?:\\d+)))+?")

fun parseServers(servers: String): Map<String, String> {
    // 環境変数からサーバー一覧を取得
    return serverListRegex.findAll(servers)
        // 切り抜いた情報からServerInfoを作成
        .associate { it.groupValues.let {k->k[3] to k[4] } }
}

fun parseForcedHosts(forcedHosts: String): Map<String, List<String>> {
    return forcedHosts.split(",").map { it.split("=") }.associate { it[0] to it[1].split("+") }
}



fun main(args: Array<String>) {
    val parser = ArgParser("vcb")
    val argTypeExplicitBoolean = ArgType.Choice(listOf(false, true), { it.toBooleanStrictOrNull()?:true })

    val bind by parser.option(ArgType.String, "bind", "b", "どのポートでVelocityをバインドするかどうか。「0.0.0.0:25565」の形式。").default("0.0.0.0:25577")
    val motd by parser.option(ArgType.String, "motd", "m", "サーバーのMOTDの設定。以来のカラーコードとjsonの両方をサポートします。").default("&#09add3A Velocity Server")
    val snowMaxPlayers by parser.option(ArgType.Int, "show_max_players", "smp", "サーバーの最大参加人数。").default(500)
    val onlineMode by parser.option(argTypeExplicitBoolean, "online_mode", "om", "Mojangでプレイヤーを認証する必要があるかどうか").default(true)
    val preventClientProxyConnections by parser.option(argTypeExplicitBoolean, "prevent_client_proxy_connections", "pcpc", "クライアントのISP/ASが、Mojangの認証サーバからのものと異なる場合プレイヤーはKickされます。これは一部のVPNやプロキシ接続を切断するものですが保護としては脆弱です。").default(false)
    val playerInfoForwardingMode by parser.option(ArgType.String,"player_info_forwarding_mode", "pifm", "サーバーにIPアドレスなどを転送するかどうか。選択肢: `none` `legacy` `bungeeguard` `modern`").default("none")
    val forwardingSecret by parser.option(ArgType.String, "forwarding_secret", "fs", "modernまたはbungeeguardのIP転送を使用している場合、これでsecretを設定します。").default("")
    val announceForge by parser.option(argTypeExplicitBoolean, "announce_forge", "af", "もしあなたのネットワークが`一貫して一つのmodpackを動かしている`のであれば、代わりに ping-passthrough = \"mods\" を使って、サーバリストでよりきれいに表示することを検討してみてください。").default(false)
    val kickExistingPlayers by parser.option(argTypeExplicitBoolean, "kick_existing_players", "kep", "trueの場合、重複した接続を試みようとしたプレイヤー(既にサーバーに居る方)をKickします。").default(false)
    val pingPassthrough by parser.option(ArgType.String, "ping_passthrough", "pp", "サーバーリストのPingリクエストをバックエンドサーバーに渡すべきかどうか。`disabled` `mods` `description` `all`").default("DISABLED")
    val servers by parser.option(ArgType.String, "servers", "s", "登録するサーバー一覧を「名前1=ホスト:ポート,名前2=ホスト:ポート」の形式。").default("lobby=127.0.0.1:25566,factions=127.0.0.1:25577")
    val tryServers by parser.option(ArgType.String, "try", "t", "サーバーにログインしたとき、またはサーバーからキックされたときに、どのような順序で接続を試すべきか。lobby,survival,werewolf").default("lobby")
    val forcedHosts by parser.option(ArgType.String, "forced_hosts", "fh", "forced_hostsの設定。lobby.example.com=lobby1+lobby2,survival.example.com=survival1").default("lobby.example.com=lobby")
    val compressionThreshold by parser.option(ArgType.Int, "compression_threshold", "ht").default(256)
    val compressionLevel by parser.option(ArgType.Int, "compression_level", "cl", "どの程度の圧縮を行うか（0_9の間）。デフォルトは_1です。").default(6)
    val loginRateLimit by parser.option(ArgType.Int,"login_ratelimit","lr", "最後の接続からクライアントが接続できるまでの時間(ミリ秒単位").default(3000)
    val connectionTimeout by parser.option(ArgType.Int, "connection_timeout", "ct", "接続タイムアウトのカスタムタイムアウトをここで指定します。デフォルトは5000ミリ秒です。").default(5000)
    val readTimeout by parser.option(ArgType.Int, "read_timeout", "rt", "コネクションの読み取りタイムアウトをここで指定します。デフォルトは30000ミリ秒です。").default(30000)
    val haproxyProtocol by parser.option(argTypeExplicitBoolean,"haproxy_protocol", "hp", "HAProxyのProxyProtocolと互換性を持たせることができます。").default(false)
    val tcpFastOpen by parser.option(argTypeExplicitBoolean,"tcp_fast_open", "tfo", "VelocityでTCPファストオープンのサポートを有効にします。Linuxでのみ動作します。").default(false)
    val bungeePluginMessageChannel by parser.option(argTypeExplicitBoolean, "bungee_plugin_message", "bpm", "Velocity で BungeeCord プラグインのメッセージングチャネルをサポートするかどうか").default(true)
    val showPingRequests by parser.option(argTypeExplicitBoolean, "show_ping_requests", "spr", "クライアントからVelocityへの ping リクエストをすべてログに記録するかどうか").default(false)
    val failoverOnUnexpectedServerDisconnect by parser.option(argTypeExplicitBoolean, "failover_on_unexpected_server_disconnect", "fousd", "読み取りタイムアウトの場合を除き、ユーザーをフォールバックさせようとすることで、明示的な切断メッセージなしでユーザーが予期せずサーバーへの接続を失った状況を適切に処理するかどうか").default(true)
    val announceProxyCommands by parser.option(argTypeExplicitBoolean, "announce_proxy_commands", "apc", "プロキシ コマンドを 1.13 以降のクライアントに定義するかどうか").default(true)
    val logCommandExecutions by parser.option(argTypeExplicitBoolean, "log_command_executions", "lce", "すべてのコマンドの実行をログに記録するかどうか").default(false)
    val queryEnabled by parser.option(argTypeExplicitBoolean, "query_enabled", "qe", "GameSpy 4のクエリ応答への応答を有効にするかどうか。").default(true)
    val queryPort by parser.option(ArgType.Int, "query_port", "qp", "Queryが有効になっている場合、Queryプロトコルはどのポートでリッスンするか").default(25577)
    val queryMap by parser.option(ArgType.String, "query_map", "qm", "Queryサービスに送信するサーバー名").default("Velocity")
    val queryShowPlugins by parser.option(argTypeExplicitBoolean, "query_show_plugins", "qsp", "プラグインをQueryレスポンスにデフォルトで表示するかどうか").default(false)

    val output by parser.option(ArgType.String, "output", "o", "config.tomlの出力先を指定します。例: /tmp/velocity.toml").required()

    parser.parse(args)
    val configuration = VelocityConfiguration(
        configVersion = "2.5",
        bind = bind,
        motd = motd,
        showMaxPlayers = snowMaxPlayers,
        onlineMode = onlineMode,
        preventClientProxyConnections = preventClientProxyConnections,
        playerInfoForwardingMode = playerInfoForwardingMode,
        forwardingSecret = forwardingSecret,
        announceForge = announceForge,
        kickExistingPlayers = kickExistingPlayers,
        pingPassthrough = pingPassthrough,
        servers = parseServers(servers),
        tryServers = tryServers.split(","),
        forcedHosts = parseForcedHosts(forcedHosts),
        advanced = VelocityConfiguration.Advanced(
            compressionThreshold = compressionThreshold,
            compressionLevel = compressionLevel,
            loginRateLimit = loginRateLimit,
            connectionTimeout = connectionTimeout,
            readTimeout = readTimeout,
            haproxyProtocol = haproxyProtocol,
            tcpFastOpen = tcpFastOpen,
            bungeePluginMessageChannel = bungeePluginMessageChannel,
            showPingRequests = showPingRequests,
            failoverOnUnexpectedServerDisconnect = failoverOnUnexpectedServerDisconnect,
            announceProxyCommands = announceProxyCommands,
            logCommandExecutions = logCommandExecutions
        ),
        query = VelocityConfiguration.Query(
            enabled = queryEnabled,
            port = queryPort,
            map = queryMap,
            showPlugins = queryShowPlugins
        )
    )
    val result = Toml.encodeToString(configuration)
    val file = File(output)
    if(!file.exists()) { file.createNewFile() }
    file.writeText(result)
}

@Serializable
data class VelocityConfiguration(
    @SerialName("config-version")
    val configVersion: String,
    val bind: String,
    val motd: String,
    @SerialName("show-max-players")
    val showMaxPlayers: Int,
    @SerialName("online-mode")
    val onlineMode: Boolean,
    @SerialName("prevent-client-proxy-connections")
    val preventClientProxyConnections: Boolean,
    @SerialName("player-info-forwarding-mode")
    val playerInfoForwardingMode: String,
    @SerialName("forwarding-secret")
    val forwardingSecret: String,
    @SerialName("announce-forge")
    val announceForge: Boolean,
    @SerialName("kick-existing-players")
    val kickExistingPlayers: Boolean,
    @SerialName("ping-passthrough")
    val pingPassthrough: String,
    val servers: Map<String, String>,
    @SerialName("try")
    val tryServers: List<String>,
    @SerialName("forced-hosts")
    val forcedHosts: Map<String, List<String>>,
    val advanced: Advanced,
    val query: Query
) {
    @Serializable
    data class Advanced(
        @SerialName("compression-threshold")
        val compressionThreshold: Int,
        @SerialName("compression-level")
        val compressionLevel: Int,
        @SerialName("login-ratelimit")
        val loginRateLimit: Int,
        @SerialName("connection-timeout")
        val connectionTimeout: Int,
        @SerialName("read-timeout")
        val readTimeout: Int,
        @SerialName("haproxy-protocol")
        val haproxyProtocol: Boolean,
        @SerialName("tcp-fast-open")
        val tcpFastOpen: Boolean,
        @SerialName("bungee-plugin-message-channel")
        val bungeePluginMessageChannel: Boolean,
        @SerialName("show-ping-requests")
        val showPingRequests: Boolean,
        @SerialName("failover-on-unexpected-server-disconnect")
        val failoverOnUnexpectedServerDisconnect: Boolean,
        @SerialName("announce-proxy-commands")
        val announceProxyCommands: Boolean,
        @SerialName("log-command-executions")
        val logCommandExecutions: Boolean
    )

    @Serializable
    data class Query(
        val enabled: Boolean,
        val port: Int,
        val map: String,
        @SerialName("show-plugins")
        val showPlugins: Boolean
    )
}