package dev.proxboss.aoe.data

import dev.proxboss.aoe.model.AboutInfo
import dev.proxboss.aoe.model.SearchHit
import dev.proxboss.aoe.model.Session
import dev.proxboss.aoe.model.SessionStatus
import dev.proxboss.aoe.util.TimeFormat
import org.json.JSONArray
import org.json.JSONObject

data class SessionEnvelope(
    val sessions: List<Session>,
    val workspaceOrdering: List<String> = emptyList(),
)

object SessionMapper {
    fun parseEnvelope(json: String): SessionEnvelope {
        val root = JSONObject(json)
        val array = root.optJSONArray("sessions") ?: JSONArray()
        val sessions = buildList {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                add(parseSession(row))
            }
        }
        val orderingArray = root.optJSONArray("workspace_ordering") ?: JSONArray()
        val ordering = buildList {
            for (index in 0 until orderingArray.length()) {
                orderingArray.optString(index)?.takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
        return SessionEnvelope(sessions, ordering)
    }

    fun parseSession(json: String): Session = parseSession(JSONObject(json))

    fun parseSession(row: JSONObject): Session {
        val tool = row.stringOrEmpty("tool")
        val model = row.stringOrNull("agent_model") ?: row.stringOrNull("acp_model")
        return Session(
            id = row.stringOrEmpty("id"),
            title = row.stringOrEmpty("title").ifBlank { "Sans titre" },
            projectPath = row.stringOrEmpty("project_path"),
            groupPath = row.stringOrEmpty("group_path"),
            tool = tool,
            agent = row.stringOrNull("agent_name") ?: row.stringOrNull("acp_agent"),
            status = SessionStatus.fromWire(row.stringOrNull("status")),
            dormant = row.optBoolean("dormant", false),
            favorited = row.optBoolean("favorited", false),
            pinnedAt = row.stringOrNull("pinned_at"),
            archivedAt = row.stringOrNull("archived_at"),
            snoozedUntil = row.stringOrNull("snoozed_until"),
            trashedAt = row.stringOrNull("trashed_at"),
            unread = row.optBoolean("unread", false),
            notifyOnWaiting = row.booleanOrNull("notify_on_waiting"),
            notifyOnIdle = row.booleanOrNull("notify_on_idle"),
            notifyOnError = row.booleanOrNull("notify_on_error"),
            view = row.stringOrNull("view"),
            acpCapable = row.optBoolean("acp_capable", false),
            acpWorkerState = row.stringOrNull("acp_worker_state"),
            hasTerminal = row.optBoolean("has_terminal", false),
            scratch = row.optBoolean("scratch", false),
            lastAccessedAt = TimeFormat.parseInstant(row.stringOrNull("last_accessed_at")),
            idleEnteredAt = TimeFormat.parseInstant(row.stringOrNull("idle_entered_at")),
            createdAt = TimeFormat.parseInstant(row.stringOrNull("created_at")),
            lastError = row.stringOrNull("last_error"),
            branch = row.stringOrNull("branch"),
            mainRepoPath = row.stringOrNull("main_repo_path"),
            color = row.stringOrNull("color"),
            profile = row.stringOrEmpty("profile"),
            parentSessionId = row.stringOrNull("parent_session_id"),
            model = model,
        )
    }

    fun parseAbout(json: String): AboutInfo {
        val root = JSONObject(json)
        return AboutInfo(
            version = root.stringOrEmpty("version"),
            authRequired = root.optBoolean("auth_required", false),
            authMode = root.stringOrEmpty("auth_mode").ifBlank { "unknown" },
            readOnly = root.optBoolean("read_only", false),
            profile = root.stringOrEmpty("profile"),
        )
    }

    fun parseSearch(json: String): List<SearchHit> {
        val root = JSONObject(json)
        val results = root.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                add(
                    SearchHit(
                        sessionId = item.stringOrEmpty("session_id"),
                        seq = item.optLong("seq", 0L),
                        snippet = item.stringOrEmpty("snippet"),
                        kind = item.stringOrEmpty("kind"),
                        matchCount = item.optInt("match_count", 0),
                    ),
                )
            }
        }
    }
}

internal fun JSONObject.stringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key)
    return value.takeIf { it.isNotEmpty() }
}

internal fun JSONObject.stringOrEmpty(key: String): String = stringOrNull(key).orEmpty()

internal fun JSONObject.booleanOrNull(key: String): Boolean? {
    if (!has(key) || isNull(key)) return null
    return optBoolean(key)
}
