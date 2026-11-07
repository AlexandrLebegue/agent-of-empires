package dev.proxboss.aoe.data

import dev.proxboss.aoe.model.NotifyEvent
import dev.proxboss.aoe.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusFrameParserTest {
    @Test
    fun `parses ready and resync frames`() {
        assertEquals(StatusFrame.Ready, StatusFrameParser.parse("""{"kind":"ready"}"""))
        assertEquals(StatusFrame.Resync, StatusFrameParser.parse("""{"kind":"resync"}"""))
    }

    @Test
    fun `parses a status move with a decimal revision`() {
        val frame = StatusFrameParser.parse(
            """{"kind":"status","id":"abc","status":"Waiting","epoch":"boot","revision":"18446744073709551615"}""",
        )
        assertEquals(StatusFrame.Status("abc", SessionStatus.WAITING), frame)
    }

    @Test
    fun `rejects status frames without a numeric revision or a known status`() {
        assertEquals(
            StatusFrame.Unknown,
            StatusFrameParser.parse("""{"kind":"status","id":"abc","status":"Waiting","revision":"x"}"""),
        )
        assertEquals(
            StatusFrame.Unknown,
            StatusFrameParser.parse("""{"kind":"status","id":"abc","status":"Bogus","revision":"1"}"""),
        )
        assertEquals(
            StatusFrame.Unknown,
            StatusFrameParser.parse("""{"kind":"status","status":"Waiting","revision":"1"}"""),
        )
    }

    @Test
    fun `parses sound due cues and ignores unknown events`() {
        assertEquals(
            StatusFrame.SoundDue("abc", NotifyEvent.IDLE),
            StatusFrameParser.parse("""{"kind":"sound_due","id":"abc","event":"idle"}"""),
        )
        assertEquals(
            StatusFrame.Unknown,
            StatusFrameParser.parse("""{"kind":"sound_due","id":"abc","event":"later"}"""),
        )
    }

    @Test
    fun `malformed json never throws`() {
        assertTrue(StatusFrameParser.parse("not json") == StatusFrame.Unknown)
    }
}
