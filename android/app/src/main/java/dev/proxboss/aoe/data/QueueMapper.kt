package dev.proxboss.aoe.data

import dev.proxboss.aoe.model.QueuedAttachment
import dev.proxboss.aoe.model.QueuedPrompt
import org.json.JSONArray
import org.json.JSONObject

object QueueMapper {
    fun parseQueue(json: String): List<QueuedPrompt> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val attachments = item.optJSONArray("attachments")
                add(
                    QueuedPrompt(
                        id = item.stringOrEmpty("id"),
                        seq = item.optLong("seq", 0L),
                        text = item.stringOrEmpty("text"),
                        attachments = buildList {
                            if (attachments != null) {
                                for (attachmentIndex in 0 until attachments.length()) {
                                    val attachment = attachments.optJSONObject(attachmentIndex) ?: continue
                                    add(
                                        QueuedAttachment(
                                            id = attachment.stringOrEmpty("id"),
                                            kind = attachment.stringOrEmpty("kind"),
                                            mimeType = attachment.stringOrEmpty("mime_type"),
                                            name = attachment.stringOrNull("name"),
                                            size = attachment.optLong("size", 0L),
                                        ),
                                    )
                                }
                            }
                        },
                        createdAt = item.stringOrEmpty("created_at"),
                    ),
                )
            }
        }
    }

    fun parsePastedPath(json: String): String? =
        runCatching { JSONObject(json).stringOrNull("path") }.getOrNull()
}
