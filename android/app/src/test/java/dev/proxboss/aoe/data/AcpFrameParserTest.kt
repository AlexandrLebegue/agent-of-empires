package dev.proxboss.aoe.data

import dev.proxboss.aoe.model.AcpControl
import dev.proxboss.aoe.model.TranscriptDelta
import dev.proxboss.aoe.model.TranscriptKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcpFrameParserTest {
    @Test
    fun `parses reduced state including config options approvals and startup errors`() {
        val json = """
            {
              "kind": "reduced_state",
              "session_id": "s1",
              "seq": 12,
              "state": {
                "agent": "opencode",
                "model": "opencode-go/deepseek",
                "mode": "Default",
                "pending_approvals": [
                  {
                    "nonce": "n1",
                    "tool_call": {
                      "id": "t1",
                      "name": "bash",
                      "kind": "execute",
                      "args_preview": "{\"command\":\"ls\"}",
                      "started_at": "2026-11-07T10:00:00Z"
                    },
                    "destructive": true,
                    "options": [
                      {"option_id": "o1", "name": "Allow", "kind": "allow_once"}
                    ],
                    "choice": false,
                    "plan": "steps"
                  }
                ],
                "pending_elicitations": [{"nonce": "e1"}],
                "turn_active": true,
                "cancelling": false,
                "startup_error": {"message": "boom"},
                "config_options": [
                  {
                    "id": "model",
                    "name": "Model",
                    "category": "model",
                    "current_value": "opencode-go/deepseek",
                    "options": [{"value": "opencode-go/deepseek", "name": "DeepSeek"}]
                  }
                ]
              },
              "unchanged": ["available_commands", "config_options"]
            }
        """.trimIndent()

        val frame = AcpFrameParser.parse(json) as AcpFrame.ReducedState
        val payload = frame.payload
        assertEquals("opencode", payload.agent)
        assertEquals("opencode-go/deepseek", payload.model)
        assertTrue(payload.turnActive == true)
        assertEquals("boom", payload.startupError)
        assertEquals(1, payload.pendingElicitationCount)
        assertEquals(setOf("available_commands", "config_options"), payload.unchanged)
        assertEquals(1, payload.pendingApprovals?.size)
        assertEquals("n1", payload.pendingApprovals?.first()?.nonce)
        assertTrue(payload.pendingApprovals?.first()?.destructive == true)
        assertEquals(1, payload.configOptions?.size)
        assertEquals("opencode-go/deepseek", payload.configOptions?.first()?.currentValue)
    }

    @Test
    fun `parses ACP modes and prefers a mode config option`() {
        val payload = """
            {"kind":"reduced_state","state":{"agent":"a","model":null,"mode":"Plan",
             "available_modes":[{"id":"build","name":"Build"},{"id":"plan","name":"Plan"}],
             "current_mode_id":"plan","pending_approvals":[],"pending_elicitations":[]}}
        """.trimIndent()
        val control = (AcpFrameParser.parse(payload) as AcpFrame.ReducedState).payload.mergeInto(AcpControl())
        assertEquals(listOf("build", "plan"), control.modeChoices.map { it.value })
        assertEquals("plan", control.currentModeChoice)

        val withConfig = """
            {"kind":"reduced_state","state":{"agent":"a","model":null,
             "config_options":[{"id":"mode","name":"Mode","category":"mode","current_value":"plan",
               "options":[{"value":"default","name":"Default"},{"value":"plan","name":"Plan"}]}],
             "pending_approvals":[],"pending_elicitations":[]}}
        """.trimIndent()
        val configured = (AcpFrameParser.parse(withConfig) as AcpFrame.ReducedState)
            .payload
            .mergeInto(AcpControl())
        assertEquals("mode", configured.modeOption?.id)
        assertEquals("plan", configured.currentModeChoice)
        assertEquals(2, configured.modeChoices.size)
    }

    @Test
    fun `omitted cold config options stay from the previous control state`() {
        val withOptions = """
            {"kind":"reduced_state","state":{"agent":"a","model":"m","config_options":[
              {"id":"effort","name":"Effort","category":"thought_level","current_value":"low","options":[]}
            ],"pending_approvals":[],"pending_elicitations":[],"turn_active":false}}
        """.trimIndent()
        val control = (AcpFrameParser.parse(withOptions) as AcpFrame.ReducedState)
            .payload
            .mergeInto(AcpControl())
        assertEquals(1, control.configOptions.size)

        val withoutOptions = """
            {"kind":"reduced_state","state":{"agent":"a","model":"m","pending_approvals":[],
            "pending_elicitations":[],"turn_active":true}}
        """.trimIndent()
        val merged = (AcpFrameParser.parse(withoutOptions) as AcpFrame.ReducedState)
            .payload
            .mergeInto(control)
        assertEquals(1, merged.configOptions.size)
        assertEquals("low", merged.configOptions.first().currentValue)
        assertTrue(merged.turnActive)
    }

    @Test
    fun `parses transcript snapshot rows and keeps only the tail`() {
        val builder = StringBuilder("""{"kind":"transcript_snapshot","seq":9,"rows":[""")
        for (index in 0 until 805) {
            if (index > 0) builder.append(',')
            builder.append("""{"id":"$index","group_id":"g","kind":"message","at":"2026-11-07T10:00:00Z","text":"line $index } \"quoted\""}""")
        }
        builder.append("]}")
        val frame = AcpFrameParser.parse(builder.toString()) as AcpFrame.TranscriptSnapshot
        assertEquals(800, frame.rows.size)
        assertEquals("5", frame.rows.first().id)
        assertEquals("804", frame.rows.last().id)
        assertEquals(TranscriptKind.MESSAGE, frame.rows.last().kind)
        assertTrue(frame.rows.last().text.contains("} \"quoted\""))
    }

    @Test
    fun `parses append patch and remove deltas`() {
        val append = AcpFrameParser.parse(
            """{"kind":"transcript_delta","delta":{"Append":{"id":"r1","group_id":"g","kind":"message","text":"hello"}}}""",
        ) as AcpFrame.Delta
        val appended = append.delta as TranscriptDelta.Append
        assertEquals("r1", appended.row.id)
        assertEquals("hello", appended.row.text)

        val patch = AcpFrameParser.parse(
            """{"kind":"transcript_delta","delta":{"Patch":{"id":"r1","row":{"id":"r1","group_id":"g","kind":"tool_error","text":"bad"}}}}""",
        ) as AcpFrame.Delta
        val patched = patch.delta as TranscriptDelta.Patch
        assertEquals("r1", patched.id)
        assertEquals(TranscriptKind.TOOL_ERROR, patched.row.kind)

        val remove = AcpFrameParser.parse(
            """{"kind":"transcript_delta","delta":{"Remove":"r1"}}""",
        ) as AcpFrame.Delta
        assertEquals(TranscriptDelta.Remove("r1"), remove.delta)
    }

    @Test
    fun `raw event frames and malformed payloads are ignored`() {
        assertNull(AcpFrameParser.parse("""{"session_id":"s","seq":1,"event":{"ThinkingStarted":{}}}"""))
        assertNull(AcpFrameParser.parse("[]"))
        assertNotNull(AcpFrameParser.parse("""{"kind":"heartbeat"}"""))
    }
}
