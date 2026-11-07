package dev.proxboss.aoe.data

import org.json.JSONArray
import org.json.JSONObject

data class DiffFile(
    val path: String,
    val oldPath: String?,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val repoName: String?,
)

data class DiffFilesResult(
    val files: List<DiffFile>,
    val warning: String?,
)

data class DiffFileContent(
    val path: String,
    val patch: String,
    val isBinary: Boolean,
    val truncated: Boolean,
)

object DiffMapper {
    fun parseFiles(json: String): DiffFilesResult {
        val root = JSONObject(json)
        val array = root.optJSONArray("files") ?: JSONArray()
        val files = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    DiffFile(
                        path = item.stringOrEmpty("path"),
                        oldPath = item.stringOrNull("old_path"),
                        status = item.stringOrEmpty("status"),
                        additions = item.optInt("additions", 0),
                        deletions = item.optInt("deletions", 0),
                        repoName = item.stringOrNull("repo_name"),
                    ),
                )
            }
        }
        return DiffFilesResult(files, root.stringOrNull("warning"))
    }

    fun parseFile(json: String): DiffFileContent {
        val root = JSONObject(json)
        val file = root.optJSONObject("file")
        return DiffFileContent(
            path = file?.stringOrEmpty("path").orEmpty(),
            patch = root.stringOrEmpty("patch"),
            isBinary = root.optBoolean("is_binary", false),
            truncated = root.optBoolean("truncated", false),
        )
    }
}
