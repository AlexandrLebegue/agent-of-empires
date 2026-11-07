package dev.proxboss.aoe.data

/**
 * Extracts the trailing object items of a large JSON array without parsing the
 * whole document, so a multi-megabyte transcript snapshot stays bounded on a
 * phone. Only used for the `rows` field of ACP snapshots.
 */
object JsonTail {
    fun objectStrings(json: String, field: String, maxItems: Int): List<String> {
        if (maxItems <= 0) return emptyList()
        val marker = "\"$field\""
        var searchFrom = 0
        while (true) {
            val keyIndex = json.indexOf(marker, searchFrom)
            if (keyIndex < 0) return emptyList()
            var cursor = keyIndex + marker.length
            while (cursor < json.length && json[cursor].isWhitespace()) cursor++
            if (cursor >= json.length || json[cursor] != ':') {
                searchFrom = keyIndex + marker.length
                continue
            }
            cursor++
            while (cursor < json.length && json[cursor].isWhitespace()) cursor++
            if (cursor >= json.length || json[cursor] != '[') return emptyList()
            return objectsInArray(json, cursor + 1, maxItems)
        }
    }

    private fun objectsInArray(json: String, start: Int, maxItems: Int): List<String> {
        val kept = ArrayDeque<String>()
        var index = start
        while (index < json.length) {
            when (json[index]) {
                '{' -> {
                    val end = scanObject(json, index) ?: return kept.toList()
                    kept.addLast(json.substring(index, end))
                    if (kept.size > maxItems) kept.removeFirst()
                    index = end
                }
                ']' -> return kept.toList()
                else -> index++
            }
        }
        return kept.toList()
    }

    private fun scanObject(json: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until json.length) {
            val char = json[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index + 1
                }
            }
        }
        return null
    }
}
