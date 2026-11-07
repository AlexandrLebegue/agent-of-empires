package dev.proxboss.aoe.data

import dev.proxboss.aoe.model.DeviceInfo
import dev.proxboss.aoe.model.HealthAgent
import dev.proxboss.aoe.model.LogsSnapshot
import dev.proxboss.aoe.model.McpConflictView
import dev.proxboss.aoe.model.McpServerView
import dev.proxboss.aoe.model.McpServersView
import dev.proxboss.aoe.model.PasskeyInfo
import dev.proxboss.aoe.model.PluginInfo
import dev.proxboss.aoe.model.PluginList
import dev.proxboss.aoe.model.PluginUpdateInfo
import dev.proxboss.aoe.model.ProfileInfo
import dev.proxboss.aoe.model.SkillDetail
import dev.proxboss.aoe.model.SkillRoot
import dev.proxboss.aoe.model.SkillSummary
import dev.proxboss.aoe.model.SkillsView
import dev.proxboss.aoe.model.SystemHealth
import dev.proxboss.aoe.model.UpdateStatusInfo
import org.json.JSONArray
import org.json.JSONObject

object ManagementMapper {
    fun parseMcpServers(json: String): McpServersView {
        val root = JSONObject(json)
        return McpServersView(
            agent = root.stringOrEmpty("agent"),
            effective = parseMcpList(root.optJSONArray("effective")),
            keptOnRemoval = parseMcpList(root.optJSONArray("keptOnRemoval")),
            conflicts = parseMcpConflicts(root.optJSONArray("conflicts")),
            driftPaused = root.optBoolean("driftPaused", false),
        )
    }

