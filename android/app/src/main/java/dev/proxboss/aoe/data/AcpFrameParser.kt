package dev.proxboss.aoe.data

import dev.proxboss.aoe.model.AcpControl
import dev.proxboss.aoe.model.AcpMode
import dev.proxboss.aoe.model.Approval
import dev.proxboss.aoe.model.ApprovalOption
import dev.proxboss.aoe.model.ConfigOption
import dev.proxboss.aoe.model.ConfigOptionChoice
import dev.proxboss.aoe.model.DiffPreview
import dev.proxboss.aoe.model.TranscriptDelta
import dev.proxboss.aoe.model.TranscriptKind
import dev.proxboss.aoe.model.TranscriptRow
import dev.proxboss.aoe.model.ToolCall
import dev.proxboss.aoe.util.TimeFormat
import org.json.JSONArray
import org.json.JSONObject

sealed interface AcpFrame {
    data object Heartbeat : AcpFrame
    data object Lagged : AcpFrame
    data class ReducedState(val payload: ReducedStatePayload) : AcpFrame
    data class TranscriptSnapshot(val rows: List<TranscriptRow>) : AcpFrame
    data class Delta(val seq: Long, val delta: TranscriptDelta) : AcpFrame
}

/**
 * A `reduced_state` may omit the large cold fields the connection already has.
 * A null here means "keep the previous value"; an empty list means "now empty".
 */
data class ReducedStatePayload(
    val agent: String? = null,
    val model: String? = null,
    val mode: String? = null,
    val configOptions: List<ConfigOption>? = null,
    val availableModes: List<AcpMode>? = null,
    val currentModeId: String? = null,
    val pendingApprovals: List<Approval>? = null,
    val pendingElicitationCount: Int? = null,
    val turnActive: Boolean? = null,
    val cancelling: Boolean? = null,
    val startupError: String? = null,
    val unchanged: Set<String> = emptySet(),
) {
    fun mergeInto(previous: AcpControl): AcpControl = AcpControl(
        agent = agent ?: previous.agent,
        model = model,
        mode = mode ?: previous.mode,
        configOptions = configOptions ?: previous.configOptions,
        availableModes = availableModes ?: previous.availableModes,
        currentModeId = currentModeId ?: previous.currentModeId,
        pendingApprovals = pendingApprovals ?: previous.pendingApprovals,
        pendingElicitationCount = pendingElicitationCount ?: previous.pendingElicitationCount,
        turnActive = turnActive ?: previous.turnActive,
        cancelling = cancelling ?: previous.cancelling,
        startupError = startupError,
    )
}

object AcpFrameParser {
    private const val MAX_SNAPSHOT_ROWS = 800
    const val MAX_WS_FRAME_CHARS = 512 * 1024

    /**
     * Live sockets never feed the history: it comes from the paginated replay.
     * Ignore oversized frames and snapshots so a multi-megabyte transcript
     * cannot stall or OOM the UI.
     */
    fun parseLive(text: String, maxChars: Int = MAX_WS_FRAME_CHARS): AcpFrame? {
        if (text.length > maxChars) return null
        val frame = parse(text) ?: return null
        if (frame is AcpFrame.TranscriptSnapshot) return null
        return frame
    }

    fun parse(text: String): AcpFrame? {
        // Snapshot frames can carry several megabytes of history. Their kind is
        // serialized first, so avoid materializing the whole document as a
        // JSONObject and let the tail extractor pick only the last rows.
        val head = text.substring(0, minOf(text.length, 256))
        if (head.contains("\"transcript_snapshot\"")) {
            return AcpFrame.TranscriptSnapshot(parseSnapshot(text, root = null))
        }
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        return when (root.optString("kind")) {
            "heartbeat" -> AcpFrame.Heartbeat
            "lagged" -> AcpFrame.Lagged
            "reduced_state" -> root.optJSONObject("state")?.let { state ->
                AcpFrame.ReducedState(parseReducedState(state, root.optJSONArray("unchanged")))
            }
            "transcript_delta" -> root.optJSONObject("delta")?.let { delta ->
                parseDelta(delta)?.let { AcpFrame.Delta(root.optLong("seq", 0L), it) }
            }
            else -> null
        }
    }

