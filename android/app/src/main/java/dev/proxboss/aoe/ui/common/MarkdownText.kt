package dev.proxboss.aoe.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
            }
            when (block) {
                is MarkdownBlock.Code -> Text(
                    text = block.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(8.dp),
                )
                is MarkdownBlock.Heading -> Text(
                    text = inlineMarkdown(block.text),
                    color = color,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                is MarkdownBlock.Bullet -> Row {
                    Text("•  ", color = color)
                    Text(text = inlineMarkdown(block.text), color = color)
                }
                is MarkdownBlock.Paragraph -> Text(
                    text = inlineMarkdown(block.text),
                    color = color,
                )
            }
        }
    }
}

private fun parseMarkdown(text: String): List<MarkdownBlock> {
    val blocks = ArrayList<MarkdownBlock>()
    val paragraph = StringBuilder()
    val code = StringBuilder()
    var inCode = false

    fun flushParagraph() {
        if (paragraph.isNotBlank()) {
            blocks.add(MarkdownBlock.Paragraph(paragraph.toString().trim()))
            paragraph.clear()
        }
    }

    for (line in text.lines()) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (inCode) {
                blocks.add(MarkdownBlock.Code(code.toString().trimEnd()))
                code.clear()
                inCode = false
            } else {
                flushParagraph()
                inCode = true
            }
            continue
        }
        if (inCode) {
            code.appendLine(line)
            continue
        }
        when {
            trimmed.startsWith("# ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Heading(1, trimmed.removePrefix("# ")))
            }
            trimmed.startsWith("## ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Heading(2, trimmed.removePrefix("## ")))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Bullet(trimmed.drop(2)))
            }
            Regex("^\\d+\\.\\s+").containsMatchIn(trimmed) -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Bullet(trimmed.replaceFirst(Regex("^\\d+\\.\\s+"), "")))
            }
            trimmed.isBlank() -> flushParagraph()
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
            }
        }
    }
    if (inCode && code.isNotEmpty()) blocks.add(MarkdownBlock.Code(code.toString().trimEnd()))
    flushParagraph()
    return blocks
}

fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val pattern = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`|\\[[^\\]]+]\\([^)]+\\)|_[^_]+_)")
    var cursor = 0
    for (match in pattern.findAll(text)) {
        if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") && token.endsWith("**") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(token.substring(2, token.length - 2))
                pop()
            }
            token.startsWith("`") && token.endsWith("`") -> {
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0x33808080),
                        fontSize = 13.sp,
                    ),
                )
                append(token.substring(1, token.length - 1))
                pop()
            }
            token.startsWith("[") && token.contains("](") -> {
                val label = token.substring(1, token.indexOf("]("))
                pushStyle(
                    SpanStyle(
                        color = Color(0xFF7FB4FF),
                        textDecoration = TextDecoration.Underline,
                    ),
                )
                append(label)
                pop()
            }
            token.startsWith("_") && token.endsWith("_") -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(token.substring(1, token.length - 1))
                pop()
            }
            else -> append(token)
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}
