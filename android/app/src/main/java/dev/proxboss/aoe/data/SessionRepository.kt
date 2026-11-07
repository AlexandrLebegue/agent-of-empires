package dev.proxboss.aoe.data

import dev.proxboss.aoe.model.Approval
import dev.proxboss.aoe.model.SearchHit
import dev.proxboss.aoe.model.Session
import dev.proxboss.aoe.net.AoeClient
import dev.proxboss.aoe.net.ApiResult
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

data class PromptDispatchResult(val queued: Boolean, val disposition: String?)

class SessionRepository(
    private val client: AoeClient,
    private val store: SessionStore,
    private val settings: SettingsRepository,
) {
    fun session(id: String): Session? = store.session(id)

    suspend fun refresh(): ApiResult<Unit> {
        return when (val result = client.get("/api/sessions?state=all")) {
            is ApiResult.Ok -> runCatching {
                val envelope = SessionMapper.parseEnvelope(result.value)
                store.replaceAll(envelope.sessions.filterNot { it.isTrashed })
            }.fold(
                onSuccess = { ApiResult.Ok(Unit) },
                onFailure = { ApiResult.Err("Réponse du serveur illisible") },
            )
            is ApiResult.Err -> result
        }
    }

    suspend fun search(query: String): ApiResult<List<SearchHit>> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return client.get("/api/sessions/search?q=$encoded").map { SessionMapper.parseSearch(it) }
    }

    suspend fun setPinned(id: String, pinned: Boolean): ApiResult<Unit> =
        mutation(id, "/api/sessions/$id/pin", JSONObject().put("pinned", pinned).toString())

    suspend fun setFavorited(id: String, favorited: Boolean): ApiResult<Unit> =
        mutation(id, "/api/sessions/$id/favorite", JSONObject().put("favorited", favorited).toString())

    suspend fun setUnread(id: String, unread: Boolean): ApiResult<Unit> =
        mutation(id, "/api/sessions/$id/unread", JSONObject().put("unread", unread).toString())

    suspend fun setMuted(id: String, muted: Boolean): ApiResult<Unit> {
        val value: Any = if (muted) false else JSONObject.NULL
        val body = JSONObject()
            .put("notify_on_waiting", value)
            .put("notify_on_idle", value)
            .put("notify_on_error", value)
            .toString()
        return mutation(id, "/api/sessions/$id/notifications", body)
    }

    suspend fun stop(id: String): ApiResult<Unit> = simplePost("/api/sessions/$id/stop")
    suspend fun start(id: String): ApiResult<Unit> = simplePost("/api/sessions/$id/start")
    suspend fun restart(id: String): ApiResult<Unit> = simplePost("/api/sessions/$id/restart")
    suspend fun cancelTurn(id: String): ApiResult<Unit> = simplePost("/api/sessions/$id/acp/cancel")

    suspend fun sendPrompt(
        sessionId: String,
        text: String,
        forceTerminal: Boolean = false,
    ): ApiResult<PromptDispatchResult> {
        val session = store.session(sessionId)
        return if (!forceTerminal && session?.isStructured == true) {
            val body = JSONObject()
                .put("text", text)
                .put("prompt_id", UUID.randomUUID().toString())
                .toString()
            when (val result = client.post("/api/sessions/$sessionId/acp/prompt", body)) {
                is ApiResult.Ok -> {
                    val parsed = runCatching { JSONObject(result.value) }.getOrNull()
                    val disposition = parsed?.optString("disposition").orEmpty().ifBlank { null }
                    ApiResult.Ok(PromptDispatchResult(queued = disposition == "queued", disposition = disposition))
                }
                is ApiResult.Err -> result
            }
        } else {
            val body = JSONObject().put("message", text).toString()
            when (val result = client.post("/api/sessions/$sessionId/send", body)) {
                is ApiResult.Ok -> ApiResult.Ok(PromptDispatchResult(queued = false, disposition = "sent"))
                is ApiResult.Err -> result
            }
        }
    }

    suspend fun resolveApproval(
        sessionId: String,
        approval: Approval,
        decision: String,
        optionId: String? = null,
    ): ApiResult<Unit> {
        val body = JSONObject().put("decision", decision)
        if (optionId != null) body.put("option_id", optionId)
        return when (
            val result = client.post(
                "/api/sessions/$sessionId/acp/approvals/${encode(approval.nonce)}",
                body.toString(),
            )
        ) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }
    }

    suspend fun setConfigOption(sessionId: String, configId: String, value: String): ApiResult<Unit> {
        val body = JSONObject().put("config_id", configId).put("value", value).toString()
        return when (val result = client.post("/api/sessions/$sessionId/acp/config-option", body)) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }
    }

    suspend fun diffFiles(sessionId: String): ApiResult<DiffFilesResult> =
        client.get("/api/sessions/$sessionId/diff/files").map { DiffMapper.parseFiles(it) }

    suspend fun diffFile(sessionId: String, path: String, repoName: String?): ApiResult<DiffFileContent> {
        val query = buildString {
            append("?path=").append(encode(path))
            if (!repoName.isNullOrBlank()) append("&repo=").append(encode(repoName))
        }
        return client.get("/api/sessions/$sessionId/diff/file$query").map { DiffMapper.parseFile(it) }
    }

    suspend fun recordModel(sessionId: String, model: String) {
        if (model.isBlank()) return
        store.setModel(sessionId, model)
        runCatching { settings.putModel(sessionId, model) }
    }

    suspend fun testConnection(baseUrl: String, token: String): ApiResult<String> {
        val probe = AoeClient()
        probe.configure(baseUrl, token)
        return probe.get("/api/about").map { json ->
            val about = SessionMapper.parseAbout(json)
            val auth = when {
                about.authMode == "none" -> "sans auth"
                about.authRequired -> "auth ${about.authMode}"
                else -> "auth ${about.authMode}"
            }
            "AoE ${about.version} · $auth · profil ${about.profile.ifBlank { "?" }}"
        }
    }

    private suspend fun simplePost(path: String): ApiResult<Unit> =
        when (val result = client.post(path)) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }

    private suspend fun mutation(id: String, path: String, body: String): ApiResult<Unit> {
        return when (val result = client.patch(path, body)) {
            is ApiResult.Ok -> {
                runCatching { store.upsert(SessionMapper.parseSession(result.value)) }
                ApiResult.Ok(Unit)
            }
            is ApiResult.Err -> result
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
