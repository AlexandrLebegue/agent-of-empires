package dev.proxboss.aoe.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueMapperTest {
    @Test
    fun `parses queued prompts with optional attachments`() {
        val rows = QueueMapper.parseQueue(
            """[
              {"id":"q1","seq":2,"text":"first","created_at":"now",
               "attachments":[{"id":"a1","kind":"image","mime_type":"image/png","name":"shot.png","size":42}]},
              {"id":"q2","seq":3,"text":"second","created_at":"later"}
            ]""",
        )
        assertEquals(2, rows.size)
        assertEquals("q1", rows[0].id)
        assertEquals(2L, rows[0].seq)
        assertEquals(1, rows[0].attachments.size)
        assertEquals("shot.png", rows[0].attachments[0].name)
        assertEquals(42L, rows[0].attachments[0].size)
        assertEquals(emptyList<Any>(), rows[1].attachments)
    }

    @Test
    fun `parses pasted image path and tolerates invalid bodies`() {
        assertEquals("/repo/.aoe-pasted-images/a.png", QueueMapper.parsePastedPath("""{"path":"/repo/.aoe-pasted-images/a.png"}"""))
        assertNull(QueueMapper.parsePastedPath("not json"))
        assertNull(QueueMapper.parsePastedPath("{}"))
    }
}
