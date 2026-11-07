package dev.proxboss.aoe.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffMapperTest {
    @Test
    fun `parses files with repository bases`() {
        val result = DiffMapper.parseFiles(
            """{
              "files":[{"path":"src/a.kt","status":"modified","additions":2,"deletions":1,"repo_name":"app"}],
              "per_repo_bases":[{"repo_name":"app","base_branch":"main","repo_path":"/repo/app",
                                 "base_override":"origin/dev"}],
              "warning":null
            }""",
        )
        assertEquals(1, result.files.size)
        assertEquals(2, result.files[0].additions)
        assertEquals("app", result.files[0].repoName)
        assertEquals(1, result.bases.size)
        assertEquals("main", result.bases[0].baseBranch)
        assertEquals("origin/dev", result.bases[0].baseOverride)
        assertNull(result.warning)
    }

    @Test
    fun `parses a workspace file and truncation flags`() {
        val file = DiffMapper.parseSessionFile(
            """{"content":"hello","is_binary":false,"truncated":true}""",
        )
        assertEquals("hello", file.content)
        assertTrue(file.truncated)
    }
}