    fun parseReducedState(state: JSONObject, unchangedArray: JSONArray? = null): ReducedStatePayload {
        val unchanged = buildSet {
            if (unchangedArray != null) {
                for (index in 0 until unchangedArray.length()) {
                    unchangedArray.optString(index)?.takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
        }
        val configOptions = if (state.has("config_options") && !state.isNull("config_options")) {
            parseConfigOptions(state.optJSONArray("config_options") ?: JSONArray())
        } else {
            null
        }
        val availableModes = if (state.has("available_modes") && !state.isNull("available_modes")) {
            parseAvailableModes(state.optJSONArray("available_modes") ?: JSONArray())
        } else {
            null
        }
        val approvals = if (state.has("pending_approvals") && !state.isNull("pending_approvals")) {
            parseApprovals(state.optJSONArray("pending_approvals") ?: JSONArray())
        } else {
            null
        }
        val elicitations = state.optJSONArray("pending_elicitations")
        val startupError = when {
            !state.has("startup_error") || state.isNull("startup_error") -> null
            else -> {
                val value = state.opt("startup_error")
                when (value) {
                    is JSONObject -> value.stringOrNull("message") ?: value.toString()
                    else -> value?.toString()
                }
            }
        }
        return ReducedStatePayload(
            agent = state.stringOrNull("agent"),
            model = state.stringOrNull("model"),
            mode = state.stringOrNull("mode"),
            configOptions = configOptions,
            availableModes = availableModes,
            currentModeId = state.stringOrNull("current_mode_id"),
            pendingApprovals = approvals,
            pendingElicitationCount = elicitations?.length() ?: 0,
            turnActive = state.optBoolean("turn_active", false),
            cancelling = state.optBoolean("cancelling", false),
            startupError = startupError,
            unchanged = unchanged,
        )
    }

    private fun parseSnapshot(text: String, root: JSONObject?): List<TranscriptRow> {
        val rawRows = JsonTail.objectStrings(text, "rows", MAX_SNAPSHOT_ROWS)
        if (rawRows.isNotEmpty()) {
            return rawRows.mapNotNull { raw ->
                runCatching { JSONObject(raw) }.getOrNull()?.let(::parseRow)
            }
        }
        if (root == null) return emptyList()
        val array = root.optJSONArray("rows") ?: return emptyList()
        val start = (array.length() - MAX_SNAPSHOT_ROWS).coerceAtLeast(0)
        return buildList {
            for (index in start until array.length()) {
                array.optJSONObject(index)?.let { add(parseRow(it)) }
            }
        }
    }

    fun parseRow(row: JSONObject): TranscriptRow {
        val tool = row.optJSONObject("tool")?.let(::parseToolCall)
        val output = row.optJSONArray("output")
        val outputText = buildList {
            if (output != null) {
                for (index in 0 until output.length()) {
                    val block = output.optJSONObject(index) ?: continue
                    when (block.optString("kind")) {
                        "text" -> block.stringOrNull("text")?.let(::add)
                        "resource" -> block.stringOrNull("text")?.let(::add)
                    }
                }
            }
        }.joinToString("\n")
        val attachments = row.optJSONArray("attachments")
        val attachmentNames = buildList {
            if (attachments != null) {
                for (index in 0 until attachments.length()) {
                    val item = attachments.optJSONObject(index) ?: continue
                    add(item.stringOrNull("name") ?: item.stringOrNull("mime_type") ?: "pièce jointe")
                }
            }
        }
        return TranscriptRow(
            id = row.stringOrEmpty("id"),
            groupId = row.stringOrEmpty("group_id"),
            kind = TranscriptKind.fromWire(row.stringOrNull("kind")),
            at = TimeFormat.parseInstant(row.stringOrNull("at")),
            text = row.stringOrEmpty("text"),
            toolCallId = row.stringOrNull("tool_call_id"),
            tool = tool,
            outputText = outputText,
            attachmentNames = attachmentNames,
            messageStart = row.optBoolean("message_start", false),
        )
    }

    private fun parseToolCall(tool: JSONObject): ToolCall {
        val diffs = tool.optJSONArray("diffs")
        return ToolCall(
            id = tool.stringOrEmpty("id"),
            name = tool.stringOrEmpty("name"),
            kind = tool.stringOrEmpty("kind"),
            argsPreview = tool.stringOrEmpty("args_preview"),
            startedAt = TimeFormat.parseInstant(tool.stringOrNull("started_at")),
            diffs = buildList {
                if (diffs != null) {
                    for (index in 0 until diffs.length()) {
                        val diff = diffs.optJSONObject(index) ?: continue
                        add(
                            DiffPreview(
                                path = diff.stringOrEmpty("path"),
                                oldText = diff.stringOrNull("old_text"),
                                newText = diff.stringOrNull("new_text"),
                            ),
                        )
                    }
                }
            },
        )
    }

    private fun parseApprovals(array: JSONArray): List<Approval> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val toolCall = item.optJSONObject("tool_call")?.let(::parseToolCall)
                ?: ToolCall("", "Outil", "other", "", null, emptyList())
            val options = item.optJSONArray("options")
            add(
                Approval(
                    nonce = item.stringOrEmpty("nonce"),
                    toolCall = toolCall,
                    destructive = item.optBoolean("destructive", false),
                    options = buildList {
                        if (options != null) {
                            for (optionIndex in 0 until options.length()) {
                                val option = options.optJSONObject(optionIndex) ?: continue
                                add(
                                    ApprovalOption(
                                        optionId = option.stringOrEmpty("option_id"),
                                        name = option.stringOrEmpty("name"),
                                        kind = option.stringOrEmpty("kind"),
                                    ),
                                )
                            }
                        }
                    },
                    choice = item.optBoolean("choice", false),
                    plan = item.stringOrNull("plan"),
                ),
            )
        }
    }

