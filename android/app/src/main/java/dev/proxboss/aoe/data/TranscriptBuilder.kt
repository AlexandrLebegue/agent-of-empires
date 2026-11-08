package dev.proxboss.aoe.data

import dev.proxboss.aoe.model.TranscriptDelta
import dev.proxboss.aoe.model.TranscriptKind
import dev.proxboss.aoe.model.TranscriptRow

class TranscriptBuilder(private val maxRows: Int = 5_000) {
    private val rows = ArrayList<TranscriptRow>()
    private val indexes = HashMap<String, Int>()
    private val optimistic = ArrayList<TranscriptRow>()

    fun snapshot(newRows: List<TranscriptRow>) {
        rows.clear()
        indexes.clear()
        newRows.forEach(::appendInternal)
        clearOptimisticIfPromptLanded()
    }

    fun prependOlder(older: List<TranscriptRow>) {
        val unique = older.filter { indexes[it.id] == null }
        if (unique.isEmpty()) return
        rows.addAll(0, unique)
        reindex()
        if (rows.size > maxRows) {
            repeat(rows.size - maxRows) { rows.removeAt(0) }
            reindex()
        }
    }

    fun append(row: TranscriptRow) {
        appendInternal(row)
        if (row.kind == TranscriptKind.USER_PROMPT || row.kind == TranscriptKind.USER_DIFF_COMMENTS) {
            optimistic.clear()
        }
    }

    fun patch(row: TranscriptRow) {
        val index = indexes[row.id]
        if (index == null) {
            appendInternal(row)
        } else if (index in rows.indices) {
            rows[index] = row
        }
    }

    fun remove(id: String) {
        val index = indexes.remove(id) ?: return
        if (index !in rows.indices) return
        rows.removeAt(index)
        indexes.clear()
        rows.forEachIndexed { rowIndex, row -> indexes[row.id] = rowIndex }
    }

    fun apply(delta: TranscriptDelta) {
        when (delta) {
            is TranscriptDelta.Append -> append(delta.row)
            is TranscriptDelta.Patch -> patch(delta.row)
            is TranscriptDelta.Remove -> remove(delta.id)
        }
    }

    fun addOptimistic(id: String, text: String) {
        optimistic.add(
            TranscriptRow(
                id = id,
                groupId = id,
                kind = TranscriptKind.USER_PROMPT,
                at = null,
                text = text,
                toolCallId = null,
                tool = null,
                outputText = "",
                attachmentNames = emptyList(),
                messageStart = false,
            ),
        )
    }

    fun clearOptimistic() {
        optimistic.clear()
    }

    fun rows(): List<TranscriptRow> = rows.toList()

    fun withOptimistic(): List<TranscriptRow> = rows + optimistic

    fun size(): Int = rows.size

    private fun reindex() {
        indexes.clear()
        rows.forEachIndexed { index, row -> indexes[row.id] = index }
    }

    private fun appendInternal(row: TranscriptRow) {
        val existing = indexes[row.id]
        if (existing != null && existing in rows.indices) {
            rows[existing] = row
            return
        }
        indexes[row.id] = rows.size
        rows.add(row)
        if (rows.size > maxRows) {
            val drop = rows.size - maxRows
            repeat(drop) { rows.removeAt(0) }
            reindex()
        }
    }

    private fun clearOptimisticIfPromptLanded() {
        if (rows.any { it.kind == TranscriptKind.USER_PROMPT }) optimistic.clear()
    }
}
