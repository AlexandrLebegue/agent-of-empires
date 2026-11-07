package dev.proxboss.aoe.data

import dev.proxboss.aoe.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMapperTest {
    @Test
    fun `maps the sessions envelope and all row fields used by the list`() {
        val json = """
            {
              "sessions": [
                {
                  "id": "abc123",
                  "title": "Fix login",
                  "project_path": "/work/api",
                  "group_path": "work/api",
                  "tool": "claude",
                  "agent_name": "claude",
                  "agent_model": "claude-sonnet",
                  "status": "Waiting",
                  "dormant": false,
                  "favorited": true,
                  "pinned_at": "2026-11-07T10:00:00Z",
                  "unread": true,
                  "notify_on_waiting": false,
                  "notify_on_idle": null,
                  "notify_on_error": true,
                  "view": "structured",
                  "acp_capable": true,
                  "acp_worker_state": "running",
                  "has_terminal": false,
                  "last_accessed_at": "2026-11-07T10:30:00+00:00",
                  "created_at": "2026-11-06T09:00:00Z",
                  "branch": "feat/login",
                  "last_error": null
                },
                {
                  "id": "def456",
                  "title": "",
                  "project_path": "/work/ui",
                  "group_path": "",
                  "tool": "codex",
                  "acp_agent": "codex",
                  "status": "NotAStatus"
                }
              ],
              "workspace_ordering": ["/work/api::main", "/work/ui::main"]
            }
        """.trimIndent()

        val envelope = SessionMapper.parseEnvelope(json)

        assertEquals(listOf("/work/api::main", "/work/ui::main"), envelope.workspaceOrdering)
        assertEquals(2, envelope.sessions.size)

        val first = envelope.sessions[0]
        assertEquals("abc123", first.id)
        assertEquals("Fix login", first.title)
        assertEquals("work/api", first.groupPath)
        assertEquals("claude", first.displayAgent)
        assertEquals("claude-sonnet", first.model)
        assertEquals(SessionStatus.WAITING, first.status)
        assertTrue(first.favorited)
        assertTrue(first.isPinned)
        assertTrue(first.unread)
        assertEquals(false, first.notifyOnWaiting)
        assertNull(first.notifyOnIdle)
        assertEquals(true, first.notifyOnError)
        assertTrue(first.isStructured)
        assertFalse(first.hasTerminal)
        assertEquals("feat/login", first.branch)

        val second = envelope.sessions[1]
        assertEquals("Sans titre", second.title)
        assertEquals(SessionStatus.UNKNOWN, second.status)
        assertEquals("codex", second.displayAgent)
        assertFalse(second.isStructured)
    }

    @Test
    fun `parses about and conversation search`() {
        val about = SessionMapper.parseAbout(
            """{"version":"1.20.0","auth_required":true,"auth_mode":"token","read_only":false,"profile":"main"}""",
        )
        assertEquals("1.20.0", about.version)
        assertTrue(about.authRequired)
        assertEquals("token", about.authMode)
        assertEquals("main", about.profile)

        val hits = SessionMapper.parseSearch(
            """{"results":[{"session_id":"s1","seq":42,"kind":"tool","snippet":"needle","match_count":3}]}""",
        )
        assertEquals(1, hits.size)
        assertEquals("s1", hits[0].sessionId)
        assertEquals(42L, hits[0].seq)
        assertEquals(3, hits[0].matchCount)
    }

    @Test
    fun `missing envelope fields stay usable`() {
        val envelope = SessionMapper.parseEnvelope("""{"sessions":[]}""")
        assertTrue(envelope.sessions.isEmpty())
        assertTrue(envelope.workspaceOrdering.isEmpty())
    }
}
