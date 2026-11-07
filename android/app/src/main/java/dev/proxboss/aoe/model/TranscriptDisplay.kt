package dev.proxboss.aoe.model

import java.time.Instant

sealed interface DisplayItem {
    val key: String
    val at: Instant?
}

data class UserDisplay(
    override val key: String,
    val text: String,
    val attachments: List<String>,
    override val at: Instant?,
) : DisplayItem

data class MessageDisplay(
    override val key: String,
    val text: String,
    val thinking: Boolean,
    override val at: Instant?,
) : DisplayItem

data class ToolDisplay(
    override val key: String,
    val name: String,
    val kind: String,
    val args: String,
    val output: String,
    val isError: Boolean,
    val diffs: List<DiffPreview>,
    override val at: Instant?,
) : DisplayItem

data class NoticeDisplay(
    override val key: String,
    val text: String,
    val isError: Boolean,
    override val at: Instant?,
) : DisplayItem

object TranscriptDisplay {
    fun build(rows: List<TranscriptRow>): List<DisplayItem> {
        val output = ArrayList<DisplayItem>()
        val toolIndex = HashMap<String, Int>()

        fun upsertTool(id: String, transform: (ToolDisplay) -> ToolDisplay, create: () -> ToolDisplay) {
            val index = toolIndex[id]
            if (index != null && index in output.indices && output[index] is ToolDisplay) {
                output[index] = transform(output[index] as ToolDisplay)
            } else {
                toolIndex[id] = output.size
                output.add(create())
            }
        }

        for (row in rows) {
            when (row.kind) {
                TranscriptKind.MESSAGE, TranscriptKind.THINKING -> {
                    val key = "${row.kind.wire}:${row.groupId.ifBlank { row.id }}"
                    val last = output.lastOrNull()
                    if (last is MessageDisplay && last.key == key) {
                        output[output.lastIndex] = last.copy(
                            text = joinChunks(last.text, row.text, row.messageStart),
                        )
                    } else {
                        output.add(
                            MessageDisplay(
                                key = key,
                                text = row.text,
                                thinking = row.kind == TranscriptKind.THINKING,
                                at = row.at,
                            ),
                        )
                    }
                }

                TranscriptKind.USER_PROMPT, TranscriptKind.USER_DIFF_COMMENTS -> {
                    output.add(UserDisplay(row.id, row.text, row.attachmentNames, row.at))
                }

                TranscriptKind.TOOL_START -> {
                    val tool = row.tool
                    val id = tool?.id ?: row.toolCallId ?: row.id
                    val name = tool?.name?.takeIf { it.isNotBlank() }
                        ?: row.text.lineSequence().firstOrNull().orEmpty().ifBlank { "Outil" }
                    upsertTool(
                        id = id,
                        transform = { current ->
                            current.copy(
                                name = name.ifBlank { current.name },
                                kind = tool?.kind?.ifBlank { current.kind } ?: current.kind,
                                args = tool?.argsPreview?.ifBlank { current.args } ?: current.args,
                                diffs = tool?.diffs?.takeIf { it.isNotEmpty() } ?: current.diffs,
                            )
                        },
                        create = {
                            ToolDisplay(
                                key = row.id,
                                name = name,
                                kind = tool?.kind.orEmpty(),
                                args = tool?.argsPreview ?: row.text,
                                output = row.outputText,
                                isError = false,
                                diffs = tool?.diffs.orEmpty(),
                                at = row.at,
                            )
                        },
                    )
                }

                TranscriptKind.TOOL_COMPLETE,
                TranscriptKind.TOOL_ERROR,
                TranscriptKind.TOOL_STOPPED,
                -> {
                    val id = row.toolCallId ?: row.tool?.id ?: row.id
                    val isError = row.kind == TranscriptKind.TOOL_ERROR
                    val resultText = row.outputText.ifBlank { row.text }
                    upsertTool(
                        id = id,
                        transform = { current ->
                            current.copy(
                                output = resultText.ifBlank { current.output },
                                isError = current.isError || isError,
                                diffs = row.tool?.diffs?.takeIf { it.isNotEmpty() } ?: current.diffs,
                            )
                        },
                        create = {
                            ToolDisplay(
                                key = row.id,
                                name = row.tool?.name?.ifBlank { "Outil" } ?: "Outil",
                                kind = row.tool?.kind.orEmpty(),
                                args = row.tool?.argsPreview.orEmpty(),
                                output = resultText,
                                isError = isError,
                                diffs = row.tool?.diffs.orEmpty(),
                                at = row.at,
                            )
                        },
                    )
                }

                TranscriptKind.EMPTY_OUTPUT -> Unit

                else -> {
                    if (row.text.isNotBlank()) {
                        output.add(
                            NoticeDisplay(
                                key = row.id,
                                text = row.text,
                                isError = row.kind == TranscriptKind.NOTICE && row.text.startsWith("error:", true),
                                at = row.at,
                            ),
                        )
                    }
                }
            }
        }
        return output
    }

    private fun joinChunks(previous: String, next: String, startsNewMessage: Boolean): String {
        if (startsNewMessage && previous.isNotEmpty()) return "$previous\n\n$next"
        return previous + next
    }
}
