package com.app.mindunload

import com.app.mindunload.data.Attachments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A document attachment carries its original name in the stored file name instead of a
 * database column, so the split has to survive whatever the picker hands over. The name
 * ends up both in the chat bubble and in the prompt sent to the model.
 */
class AttachmentNamingTest {

    private fun stored(name: String) =
        "/data/user/0/app/files/attachments/1712345678-ab12cd34__$name"

    @Test
    fun `the original name is recovered from the stored path`() {
        assertEquals("Rechnung.pdf", Attachments.fileName(stored("Rechnung.pdf")))
    }

    @Test
    fun `a name containing the separator does not truncate the rest`() {
        // sanitize() replaces the separator on import, so this can only come from an
        // older file — the whole remainder still has to come back.
        val path = stored("Kapitel__2.pdf")
        assertEquals("Kapitel__2.pdf", Attachments.fileName(path))
    }

    @Test
    fun `a file without the separator falls back to its own name`() {
        // Photos and voice messages use newFile(), which has no name part at all.
        assertEquals(
            "1712345678-ab12cd34.jpg",
            Attachments.fileName("/data/user/0/app/files/attachments/1712345678-ab12cd34.jpg"),
        )
    }

    @Test
    fun `extension is lowercased and without the dot`() {
        assertEquals("pdf", Attachments.extension(stored("Rechnung.PDF")))
        assertEquals("jpg", Attachments.extension(stored("foto.jpg")))
        assertEquals("", Attachments.extension(stored("LICENSE")))
    }

    @Test
    fun `sanitize keeps path separators and line breaks out of the file name`() {
        val cleaned = Attachments.sanitize("../../etc/pa\nss\twd")
        assertFalse('/' in cleaned)
        assertFalse('\\' in cleaned)
        assertFalse('\n' in cleaned)
        assertFalse('\t' in cleaned)
    }

    @Test
    fun `sanitize collapses the separator so the split stays unambiguous`() {
        val cleaned = Attachments.sanitize("Kapitel__2.pdf")
        assertFalse("__" in cleaned)
        // The name loses one underscore — the alternative is a name that splits itself.
        assertEquals("Kapitel_2.pdf", cleaned)
        assertEquals(cleaned, Attachments.fileName(stored(cleaned)))
    }

    @Test
    fun `an over-long name is capped but still round-trips`() {
        val long = "a".repeat(300) + ".pdf"
        val cleaned = Attachments.sanitize(long)
        assertTrue(cleaned.length <= 80)
        assertEquals(cleaned, Attachments.fileName(stored(cleaned)))
    }

    @Test
    fun `umlauts and spaces survive untouched`() {
        assertEquals("Steuererklärung 2025.pdf", Attachments.sanitize("Steuererklärung 2025.pdf"))
    }
}
