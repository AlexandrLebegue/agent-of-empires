package dev.proxboss.aoe.data

import dev.proxboss.aoe.model.TranscriptDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayBackfillTest {
    private fun page(rows: String, cursor: Long, hasMore: Boolean, highest: Long = 10) =
        ReplayMapper.parsePage(
            """{"frames":[],"rows":[$rows],"lost":false,"highest_seq":$highest,"lowest_seq":1,
                "next_cursor":$cursor,"has_more":$hasMore}""",
        )

    private fun row(id: String, text: String, seqLabel: String = "") =
        """{"id":"$id","group_id":"g1","kind":"message","at":"2026-11-08T22:36:55Z","text":"$text$seqLabel"}"""

    @Test
    fun `pages chain their cursor until has_more is false`() {
        val tail = page("${row("m4", "quatre")},${row("m5", "cinq")}", cursor = 100, hasMore = true)
        val middle = page("${row("m2", "deux")},${row("m3", "trois")}", cursor = 50, hasMore = true)
        val oldest = page("${row("m0", "zero")},${row("m1", "un")}", cursor = 1, hasMore = false)

        assertEquals(100L, ReplayPagination.nextBefore(Long.MAX_VALUE, tail))
        assertEquals(50L, ReplayPagination.nextBefore(100, middle))
        assertNull(ReplayPagination.nextBefore(50, oldest))
        assertFalse(oldest.hasMore)
        assertEquals(2, middle.rows.size)
    }

    @Test
    fun `backfill pages merge oldest first and live rows do not duplicate`() {
        val builder = TranscriptBuilder()
        val tail = page("${row("m2", "deux")},${row("m3", "trois")}", cursor = 10, hasMore = true)
        val older = page("${row("m0", "zero")},${row("m1", "un")}", cursor = 1, hasMore = false)

        builder.snapshot(tail.rows)
        builder.prependOlder(older.rows)
        assertEquals(listOf("m0", "m1", "m2", "m3"), builder.rows().map { it.id })

        builder.apply(dev.proxboss.aoe.model.TranscriptDelta.Append(tail.rows[0].copy(text = "deux corrigé")))
        builder.apply(dev.proxboss.aoe.model.TranscriptDelta.Append(
            dev.proxboss.aoe.model.TranscriptRow(
                id = "m4",
                groupId = "g1",
                kind = dev.proxboss.aoe.model.TranscriptKind.MESSAGE,
                at = null,
                text = "quatre",
                toolCallId = null,
                tool = null,
                outputText = "",
                attachmentNames = emptyList(),
                messageStart = false,
            ),
        ))

        assertEquals(listOf("m0", "m1", "m2", "m3", "m4"), builder.rows().map { it.id })
        assertEquals("deux corrigé", builder.rows().first { it.id == "m2" }.text)
        assertEquals(builder.rows().size, builder.rows().map { it.id }.distinct().size)
    }

    @Test
    fun `an idle session with replayed history is not empty`() {
        val tail = page(
            """{"id":"msg-74269","group_id":"g1","kind":"message","at":"2026-11-08T22:36:55Z","text":"dernier seq connu"},
               {"id":"msg-74270","group_id":"g1","kind":"message","at":"2026-11-08T22:36:55Z","text":"."}""",
            cursor = 74269,
            hasMore = true,
            highest = 74271,
        )
        val display = TranscriptDisplay.build(tail.rows)
        assertTrue(display.isNotEmpty())
        assertEquals("dernier seq connu.", (display.first() as dev.proxboss.aoe.model.MessageDisplay).text)
    }

    @Test
    fun `oversized live frames are ignored and snapshots are never used for live history`() {
        val huge = """{"kind":"transcript_snapshot","rows":[]}""" + "x".repeat(AcpFrameParser.MAX_WS_FRAME_CHARS)
        assertNull(AcpFrameParser.parseLive(huge))
        assertNull(AcpFrameParser.parseLive("""{"kind":"transcript_snapshot","rows":[]}"""))
        assertNotNull(AcpFrameParser.parse("""{"kind":"heartbeat"}"""))
    }

    @Test
    fun `delta seq is carried for gap detection`() {
        val frame = AcpFrameParser.parse(
            """{"kind":"transcript_delta","seq":4242,
                "delta":{"Append":{"id":"r1","group_id":"g","kind":"message","text":"hello"}}}""",
        ) as AcpFrame.Delta
        assertEquals(4242L, frame.seq)
    }
}
