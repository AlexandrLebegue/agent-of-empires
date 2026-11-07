package dev.proxboss.aoe.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreationMapperTest {
    @Test
    fun `parses agents from old and current daemons`() {
        val agents = CreationMapper.parseAgents(
            """[
              {"kind":"builtin","name":"opencode","display_name":"OpenCode","installed":true,
               "acp_capable":true,"acp_installed":true,"acp_allowed":true},
              {"kind":"builtin","name":"claude","binary":"claude","installed":true,
               "acp_capable":true,"acp_installed":false,"acp_allowed":false}
            ]""",
        )
        assertEquals(2, agents.size)
        assertEquals("OpenCode", agents[0].displayName)
        assertTrue(agents[0].canRunStructured)
        assertEquals("claude", agents[1].displayName)
        assertFalse(agents[1].canRunStructured)
    }

    @Test
    fun `parses browse branches groups and recent projects`() {
        val browse = CreationMapper.parseBrowse(
            """{"entries":[{"name":"repo","path":"/home/dev/repo","is_dir":true,"is_git_repo":true}],"has_more":true}""",
        )
        assertTrue(browse.hasMore)
        assertEquals("/home/dev/repo", browse.entries.single().path)
        assertTrue(browse.entries.single().isGitRepo)

        val branches = CreationMapper.parseBranches(
            """[{"name":"main","is_current":true},{"name":"dev","is_current":false,"remote_only":true}]""",
        )
        assertTrue(branches[0].isCurrent)
        assertTrue(branches[1].remoteOnly)

        val groups = CreationMapper.parseGroups("""[{"path":"work/api","session_count":2}]""")
        assertEquals("work/api", groups.single().path)
        assertEquals(2, groups.single().sessionCount)

        val recent = CreationMapper.parseRecentProjects(
            """{"projects":[{"path":"/home/dev/app","display_name":"app","tool":"opencode"}]}""",
        )
        assertEquals("opencode", recent.single().tool)
    }

    @Test
    fun `parses creation trust including a create refusal payload`() {
        val review = CreationMapper.parseTrust(
            """{
              "fingerprint":{"project_path":"/repo","base_hooks_hash":"b","hooks_hash":"h","mcp_hash":null},
              "merged_hooks":{"on_create":["npm ci"],"on_launch":[]},
              "repo_hooks":{"on_create":["npm ci"]},
              "mcp_summaries":["server: fs"],
              "hooks_need_trust":true,
              "mcp_need_trust":true
            }""",
        )
        assertEquals("/repo", review.fingerprint?.projectPath)
        assertEquals(listOf("npm ci"), review.mergedHooks.onCreate)
        assertTrue(review.requiresTrust)

        val refusal = CreationMapper.parseTrustRequired(
            org.json.JSONObject(
                """{"error":"hooks_need_trust","on_create":["make"],"needs_mcp_trust":true,
                    "mcp_summaries":["server: git"]}""",
            ),
        )
        assertNull(refusal.fingerprint)
        assertEquals(listOf("make"), refusal.mergedHooks.onCreate)
        assertTrue(refusal.mcpNeedTrust)
    }

    @Test
    fun `parses progress and option catalog`() {
        val progress = CreationMapper.parseProgress(
            """{"stage":"running_hooks","hook":"npm ci","output":["one","two"]}""",
        )
        assertEquals("running_hooks", progress.stage)
        assertEquals("npm ci", progress.hook)
        assertEquals(listOf("one", "two"), progress.output)

        val catalog = CreationMapper.parseOptionCatalog(
            """{"version":1,"agents":{"opencode":{"options":[
              {"id":"model","name":"Model","category":"model","current_value":"m",
               "options":[{"value":"m","name":"M"}]}
            ]}}}""",
        )
        assertEquals("m", catalog["opencode"]?.single()?.currentValue)
    }
}
