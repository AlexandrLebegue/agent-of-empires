package dev.proxboss.aoe.util

object Ansi {
    private val csi = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
    private val osc = Regex("\u001B\\][^\u0007]*(?:\u0007|\u001B\\\\)")
    private val otherEscapes = Regex("\u001B[@-_]")

    fun strip(text: String): String {
        return text
            .replace(osc, "")
            .replace(csi, "")
            .replace(otherEscapes, "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .filter { char ->
                char == '\n' || char == '\t' || char.code >= 0x20
            }
    }

    fun tail(text: String, maxLines: Int): String {
        if (maxLines <= 0) return ""
        val lines = text.lines()
        if (lines.size <= maxLines) return text
        return lines.takeLast(maxLines).joinToString("\n")
    }
}
