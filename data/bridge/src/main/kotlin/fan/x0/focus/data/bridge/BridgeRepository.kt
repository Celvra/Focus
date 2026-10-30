package fan.x0.focus.data.bridge

import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

@Single
class BridgeRepository(
    private val client: BridgeClient,
    private val terminals: BridgeTerminalClient,
    private val store: BridgeStore,
    private val discovery: BridgeDiscovery,
) {
    val endpoints: Flow<List<BridgeEndpoint>> = store.endpoints
    val activeEndpointId: Flow<String?> = store.activeEndpointId

    suspend fun endpointsOnce(): List<BridgeEndpoint> = store.endpointsOnce()

    suspend fun activeEndpoint(): BridgeEndpoint? = store.activeEndpoint()

    suspend fun token(endpoint: BridgeEndpoint): String? = store.token(endpoint.id)

    suspend fun health(host: String, port: Int): BridgeHealth = client.health(host, port)

    suspend fun scanLan(): List<BridgeDiscoveredHost> = discovery.scan()

    suspend fun pair(host: String, port: Int, code: String, deviceName: String): BridgeEndpoint {
        val health = client.health(host, port)
        val result = client.pair(host, port, code, deviceName)
        val endpoint = BridgeEndpoint(
            id = result.deviceId,
            name = health.name.ifBlank { host },
            host = host,
            port = port,
            os = health.os,
            arch = health.arch,
            version = health.version,
        )
        store.saveEndpoint(endpoint, result.token)
        return endpoint
    }

    suspend fun activeToken(): String? {
        val endpoint = store.activeEndpoint() ?: return null
        return store.token(endpoint.id)
    }

    suspend fun lastHost(): String? = store.lastHost()

    suspend fun lastPort(): Int? = store.lastPort()

    suspend fun forget(endpoint: BridgeEndpoint) {
        runCatching { revokeSelf(endpoint) }
        store.remove(endpoint.id)
    }

    suspend fun select(endpoint: BridgeEndpoint) {
        store.setActive(endpoint.id)
    }

    suspend fun workspace(endpoint: BridgeEndpoint): List<String> {
        val token = requireToken(endpoint)
        return client.workspace(endpoint, token).roots
    }

    suspend fun setWorkspace(endpoint: BridgeEndpoint, roots: List<String>): List<String> {
        val token = requireToken(endpoint)
        val result = client.setWorkspace(endpoint, token, roots).roots
        store.setWorkspaceRoots(endpoint.id, result)
        return result
    }

    suspend fun sessions(endpoint: BridgeEndpoint): List<BridgeSessionInfo> {
        val token = requireToken(endpoint)
        return client.sessions(endpoint, token)
    }

    suspend fun openSession(
        endpoint: BridgeEndpoint,
        cwd: String? = null,
        shell: String? = null,
        cols: Int = 100,
        rows: Int = 30,
    ): BridgeSessionInfo {
        val token = requireToken(endpoint)
        return client.openSession(
            endpoint,
            token,
            BridgeSessionOpenRequest(shell = shell, cwd = cwd, cols = cols, rows = rows),
        )
    }

    suspend fun closeSession(endpoint: BridgeEndpoint, sessionId: String) {
        val token = requireToken(endpoint)
        client.closeSession(endpoint, token, sessionId)
    }

    suspend fun sendSessionInput(
        endpoint: BridgeEndpoint,
        sessionId: String,
        data: String,
    ) {
        val token = requireToken(endpoint)
        client.sessionInput(endpoint, token, sessionId, BridgeSessionWriteRequest(data = data))
    }

    suspend fun signalSession(endpoint: BridgeEndpoint, sessionId: String, signal: String) {
        val token = requireToken(endpoint)
        client.sessionInput(endpoint, token, sessionId, BridgeSessionWriteRequest(signal = signal))
    }

    suspend fun sessionTail(endpoint: BridgeEndpoint, sessionId: String): String {
        val token = requireToken(endpoint)
        return client.sessionTail(endpoint, token, sessionId)
    }

    fun attach(endpoint: BridgeEndpoint, token: String, sessionId: String, cols: Int, rows: Int): BridgeTerminal =
        terminals.attach(endpoint, token, sessionId, cols, rows)

    suspend fun read(endpoint: BridgeEndpoint, path: String, offset: Int? = null, limit: Int? = null): BridgeReadResult {
        val token = requireToken(endpoint)
        return client.read(endpoint, token, BridgeReadRequest(path, offset, limit))
    }

    suspend fun write(endpoint: BridgeEndpoint, path: String, content: String): BridgeOkResult {
        val token = requireToken(endpoint)
        return client.write(endpoint, token, BridgeWriteRequest(path, content))
    }

    suspend fun edit(
        endpoint: BridgeEndpoint,
        path: String,
        old: String,
        new: String,
        replaceAll: Boolean = false,
    ): BridgeOkResult {
        val token = requireToken(endpoint)
        return client.edit(endpoint, token, BridgeEditRequest(path, old, new, replaceAll))
    }

    suspend fun list(endpoint: BridgeEndpoint, path: String, depth: Int? = null): List<BridgeListEntry> {
        val token = requireToken(endpoint)
        return client.list(endpoint, token, BridgeListRequest(path, depth)).entries
    }

    suspend fun glob(endpoint: BridgeEndpoint, pattern: String, root: String? = null): BridgeGlobResult {
        val token = requireToken(endpoint)
        return client.glob(endpoint, token, BridgeGlobRequest(pattern, root))
    }

    suspend fun grep(
        endpoint: BridgeEndpoint,
        pattern: String,
        root: String? = null,
        include: String? = null,
        exclude: String? = null,
        literal: Boolean = false,
        caseSensitive: Boolean = false,
        context: Int? = null,
        limit: Int? = null,
    ): BridgeGrepResult {
        val token = requireToken(endpoint)
        return client.grep(
            endpoint,
            token,
            BridgeGrepRequest(
                pattern = pattern,
                root = root,
                include = include,
                exclude = exclude,
                literal = literal,
                caseSensitive = caseSensitive,
                context = context,
                limit = limit,
            ),
        )
    }

    suspend fun backgroundStart(
        endpoint: BridgeEndpoint,
        command: String,
        cwd: String? = null,
        timeoutSec: Long? = null,
    ): BridgeBgJob {
        val token = requireToken(endpoint)
        return client.bgStart(
            endpoint,
            token,
            BridgeBgStartRequest(command = command, cwd = cwd, timeoutSec = timeoutSec),
        )
    }

    suspend fun backgroundLogs(
        endpoint: BridgeEndpoint,
        id: String,
        after: Long = 0,
        follow: Boolean = true,
        onFrame: suspend (BridgeBgLogsResult) -> Unit,
    ) {
        val token = requireToken(endpoint)
        if (follow) {
            client.bgFollow(endpoint, token, id, after, onFrame)
        } else {
            onFrame(client.bgLogs(endpoint, token, id, after))
        }
    }

    suspend fun backgroundList(endpoint: BridgeEndpoint): List<BridgeBgJob> {
        val token = requireToken(endpoint)
        return client.bgList(endpoint, token)
    }

    suspend fun backgroundKill(endpoint: BridgeEndpoint, id: String): Boolean {
        val token = requireToken(endpoint)
        return client.bgKill(endpoint, token, id).killed
    }

    suspend fun backgroundClear(endpoint: BridgeEndpoint, id: String? = null) {
        val token = requireToken(endpoint)
        client.bgClear(endpoint, token, id)
    }

    suspend fun mkdir(endpoint: BridgeEndpoint, path: String): BridgeOkResult {
        val token = requireToken(endpoint)
        return client.mkdir(endpoint, token, path)
    }

    suspend fun move(endpoint: BridgeEndpoint, from: String, to: String): BridgeOkResult {
        val token = requireToken(endpoint)
        return client.move(endpoint, token, from, to)
    }

    suspend fun remove(endpoint: BridgeEndpoint, path: String): BridgeOkResult {
        val token = requireToken(endpoint)
        return client.remove(endpoint, token, path)
    }

    suspend fun exec(
        endpoint: BridgeEndpoint,
        request: BridgeExecRequest,
        onEvent: suspend (BridgeExecEvent) -> Unit,
    ) {
        val token = requireToken(endpoint)
        client.exec(endpoint, token, request, onEvent)
    }

    private suspend fun revokeSelf(endpoint: BridgeEndpoint) {
        val token = store.token(endpoint.id) ?: return
        client.revoke(endpoint, token, endpoint.id)
    }

    private suspend fun requireToken(endpoint: BridgeEndpoint): String =
        store.token(endpoint.id) ?: error("device is not paired: ${endpoint.name}")
}
