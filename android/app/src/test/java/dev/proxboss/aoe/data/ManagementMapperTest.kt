package dev.proxboss.aoe.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagementMapperTest {
    @Test
    fun `parses MCP servers and conflicts`() {
        val view = ManagementMapper.parseMcpServers(
            """{"agent":"claude",
                "effective":[{"name":"fs","transport":"stdio","command":"npx","args":["-y","server"],
                              "envNames":["TOKEN"],"provenance":"aoe"}],
                "keptOnRemoval":[],"conflicts":[
                  {"name":"fs","agent":"claude","previous":"a","current":"b","fingerprint":"fp"}],
                "driftPaused":true}""",
        )
        assertEquals("claude", view.agent)
        assertEquals(1, view.effective.size)
        assertEquals(listOf("-y", "server"), view.effective[0].args)
        assertEquals("TOKEN", view.effective[0].envNames.single())
        assertEquals("fp", view.conflicts.single().fingerprint)
        assertTrue(view.driftPaused)
    }

    @Test
    fun `parses skills plugins profiles devices and health`() {
        val skills = ManagementMapper.parseSkills(
            """{"skills":[{"directory":"review","name":"Review","description":"d",
                          "provenance":{"kind":"external","root":"claude-user"},
                          "provenanceLabel":"externe","writable":false}],
                "roots":[{"id":"claude-user","label":"Claude","relativePath":".claude/skills",
                          "consumers":["claude"],"legacy":false}]}""",
        )
        assertEquals("external:claude-user", skills.skills.single().source)
        assertFalse(skills.skills.single().writable)
        assertEquals("Claude", skills.roots.single().label)

        val plugins = ManagementMapper.parsePlugins(
            """{"plugins":[{"id":"aoe.web","name":"Web","version":"1","description":"d",
                           "enabled":true,"builtin":true,"validation":"builtin",
                           "capabilities":[],"granted":true,"needs_reapproval":false}],
                "load_errors":["bad"]}""",
        )
        assertTrue(plugins.plugins.single().enabled)
        assertEquals("bad", plugins.loadErrors.single())

        val profiles = ManagementMapper.parseProfiles("""[{"name":"main","is_default":true}]""")
        assertTrue(profiles.single().isDefault)

        val devices = ManagementMapper.parseDevices(
            """[{"session_id":"s1","user_agent":"UA","created_ip":"1.2.3.4",
                 "created_at":"a","last_seen":"b","current":true}]""",
        )
        assertTrue(devices.single().current)

        val health = ManagementMapper.parseHealth(
            """{"status":"ok","cpu_fraction":0.5,"memory_used_bytes":10,"memory_total_bytes":20,
                "load_average":[1.0,2.0,3.0],"swap_used_bytes":0,"swap_total_bytes":0,
                "agent_count":1,"proc_count":2,
                "agents":[{"id":"a","title":"Agent","cpu_fraction":null,"memory_bytes":5,
                           "procs":1,"sandboxed":false}]}""",
        )
        assertEquals("ok", health.status)
        assertEquals(3, health.loadAverage.size)
        assertNull(health.agents.single().cpuFraction)
        assertEquals(5L, health.agents.single().memoryBytes)
    }

    @Test
    fun `parses logs and update status`() {
        val logs = ManagementMapper.parseLogs(
            """{"status":"available","content":"line","line_count":1,"truncated":false}""",
        )
        assertEquals("available", logs.status)
        assertEquals(1, logs.lineCount)

        val update = ManagementMapper.parseUpdateStatus(
            """{"update_check_mode":"notify","current_version":"1.0.0","latest_version":"1.1.0",
                "update_available":true,"release_url":"https://example.test","error":null,
                "install_method":"binary","update_command":"aoe update"}""",
        )
        assertTrue(update.updateAvailable)
        assertEquals("aoe update", update.updateCommand)
    }
}
