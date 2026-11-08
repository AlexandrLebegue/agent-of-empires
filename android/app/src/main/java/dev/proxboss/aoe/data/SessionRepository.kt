package dev.proxboss.aoe.data

import dev.proxboss.aoe.model.AcpAgent
import dev.proxboss.aoe.model.AgentInfo
import dev.proxboss.aoe.model.Approval
import dev.proxboss.aoe.model.BranchInfo
import dev.proxboss.aoe.model.BrowseResult
import dev.proxboss.aoe.model.ConfigOption
import dev.proxboss.aoe.model.CreateSessionRequest
import dev.proxboss.aoe.model.CreateSessionResult
import dev.proxboss.aoe.model.CreationProgress
import dev.proxboss.aoe.model.CreationTrustReview
import dev.proxboss.aoe.model.GroupInfo
import dev.proxboss.aoe.model.RecentProject
import dev.proxboss.aoe.model.SearchHit
import dev.proxboss.aoe.model.Session
import dev.proxboss.aoe.net.AoeClient
import dev.proxboss.aoe.net.ApiResult
import dev.proxboss.aoe.net.HttpReply
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
                store.replaceAll(envelope.sessions)
                store.setWorkspaceOrdering(envelope.workspaceOrdering)
            }.fold(
                onSuccess = { ApiResult.Ok(Unit) },
                onFailure = { ApiResult.Err("Réponse du serveur illisible") },
            )
            is ApiResult.Err -> result
        }
    }



    suspend fun fetchMcpServers(agent: String? = null): ApiResult<dev.proxboss.aoe.model.McpServersView> {
        val query = if (agent.isNullOrBlank()) "" else "?agent=" + encode(agent)
        return client.get("/api/mcp/servers$query").map { ManagementMapper.parseMcpServers(it) }
    }

    suspend fun createMcpServer(name: String, input: dev.proxboss.aoe.model.McpServerInput): ApiResult<Unit> =
        simplePostBody("/api/mcp/servers", ManagementMapper.mcpCreateBody(name, input))

    suspend fun updateMcpServer(name: String, input: dev.proxboss.aoe.model.McpServerInput): ApiResult<Unit> {
        val body = ManagementMapper.mcpUpdateBody(input)
        return when (val result = client.put("/api/mcp/servers/${encode(name)}", body)) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }
    }

    suspend fun deleteMcpServer(name: String): ApiResult<Unit> =
        when (val result = client.delete("/api/mcp/servers/${encode(name)}")) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }

    suspend fun resolveMcpConflict(name: String, agent: String, winner: String, fingerprint: String): ApiResult<Unit> {
        val body = JSONObject().put("agent", agent).put("winner", winner).put("fingerprint", fingerprint).toString()
        return simplePostBody("/api/mcp/servers/${encode(name)}/resolve", body)
    }

    suspend fun keepMcpServer(name: String, agent: String): ApiResult<Unit> =
        simplePostBody("/api/mcp/servers/${encode(name)}/keep", JSONObject().put("agent", agent).toString())

    suspend fun dropMcpServer(name: String, agent: String): ApiResult<Unit> =
        simplePostBody("/api/mcp/servers/${encode(name)}/drop", JSONObject().put("agent", agent).toString())

    suspend fun fetchSkills(): ApiResult<dev.proxboss.aoe.model.SkillsView> =
        client.get("/api/skills").map { ManagementMapper.parseSkills(it) }

    suspend fun fetchSkillDetail(source: String, directory: String): ApiResult<dev.proxboss.aoe.model.SkillDetail> =
        client.get("/api/skills/${encode(source)}/${encode(directory)}")
            .map { ManagementMapper.parseSkillDetail(it) }

    suspend fun createSkill(directory: String, description: String?): ApiResult<Unit> {
        val body = JSONObject().put("directory", directory)
        if (!description.isNullOrBlank()) body.put("description", description)
        return simplePostBody("/api/skills", body.toString())
    }

    suspend fun updateSkill(directory: String, content: String): ApiResult<Unit> {
        val body = JSONObject().put("content", content).toString()
        return when (val result = client.put("/api/skills/${encode(directory)}", body)) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }
    }

    suspend fun deleteSkill(directory: String): ApiResult<Unit> =
        when (val result = client.delete("/api/skills/${encode(directory)}")) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }

    suspend fun adoptSkill(source: String, directory: String, destination: String?): ApiResult<Unit> {
        val body = JSONObject()
        if (!destination.isNullOrBlank()) body.put("destination", destination)
        return simplePostBody("/api/skills/${encode(source)}/${encode(directory)}/adopt", body.toString())
    }

    suspend fun fetchPlugins(): ApiResult<dev.proxboss.aoe.model.PluginList> =
        client.get("/api/plugins").map { ManagementMapper.parsePlugins(it) }

    suspend fun fetchPluginUpdates(): ApiResult<List<dev.proxboss.aoe.model.PluginUpdateInfo>> =
        client.get("/api/plugins/updates").map { ManagementMapper.parsePluginUpdates(it) }

    suspend fun setPluginEnabled(id: String, enabled: Boolean): ApiResult<Unit> =
        simplePostBody(
            "/api/plugins/${encode(id)}/enabled",
            ManagementMapper.pluginToggleBody(enabled),
        )

    suspend fun fetchProfiles(): ApiResult<List<dev.proxboss.aoe.model.ProfileInfo>> =
        client.get("/api/profiles").map { ManagementMapper.parseProfiles(it) }

    suspend fun createProfile(name: String): ApiResult<Unit> =
        simplePostBody("/api/profiles", JSONObject().put("name", name).toString())

    suspend fun renameProfile(name: String, newName: String): ApiResult<Unit> {
        val body = JSONObject().put("new_name", newName).toString()
        return when (val result = client.patch("/api/profiles/${encode(name)}/rename", body)) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }
    }

    suspend fun setDefaultProfile(name: String): ApiResult<Unit> {
        val body = JSONObject().put("name", name).toString()
        return when (val result = client.patch("/api/default-profile", body)) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }
    }

    suspend fun deleteProfile(name: String): ApiResult<Unit> =
        when (val result = client.delete("/api/profiles/${encode(name)}")) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }

    suspend fun fetchDevices(): ApiResult<List<dev.proxboss.aoe.model.DeviceInfo>> =
        client.get("/api/devices").map { ManagementMapper.parseDevices(it) }

    suspend fun revokeDevice(sessionId: String): ApiResult<Unit> =
        when (val result = client.delete("/api/login/sessions/${encode(sessionId)}")) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }

    suspend fun fetchPasskeys(): ApiResult<List<dev.proxboss.aoe.model.PasskeyInfo>> =
        client.get("/api/passkeys").map { ManagementMapper.parsePasskeys(it) }

    suspend fun deletePasskey(credentialId: String): ApiResult<Unit> =
        when (val result = client.delete("/api/passkeys/${encode(credentialId)}")) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }

    suspend fun fetchHealth(): ApiResult<dev.proxboss.aoe.model.SystemHealth> =
        client.get("/api/system/health").map { ManagementMapper.parseHealth(it) }

    suspend fun fetchLogs(lines: Int = 300): ApiResult<dev.proxboss.aoe.model.LogsSnapshot> =
        client.get("/api/system/logs?lines=$lines").map { ManagementMapper.parseLogs(it) }

    suspend fun fetchUpdateStatus(): ApiResult<dev.proxboss.aoe.model.UpdateStatusInfo> =
        client.get("/api/system/update-status").map { ManagementMapper.parseUpdateStatus(it) }

    suspend fun fetchAbout(): ApiResult<dev.proxboss.aoe.model.AboutInfo> =
        client.get("/api/about").map { SessionMapper.parseAbout(it) }

    private suspend fun simplePostBody(path: String, body: String): ApiResult<Unit> =
        when (val result = client.post(path, body)) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }

    suspend fun fetchGroups(): ApiResult<List<GroupInfo>> =
        client.get("/api/groups").map { CreationMapper.parseGroups(it) }

    suspend fun createGroup(path: String, profile: String = "main"): ApiResult<Unit> {
        val body = JSONObject().put("profile", profile).put("path", path).toString()
        return when (val result = client.post("/api/groups", body)) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }
    }

    suspend fun renameGroup(oldPath: String, newPath: String, profile: String = "main"): ApiResult<Unit> {
        val body = JSONObject()
            .put("source", JSONObject().put("profile", profile).put("path", oldPath))
            .put("target", JSONObject().put("profile", profile).put("path", newPath))
            .toString()
        return when (val result = client.patch("/api/groups", body)) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }
    }

    suspend fun deleteGroup(path: String, mode: String, profile: String = "main"): ApiResult<Unit> {
        val body = JSONObject()
            .put("group", JSONObject().put("profile", profile).put("path", path))
            .put("mode", mode)
            .put("cleanup", JSONObject())
            .toString()
        return when (val result = client.delete("/api/groups", body)) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }
    }


    suspend fun moveSessionToGroup(id: String, group: String): ApiResult<Unit> =
        patchMutation(id, "/api/sessions/$id/group", JSONObject().put("group", group).toString())

    suspend fun updateWorkspaceOrdering(order: List<String>): ApiResult<Unit> {
        val body = JSONObject().put("order", org.json.JSONArray(order)).toString()
        return when (val result = client.put("/api/workspace-ordering", body)) {
            is ApiResult.Ok -> {
                store.setWorkspaceOrdering(order)
                ApiResult.Ok(Unit)
            }
            is ApiResult.Err -> result
        }
    }

    suspend fun fetchAgents(): ApiResult<List<AgentInfo>> =
        client.get("/api/agents").map { CreationMapper.parseAgents(it) }

    suspend fun fetchAcpAgents(): ApiResult<List<AcpAgent>> =
        client.get("/api/acp/agents").map { CreationMapper.parseAcpAgents(it) }

    suspend fun fetchOptionCatalog(): ApiResult<Map<String, List<ConfigOption>>> =
        client.get("/api/acp/option-catalog").map { CreationMapper.parseOptionCatalog(it) }

    suspend fun fetchHome(): ApiResult<String> =
        client.get("/api/filesystem/home").map { CreationMapper.parseHome(it) }

    suspend fun browse(path: String, showHidden: Boolean = false, filter: String? = null): ApiResult<BrowseResult> {
        val query = buildString {
            append("?path=").append(encode(path))
            if (showHidden) append("&show_hidden=true")
            if (!filter.isNullOrBlank()) append("&filter=").append(encode(filter))
        }
        return client.get("/api/filesystem/browse$query").map { CreationMapper.parseBrowse(it) }
    }

    suspend fun fetchRecentProjects(): ApiResult<List<RecentProject>> =
        client.get("/api/recent-projects").map { CreationMapper.parseRecentProjects(it) }

    suspend fun isGitRepo(path: String): ApiResult<Boolean> =
        client.get("/api/git/is-repo?path=${encode(path)}").map { CreationMapper.parseIsGitRepo(it) }

    suspend fun fetchBranches(path: String, includeRemote: Boolean = false): ApiResult<List<BranchInfo>> {
        val query = buildString {
            append("?path=").append(encode(path))
            if (includeRemote) append("&include_remote=true")
        }
        return client.get("/api/git/branches$query").map { CreationMapper.parseBranches(it) }
    }

    suspend fun reviewCreationTrust(path: String, scratch: Boolean): ApiResult<CreationTrustReview?> {
        val body = JSONObject()
            .put("path", path)
            .put("scratch", scratch)
            .toString()
        return when (val result = client.postRaw("/api/sessions/creation-trust", body)) {
            is ApiResult.Ok -> {
                val reply = result.value
                when {
                    reply.isSuccessful -> runCatching { CreationMapper.parseTrust(reply.body) }
                        .fold(
                            onSuccess = { ApiResult.Ok(it) },
                            onFailure = { ApiResult.Err("Réponse de confiance illisible") },
                        )
                    reply.status == 404 || reply.status == 405 -> ApiResult.Ok(null)
                    else -> ApiResult.Err(describeReply(reply), reply.status)
                }
            }
            is ApiResult.Err -> result
        }
    }

    suspend fun createSession(request: CreateSessionRequest): ApiResult<CreateSessionResult> {
        return when (val result = client.postRaw("/api/sessions", CreationMapper.createBody(request))) {
            is ApiResult.Ok -> {
                val reply = result.value
                when {
                    reply.isSuccessful -> runCatching {
                        CreateSessionResult(session = SessionMapper.parseSession(reply.body))
                    }.fold(
                        onSuccess = { ApiResult.Ok(it) },
                        onFailure = { ApiResult.Err("Réponse de création illisible") },
                    )
                    reply.status == 409 && reply.errorCode == "creation_trust_changed" -> {
                        ApiResult.Ok(CreateSessionResult(trustChanged = true, error = "Configuration des hooks modifiée"))
                    }
                    reply.status == 409 && extractErrorCode(reply.body) == "creation_trust_changed" -> {
                        ApiResult.Ok(CreateSessionResult(trustChanged = true, error = "Configuration des hooks modifiée"))
                    }
                    reply.status == 409 && extractErrorCode(reply.body) == "hooks_need_trust" -> {
                        val root = runCatching { JSONObject(reply.body) }.getOrNull()
                        val review = root?.let { CreationMapper.parseTrustRequired(it) }
                        ApiResult.Ok(
                            CreateSessionResult(
                                hooksRequired = review,
                                error = root?.optString("message").orEmpty().ifBlank { null },
                            ),
                        )
                    }
                    reply.status == 409 && extractErrorCode(reply.body) == "create_outcome_unknown" -> {
                        ApiResult.Err("Création peut-être en cours, résultat inconnu", reply.status)
                    }
                    else -> ApiResult.Err(describeReply(reply), reply.status)
                }
            }
            is ApiResult.Err -> result
        }
    }

    suspend fun createProgress(key: String): ApiResult<CreationProgress?> {
        return when (val result = client.get("/api/sessions/create-progress/${encode(key)}")) {
            is ApiResult.Ok -> runCatching { CreationMapper.parseProgress(result.value) }
                .fold(
                    onSuccess = { ApiResult.Ok(it) },
                    onFailure = { ApiResult.Err("Progression illisible") },
                )
            is ApiResult.Err -> if (result.status == 404) ApiResult.Ok(null) else result
        }
    }

    suspend fun rename(id: String, title: String): ApiResult<Unit> =
        patchMutation(id, "/api/sessions/$id", JSONObject().put("title", title).toString())

    suspend fun setArchived(id: String, archived: Boolean): ApiResult<Unit> =
        patchMutation(
            id,
            "/api/sessions/$id/archive",
            JSONObject().put("archived", archived).put("kill_pane", true).toString(),
        )

    suspend fun setSnoozed(id: String, minutes: Int?): ApiResult<Unit> {
        val body = JSONObject().put("minutes", minutes ?: JSONObject.NULL).toString()
        return patchMutation(id, "/api/sessions/$id/snooze", body)
    }

    suspend fun setColor(id: String, color: String?): ApiResult<Unit> {
        val body = JSONObject().put("color", color ?: JSONObject.NULL).toString()
        return patchMutation(id, "/api/sessions/$id/color", body)
    }

    suspend fun trash(id: String): ApiResult<Unit> =
        postMutation(id, "/api/sessions/$id/trash", JSONObject().put("kill_pane", true).toString())

    suspend fun restore(id: String): ApiResult<Unit> =
        postMutation(id, "/api/sessions/$id/restore", null)

    suspend fun reorderSession(id: String, sourceGroup: String, profile: String, up: Boolean): ApiResult<Unit> {
        val body = JSONObject()
            .put("target", "session")
            .put("id", id)
            .put("profile", profile)
            .put("source_group", sourceGroup)
            .put("direction", if (up) "up" else "down")
            .put("destination", JSONObject.NULL)
            .toString()
        return when (val result = client.post("/api/reorder", body)) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }
    }

    suspend fun ensureTerminal(sessionId: String): ApiResult<Unit> =
        simplePost("/api/sessions/$sessionId/terminal")

    suspend fun acpEnable(sessionId: String): ApiResult<Unit> =
        simplePost("/api/sessions/$sessionId/acp/enable")

    suspend fun acpDisable(sessionId: String): ApiResult<Unit> =
        simplePost("/api/sessions/$sessionId/acp/disable")

    suspend fun setMode(sessionId: String, modeId: String): ApiResult<Unit> {
        val body = JSONObject().put("mode_id", modeId).toString()
        return when (val result = client.post("/api/sessions/$sessionId/acp/mode", body)) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }
    }

    suspend fun switchAgent(sessionId: String, target: String): ApiResult<Unit> {
        val body = JSONObject().put("target", target).put("reason", "manual").toString()
        return when (val result = client.post("/api/sessions/$sessionId/acp/switch-agent", body)) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
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
        // Older daemons treated a null tri-state as false, so unmuting answers
        // with an explicit true override instead of "inherit".
        val body = JSONObject()
            .put("notify_on_waiting", !muted)
            .put("notify_on_idle", !muted)
            .put("notify_on_error", !muted)
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

    suspend fun listQueue(sessionId: String): ApiResult<List<dev.proxboss.aoe.model.QueuedPrompt>> =
        client.get("/api/sessions/$sessionId/queue").map { QueueMapper.parseQueue(it) }

    suspend fun editQueuedPrompt(sessionId: String, promptId: String, text: String): ApiResult<Unit> {
        val body = JSONObject().put("text", text).toString()
        return when (val result = client.patch("/api/sessions/$sessionId/queue/${encode(promptId)}", body)) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }
    }

    suspend fun removeQueuedPrompt(sessionId: String, promptId: String): ApiResult<Unit> =
        when (val result = client.delete("/api/sessions/$sessionId/queue/${encode(promptId)}")) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }

    suspend fun clearQueue(sessionId: String): ApiResult<Unit> =
        when (val result = client.delete("/api/sessions/$sessionId/queue")) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
        }

    suspend fun sendQueuedPrompt(sessionId: String, prompt: dev.proxboss.aoe.model.QueuedPrompt): ApiResult<PromptDispatchResult> {
        val body = JSONObject()
            .put("text", prompt.text)
            .put("prompt_id", UUID.randomUUID().toString())
            .put("queued_prompt_id", prompt.id)
            .toString()
        return when (val result = client.post("/api/sessions/$sessionId/acp/prompt", body)) {
            is ApiResult.Ok -> {
                val parsed = runCatching { JSONObject(result.value) }.getOrNull()
                val disposition = parsed?.optString("disposition").orEmpty().ifBlank { null }
                ApiResult.Ok(PromptDispatchResult(queued = disposition == "queued", disposition = disposition))
            }
            is ApiResult.Err -> result
        }
    }

    suspend fun pasteImage(sessionId: String, mimeType: String, base64: String): ApiResult<String> {
        val body = JSONObject()
            .put("mime_type", mimeType)
            .put("data", base64)
            .toString()
        return when (val result = client.post("/api/sessions/$sessionId/paste-image", body)) {
            is ApiResult.Ok -> {
                val path = QueueMapper.parsePastedPath(result.value)
                if (path.isNullOrBlank()) ApiResult.Err("Réponse de collage illisible") else ApiResult.Ok(path)
            }
            is ApiResult.Err -> result
        }
    }

    suspend fun resolveApprovalNonce(
        sessionId: String,
        nonce: String,
        decision: String,
    ): ApiResult<Unit> {
        val body = JSONObject().put("decision", decision).toString()
        return when (val result = client.post("/api/sessions/$sessionId/acp/approvals/${encode(nonce)}", body)) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> result
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

    suspend fun setDiffBase(sessionId: String, baseBranch: String?, repoName: String?): ApiResult<Unit> {
        val body = JSONObject().put("base_branch", baseBranch ?: JSONObject.NULL)
        if (!repoName.isNullOrBlank()) body.put("repo", repoName)
        return patchMutation(sessionId, "/api/sessions/$sessionId/diff-base", body.toString())
    }

    suspend fun sessionFile(sessionId: String, path: String): ApiResult<SessionFileContent> {
        val query = "?path=" + encode(path)
        return client.get("/api/sessions/$sessionId/file$query").map { DiffMapper.parseSessionFile(it) }
    }

    suspend fun replayRowsBefore(
        sessionId: String,
        before: Long,
        limit: Int,
    ): ApiResult<ReplayPage> =
        client.get("/api/sessions/$sessionId/acp/replay?view=rows&before=$before&limit=$limit")
            .map { ReplayMapper.parsePage(it) }

    suspend fun replayRowsSince(
        sessionId: String,
        since: Long,
        limit: Int,
    ): ApiResult<ReplayPage> =
        client.get("/api/sessions/$sessionId/acp/replay?view=rows&since=$since&limit=$limit")
            .map { ReplayMapper.parsePage(it) }

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

    private suspend fun mutation(id: String, path: String, body: String): ApiResult<Unit> =
        patchMutation(id, path, body)

    private suspend fun patchMutation(id: String, path: String, body: String): ApiResult<Unit> =
        when (val result = client.patch(path, body)) {
            is ApiResult.Ok -> {
                runCatching { store.upsert(SessionMapper.parseSession(result.value)) }
                ApiResult.Ok(Unit)
            }
            is ApiResult.Err -> result
        }

    private suspend fun postMutation(id: String, path: String, body: String?): ApiResult<Unit> =
        when (val result = client.post(path, body)) {
            is ApiResult.Ok -> {
                runCatching { store.upsert(SessionMapper.parseSession(result.value)) }
                ApiResult.Ok(Unit)
            }
            is ApiResult.Err -> result
        }

    private fun extractErrorCode(body: String): String? =
        runCatching { JSONObject(body).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun describeReply(reply: HttpReply): String {
        val code = extractErrorCode(reply.body)
        val message = runCatching { JSONObject(reply.body).optString("message") }.getOrNull()
            .orEmpty()
            .ifBlank { code.orEmpty() }
            .ifBlank { reply.body }
        val base = when (reply.status) {
            400 -> "Requête refusée"
            401 -> "Jeton refusé"
            403 -> "Action refusée"
            404 -> "Introuvable"
            409 -> "Conflit"
            422 -> "Requête invalide"
            else -> "Erreur HTTP ${reply.status}"
        }
        return if (message.isBlank()) base else "$base : ${message.take(180)}"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
