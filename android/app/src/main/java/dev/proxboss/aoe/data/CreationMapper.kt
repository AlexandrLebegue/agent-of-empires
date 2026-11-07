package dev.proxboss.aoe.data

import dev.proxboss.aoe.model.AcpAgent
import dev.proxboss.aoe.model.AgentInfo
import dev.proxboss.aoe.model.BranchInfo
import dev.proxboss.aoe.model.BrowseResult
import dev.proxboss.aoe.model.ConfigOption
import dev.proxboss.aoe.model.CreateSessionRequest
import dev.proxboss.aoe.model.CreationFingerprint
import dev.proxboss.aoe.model.CreationProgress
import dev.proxboss.aoe.model.CreationTrustReview
import dev.proxboss.aoe.model.DirEntry
import dev.proxboss.aoe.model.GroupInfo
import dev.proxboss.aoe.model.HookCommands
import dev.proxboss.aoe.model.RecentProject
import org.json.JSONArray
import org.json.JSONObject

object CreationMapper {
    fun parseHome(json: String): String = JSONObject(json).stringOrEmpty("path")

    fun parseIsGitRepo(json: String): Boolean = JSONObject(json).optBoolean("is_git_repo", false)

    fun parseAgents(json: String): List<AgentInfo> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.stringOrEmpty("name")
                if (name.isBlank()) continue
                val acpCapable = item.optBoolean("acp_capable", false)
                val acpInstalled = item.optBoolean("acp_installed", false)
                add(
                    AgentInfo(
                        name = name,
                        displayName = item.stringOrNull("display_name") ?: name,
                        kind = item.stringOrEmpty("kind"),
                        installed = item.optBoolean("installed", true),
                        acpCapable = acpCapable,
                        acpInstalled = acpInstalled,
                        acpCreateReady = item.optBoolean("acp_create_ready", acpInstalled),
                        acpAllowed = if (item.has("acp_allowed") && !item.isNull("acp_allowed")) {
                            item.optBoolean("acp_allowed", true)
                        } else {
                            true
                        },
                        alwaysYolo = item.optBoolean("always_yolo", false),
                        acpAlwaysYolo = item.optBoolean("acp_always_yolo", false),
                    ),
                )
            }
        }
    }

    fun parseBrowse(json: String): BrowseResult {
        val root = JSONObject(json)
        val array = root.optJSONArray("entries") ?: JSONArray()
        val entries = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val path = item.stringOrEmpty("path")
                if (path.isBlank()) continue
                add(
                    DirEntry(
                        name = item.stringOrEmpty("name").ifBlank { path.substringAfterLast('/') },
                        path = path,
                        isGitRepo = item.optBoolean("is_git_repo", false),
                    ),
                )
            }
        }
        return BrowseResult(entries, root.optBoolean("has_more", false))
    }

    fun parseRecentProjects(json: String): List<RecentProject> {
        val root = JSONObject(json)
        val array = root.optJSONArray("projects") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val path = item.stringOrEmpty("path")
                if (path.isBlank()) continue
                add(
                    RecentProject(
                        path = path,
                        displayName = item.stringOrEmpty("display_name").ifBlank { path.substringAfterLast('/') },
                        tool = item.stringOrEmpty("tool"),
                    ),
                )
            }
        }
    }

    fun parseBranches(json: String): List<BranchInfo> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.stringOrEmpty("name")
                if (name.isBlank()) continue
                add(
                    BranchInfo(
                        name = name,
                        isCurrent = item.optBoolean("is_current", false),
                        remoteOnly = item.optBoolean("remote_only", false),
                    ),
                )
            }
        }
    }

    fun parseGroups(json: String): List<GroupInfo> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val path = item.stringOrEmpty("path")
                if (path.isBlank()) continue
                add(
                    GroupInfo(
                        path = path,
                        sessionCount = item.optInt("session_count", 0),
                        archived = item.optBoolean("archived", false),
                    ),
                )
            }
        }
    }

    fun parseAcpAgents(json: String): List<AcpAgent> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.stringOrEmpty("name")
                if (name.isBlank()) continue
                add(
                    AcpAgent(
                        name = name,
                        description = item.stringOrEmpty("description"),
                        command = item.stringOrEmpty("command"),
                    ),
                )
            }
        }
    }

    fun parseOptionCatalog(json: String): Map<String, List<ConfigOption>> {
        val root = JSONObject(json)
        val agents = root.optJSONObject("agents") ?: return emptyMap()
        val result = mutableMapOf<String, List<ConfigOption>>()
        val keys = agents.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = agents.optJSONObject(key) ?: continue
            val options = entry.optJSONArray("options") ?: continue
            result[key] = AcpFrameParser.parseConfigOptions(options)
        }
        return result
    }

    fun parseTrust(json: String): CreationTrustReview {
        val root = JSONObject(json)
        return parseTrust(root, includeFingerprint = true)
    }

    fun parseTrustRequired(root: JSONObject): CreationTrustReview =
        parseTrust(root, includeFingerprint = false)

    private fun parseTrust(root: JSONObject, includeFingerprint: Boolean): CreationTrustReview {
        val fingerprint = if (includeFingerprint) {
            root.optJSONObject("fingerprint")?.let { fp ->
                CreationFingerprint(
                    projectPath = fp.stringOrEmpty("project_path"),
                    baseHooksHash = fp.stringOrEmpty("base_hooks_hash"),
                    hooksHash = fp.stringOrNull("hooks_hash"),
                    mcpHash = fp.stringOrNull("mcp_hash"),
                )
            }
        } else {
            null
        }
        val merged = root.optJSONObject("merged_hooks") ?: root
        val repo = root.optJSONObject("repo_hooks")
        val summaries = root.optJSONArray("mcp_summaries")
        return CreationTrustReview(
            fingerprint = fingerprint,
            mergedHooks = parseHooks(merged),
            repoHooks = parseHooks(repo),
            mcpSummaries = buildList {
                if (summaries != null) {
                    for (index in 0 until summaries.length()) {
                        val value = summaries.optString(index)
                        if (value.isNotBlank()) add(value)
                    }
                }
            },
            hooksNeedTrust = root.optBoolean("hooks_need_trust", true),
            mcpNeedTrust = root.optBoolean("mcp_need_trust", root.optBoolean("needs_mcp_trust", false)),
        )
    }

    private fun parseHooks(root: JSONObject?): HookCommands {
        if (root == null) return HookCommands()
        return HookCommands(
            onCreate = stringArray(root.optJSONArray("on_create")),
            onLaunch = stringArray(root.optJSONArray("on_launch")),
            onDestroy = stringArray(root.optJSONArray("on_destroy")),
        )
    }

    private fun stringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isNotBlank()) add(value)
            }
        }
    }

    fun createBody(request: CreateSessionRequest): String {
        val body = JSONObject()
            .put("path", if (request.scratch) "" else request.path)
            .put("tool", request.tool)
            .put("view", if (request.structured) "structured" else "terminal")
            .put("yolo_mode", request.yoloMode)
            .put("worktree_enabled", request.worktreeEnabled)
            .put("create_new_branch", request.createNewBranch)
            .put("scratch", request.scratch)
            .put("idempotency_key", request.idempotencyKey)
        if (!request.title.isNullOrBlank()) body.put("title", request.title.trim())
        if (!request.group.isNullOrBlank()) body.put("group", request.group.trim())
        if (request.structured && !request.agentModel.isNullOrBlank()) body.put("agent_model", request.agentModel)
        if (request.structured && !request.agentEffort.isNullOrBlank()) body.put("agent_effort", request.agentEffort)
        if (request.worktreeEnabled) {
            if (!request.worktreeBranch.isNullOrBlank()) body.put("worktree_branch", request.worktreeBranch.trim())
            if (request.createNewBranch && !request.baseBranch.isNullOrBlank()) {
                body.put("base_branch", request.baseBranch.trim())
            }
        }
        if (request.trustHooks != null) body.put("trust_hooks", request.trustHooks)
        request.trustReview?.let { fingerprint ->
            body.put(
                "trust_review",
                JSONObject()
                    .put("project_path", fingerprint.projectPath)
                    .put("base_hooks_hash", fingerprint.baseHooksHash)
                    .put("hooks_hash", fingerprint.hooksHash ?: JSONObject.NULL)
                    .put("mcp_hash", fingerprint.mcpHash ?: JSONObject.NULL),
            )
        }
        return body.toString()
    }

    fun parseProgress(json: String): CreationProgress {
        val root = JSONObject(json)
        val output = root.optJSONArray("output")
        return CreationProgress(
            stage = root.stringOrEmpty("stage").ifBlank { "preparing" },
            hook = root.stringOrNull("hook"),
            output = stringArray(output),
        )
    }
}