    internal fun parseConfigOptions(array: JSONArray): List<ConfigOption> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val values = item.optJSONArray("options")
            add(
                ConfigOption(
                    id = item.stringOrEmpty("id"),
                    name = item.stringOrEmpty("name"),
                    category = item.stringOrEmpty("category"),
                    currentValue = item.stringOrEmpty("current_value"),
                    choices = buildList {
                        if (values != null) {
                            for (choiceIndex in 0 until values.length()) {
                                val choice = values.optJSONObject(choiceIndex) ?: continue
                                add(
                                    ConfigOptionChoice(
                                        value = choice.stringOrEmpty("value"),
                                        name = choice.stringOrEmpty("name"),
                                    ),
                                )
                            }
                        }
                    },
                ),
            )
        }
    }

    private fun parseAvailableModes(array: JSONArray): List<AcpMode> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                AcpMode(
                    id = item.stringOrEmpty("id"),
                    name = item.stringOrEmpty("name").ifBlank { item.stringOrEmpty("id") },
                    description = item.stringOrEmpty("description"),
                ),
            )
        }
    }

    fun parseDelta(delta: JSONObject): TranscriptDelta? {
        if (delta.has("Append")) {
            val row = delta.optJSONObject("Append") ?: return null
            return TranscriptDelta.Append(parseRow(row))
        }
        if (delta.has("Patch")) {
            val patch = delta.optJSONObject("Patch") ?: return null
            val row = patch.optJSONObject("row") ?: return null
            return TranscriptDelta.Patch(patch.stringOrEmpty("id"), parseRow(row))
        }
        if (delta.has("Remove")) {
            val id = when (val value = delta.opt("Remove")) {
                is String -> value
                else -> value?.toString().orEmpty()
            }
            if (id.isNotEmpty()) return TranscriptDelta.Remove(id)
        }
        return null
    }
}