    private fun parseMcpList(array: JSONArray?): List<McpServerView> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    McpServerView(
                        name = item.stringOrEmpty("name"),
                        transport = item.stringOrEmpty("transport"),
                        command = item.stringOrNull("command"),
                        args = stringList(item.optJSONArray("args")),
                        url = item.stringOrNull("url"),
                        envNames = stringList(item.optJSONArray("envNames")),
                        headerNames = stringList(item.optJSONArray("headerNames")),
                        provenance = item.stringOrEmpty("provenance"),
                        shadowed = stringList(item.optJSONArray("shadowed")),
                    ),
                )
            }
        }
    }

    private fun parseMcpConflicts(array: JSONArray?): List<McpConflictView> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    McpConflictView(
                        name = item.stringOrEmpty("name"),
                        agent = item.stringOrEmpty("agent"),
                        previous = item.stringOrEmpty("previous"),
                        current = item.stringOrEmpty("current"),
                        fingerprint = item.stringOrEmpty("fingerprint"),
                    ),
                )
            }
        }
    }

    fun parseSkills(json: String): SkillsView {
        val root = JSONObject(json)
        val skillsArray = root.optJSONArray("skills") ?: JSONArray()
        val rootsArray = root.optJSONArray("roots") ?: JSONArray()
        return SkillsView(
            skills = buildList {
                for (index in 0 until skillsArray.length()) {
                    val item = skillsArray.optJSONObject(index) ?: continue
                    add(
                        SkillSummary(
                            directory = item.stringOrEmpty("directory"),
                            name = item.stringOrEmpty("name"),
                            description = item.stringOrEmpty("description"),
                            source = provenanceSource(item.opt("provenance")),
                            provenanceLabel = item.stringOrEmpty("provenanceLabel"),
                            writable = item.optBoolean("writable", false),
                        ),
                    )
                }
            },
            roots = buildList {
                for (index in 0 until rootsArray.length()) {
                    val item = rootsArray.optJSONObject(index) ?: continue
                    add(
                        SkillRoot(
                            id = item.stringOrEmpty("id"),
                            label = item.stringOrEmpty("label"),
                            relativePath = item.stringOrEmpty("relativePath"),
                            consumers = stringList(item.optJSONArray("consumers")),
                            legacy = item.optBoolean("legacy", false),
                        ),
                    )
                }
            },
        )
    }

    fun parseSkillDetail(json: String): SkillDetail {
        val item = JSONObject(json)
        return SkillDetail(
            directory = item.stringOrEmpty("directory"),
            name = item.stringOrEmpty("name"),
            description = item.stringOrEmpty("description"),
            source = provenanceSource(item.opt("provenance")),
            content = item.stringOrEmpty("content"),
        )
    }

    private fun provenanceSource(value: Any?): String = when (value) {
        is JSONObject -> value.stringOrEmpty("kind") + value.stringOrNull("root")?.let { ":$it" }.orEmpty()
        is String -> value
        else -> ""
    }

    fun parsePlugins(json: String): PluginList {
        val root = JSONObject(json)
        val array = root.optJSONArray("plugins") ?: JSONArray()
        return PluginList(
            plugins = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        PluginInfo(
                            id = item.stringOrEmpty("id"),
                            name = item.stringOrEmpty("name"),
                            version = item.stringOrEmpty("version"),
                            description = item.stringOrEmpty("description"),
                            enabled = item.optBoolean("enabled", false),
                            builtin = item.optBoolean("builtin", false),
                            validation = item.stringOrEmpty("validation"),
                            source = item.stringOrNull("source"),
                            capabilities = stringList(item.optJSONArray("capabilities")),
                            granted = item.optBoolean("granted", true),
                            needsReapproval = item.optBoolean("needs_reapproval", false),
                        ),
                    )
                }
            },
            loadErrors = stringList(root.optJSONArray("load_errors")),
        )
    }

    fun parsePluginUpdates(json: String): List<PluginUpdateInfo> {
        val root = JSONObject(json)
        val array = root.optJSONArray("updates") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    PluginUpdateInfo(
                        id = item.stringOrEmpty("id"),
                        source = item.stringOrEmpty("source"),
                        current = item.stringOrEmpty("current"),
                        available = item.stringOrNull("available"),
                        needsUpdate = item.optBoolean("needs_update", false),
                        error = item.stringOrNull("error"),
                    ),
                )
            }
        }
    }

    fun parseProfiles(json: String): List<ProfileInfo> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    ProfileInfo(
                        name = item.stringOrEmpty("name"),
                        isDefault = item.optBoolean("is_default", false),
                        description = item.stringOrEmpty("description"),
                    ),
                )
            }
        }
    }

    fun parseDevices(json: String): List<DeviceInfo> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    DeviceInfo(
                        sessionId = item.stringOrEmpty("session_id"),
                        userAgent = item.stringOrEmpty("user_agent"),
                        createdIp = item.stringOrEmpty("created_ip"),
                        createdAt = item.stringOrEmpty("created_at"),
                        lastSeen = item.stringOrEmpty("last_seen"),
                        current = item.optBoolean("current", false),
                    ),
                )
            }
        }
    }

    fun parsePasskeys(json: String): List<PasskeyInfo> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    PasskeyInfo(
                        credentialId = item.stringOrEmpty("credential_id"),
                        label = item.stringOrEmpty("label"),
                        origin = item.stringOrEmpty("origin"),
                        createdAtMs = item.optLong("created_at_ms", 0L),
                        lastUsedAtMs = if (item.has("last_used_at_ms") && !item.isNull("last_used_at_ms")) {
                            item.optLong("last_used_at_ms")
                        } else {
                            null
                        },
                    ),
                )
            }
        }
    }

    fun parseHealth(json: String): SystemHealth {
        val root = JSONObject(json)
        val agents = root.optJSONArray("agents") ?: JSONArray()
        val load = root.optJSONArray("load_average")
        return SystemHealth(
            status = root.stringOrEmpty("status"),
            cpuFraction = if (root.has("cpu_fraction") && !root.isNull("cpu_fraction")) {
                root.optDouble("cpu_fraction")
            } else {
                null
            },
            memoryUsedBytes = root.optLong("memory_used_bytes", 0L),
            memoryTotalBytes = root.optLong("memory_total_bytes", 0L),
            loadAverage = buildList {
                if (load != null) {
                    for (index in 0 until load.length()) add(load.optDouble(index))
                }
            },
            swapUsedBytes = root.optLong("swap_used_bytes", 0L),
            swapTotalBytes = root.optLong("swap_total_bytes", 0L),
            agentCount = root.optInt("agent_count", 0),
            procCount = root.optInt("proc_count", 0),
            agents = buildList {
                for (index in 0 until agents.length()) {
                    val item = agents.optJSONObject(index) ?: continue
                    add(
                        HealthAgent(
                            id = item.stringOrEmpty("id"),
                            title = item.stringOrEmpty("title"),
                            cpuFraction = if (item.has("cpu_fraction") && !item.isNull("cpu_fraction")) {
                                item.optDouble("cpu_fraction")
                            } else {
                                null
                            },
                            memoryBytes = if (item.has("memory_bytes") && !item.isNull("memory_bytes")) {
                                item.optLong("memory_bytes")
                            } else {
                                null
                            },
                            procs = if (item.has("procs") && !item.isNull("procs")) item.optInt("procs") else null,
                            sandboxed = item.optBoolean("sandboxed", false),
                        ),
                    )
                }
            },
        )
    }

    fun parseLogs(json: String): LogsSnapshot {
        val root = JSONObject(json)
        return LogsSnapshot(
            status = root.stringOrEmpty("status"),
            content = root.stringOrEmpty("content"),
            lineCount = root.optInt("line_count", 0),
            truncated = root.optBoolean("truncated", false),
        )
    }

    fun parseUpdateStatus(json: String): UpdateStatusInfo {
        val root = JSONObject(json)
        return UpdateStatusInfo(
            updateCheckMode = root.stringOrEmpty("update_check_mode"),
            currentVersion = root.stringOrEmpty("current_version"),
            latestVersion = root.stringOrNull("latest_version"),
            updateAvailable = root.optBoolean("update_available", false),
            releaseUrl = root.stringOrNull("release_url"),
            error = root.stringOrNull("error"),
            installMethod = root.stringOrEmpty("install_method"),
            updateCommand = root.stringOrEmpty("update_command"),
        )
    }

    fun pluginToggleBody(enabled: Boolean): String =
        JSONObject().put("enabled", enabled).toString()

    fun mcpCreateBody(name: String, input: dev.proxboss.aoe.model.McpServerInput): String {
        val body = JSONObject().put("name", name).put("transport", input.transport)
        if (input.transport == "stdio") {
            body.put("command", input.command.orEmpty())
            body.put("args", JSONArray(input.args))
            body.put("env", secrets(input.env))
        } else {
            body.put("url", input.url.orEmpty())
            body.put("headers", secrets(input.headers))
        }
        return body.toString()
    }

    fun mcpUpdateBody(input: dev.proxboss.aoe.model.McpServerInput): String {
        val body = JSONObject().put("transport", input.transport)
        if (input.transport == "stdio") {
            body.put("command", input.command.orEmpty())
            body.put("args", JSONArray(input.args))
            body.put("env", secrets(input.env))
        } else {
            body.put("url", input.url.orEmpty())
            body.put("headers", secrets(input.headers))
        }
        return body.toString()
    }

    private fun secrets(entries: List<dev.proxboss.aoe.model.McpSecretEntry>): JSONArray {
        val array = JSONArray()
        entries.forEach { entry ->
            if (entry.name.isBlank()) return@forEach
            val item = JSONObject().put("name", entry.name)
            if (!entry.value.isNullOrEmpty()) item.put("value", entry.value)
            array.put(item)
        }
        return array
    }

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isNotBlank()) add(value)
            }
        }
    }
}
