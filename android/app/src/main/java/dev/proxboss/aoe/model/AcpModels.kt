package dev.proxboss.aoe.model

import java.time.Instant

data class DiffPreview(
    val path: String,
    val oldText: String?,
    val newText: String?,
)

data class ToolCall(
    val id: String,
    val name: String,
    val kind: String,
    val argsPreview: String,
    val startedAt: Instant?,
    val diffs: List<DiffPreview>,
)

data class ApprovalOption(
    val optionId: String,
    val name: String,
    val kind: String,
)

data class Approval(
    val nonce: String,
    val toolCall: ToolCall,
    val destructive: Boolean,
    val options: List<ApprovalOption>,
    val choice: Boolean,
    val plan: String?,
)

data class ConfigOptionChoice(
    val value: String,
    val name: String,
)

data class ConfigOption(
    val id: String,
    val name: String,
    val category: String,
    val currentValue: String,
    val choices: List<ConfigOptionChoice>,
) {
    val isModel: Boolean get() = category == "model" || id == "model"
    val isEffort: Boolean get() = category == "thought_level" || id == "effort" || id == "reasoning_effort"
}

data class AcpControl(
    val agent: String? = null,
    val model: String? = null,
    val mode: String? = null,
    val configOptions: List<ConfigOption> = emptyList(),
    val pendingApprovals: List<Approval> = emptyList(),
    val pendingElicitationCount: Int = 0,
    val turnActive: Boolean = false,
    val cancelling: Boolean = false,
    val startupError: String? = null,
) {
    val modelOption: ConfigOption? get() = configOptions.firstOrNull { it.isModel }
    val effortOption: ConfigOption? get() = configOptions.firstOrNull { it.isEffort }
}

enum class TranscriptKind(val wire: String) {
    TOOL_START("tool_start"),
    TOOL_COMPLETE("tool_complete"),
    TOOL_ERROR("tool_error"),
    TOOL_STOPPED("tool_stopped"),
    MESSAGE("message"),
    THINKING("thinking"),
    USER_PROMPT("user_prompt"),
    USER_DIFF_COMMENTS("user_diff_comments"),
    ELICITATION_ANSWERED("elicitation_answered"),
    EMPTY_OUTPUT("empty_output"),
    CONTEXT_RESET("context_reset"),
    SESSION_CLEARED("session_cleared"),
    COMPACTED("compacted"),
    COMPACTION_SUMMARY("compaction_summary"),
    SUMMARY("summary"),
    NOTICE("notice"),
    ADVISORY("advisory"),
    OTHER("other");

    companion object {
        fun fromWire(value: String?): TranscriptKind =
            entries.firstOrNull { it.wire == value } ?: OTHER
    }
}

data class TranscriptRow(
    val id: String,
    val groupId: String,
    val kind: TranscriptKind,
    val at: Instant?,
    val text: String,
    val toolCallId: String?,
    val tool: ToolCall?,
    val outputText: String,
    val attachmentNames: List<String>,
    val messageStart: Boolean,
)

sealed interface TranscriptDelta {
    data class Append(val row: TranscriptRow) : TranscriptDelta
    data class Patch(val id: String, val row: TranscriptRow) : TranscriptDelta
    data class Remove(val id: String) : TranscriptDelta
}

data class PendingUserPrompt(val id: String, val text: String)
