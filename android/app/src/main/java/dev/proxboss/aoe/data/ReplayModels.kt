package dev.proxboss.aoe.data

import dev.proxboss.aoe.model.TranscriptRow
import org.json.JSONObject

data class ReplayPage(
    val rows: List<TranscriptRow>,
    val lost: Boolean,
    val highestSeq: Long,
    val lowestSeq: Long?,
    val nextCursor: Long?,
    val hasMore: Boolean,
)

object ReplayPagination {
    /** The next `before` cursor, or null at the oldest edge. */
    fun nextBefore(requested: Long, page: ReplayPage): Long? {
        val next = page.nextCursor ?: return null
        if (!page.hasMore || next >= requested) return null
        return next
    }
}

object ReplayMapper {
    fun parsePage(json: String): ReplayPage {
        val root = JSONObject(json)
        val rowsArray = root.optJSONArray("rows")
        val rows = buildList {
            if (rowsArray != null) {
                for (index in 0 until rowsArray.length()) {
                    val row = rowsArray.optJSONObject(index) ?: continue
                    add(AcpFrameParser.parseRow(row))
                }
            }
        }
        return ReplayPage(
            rows = rows,
            lost = root.optBoolean("lost", false),
            highestSeq = root.optLong("highest_seq", 0L),
            lowestSeq = if (root.has("lowest_seq") && !root.isNull("lowest_seq")) {
                root.optLong("lowest_seq")
            } else {
                null
            },
            nextCursor = if (root.has("next_cursor") && !root.isNull("next_cursor")) {
                root.optLong("next_cursor")
            } else {
                null
            },
            hasMore = root.optBoolean("has_more", false),
        )
    }
}
