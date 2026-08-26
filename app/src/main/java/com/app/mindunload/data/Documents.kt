package com.app.mindunload.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Turns an attached document into something the model can actually read: plain text is
 * inlined as-is (no API call at all), a PDF is rendered to page images and goes through
 * the same OCR call as a photo, and an image file is simply passed on.
 *
 * Rendering the PDF locally instead of uploading it keeps the extraction working with
 * every model — only vision is required, which the OCR path needs for photos anyway.
 */
object Documents {

    /** How a document has to be handed to the model. */
    sealed interface Kind {
        /** Readable as text right here — [Text.content] is the whole extraction. */
        data class Text(val content: String) : Kind

        /** Needs the vision model: one base64 JPEG/PNG per page or per image. */
        data class Pages(val mediaType: String, val base64: List<String>, val truncated: Boolean) :
            Kind

        /** Nothing sensible to extract (archive, binary, unreadable). */
        data object Unsupported : Kind
    }

    /** Long edge of a rendered PDF page — matches the photo import, see [Attachments]. */
    private const val PAGE_EDGE = 1568

    /** Page ceiling: every page is an image in the request, so a 200-page PDF is not free. */
    private const val MAX_PAGES = 8

    /** Text cap; beyond this a document stops being a chat message. */
    private const val MAX_TEXT_CHARS = 100_000

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

    fun classify(file: File): Kind = when {
        !file.exists() || file.length() == 0L -> Kind.Unsupported
        Attachments.extension(file.absolutePath) == "pdf" -> renderPdf(file)
        Attachments.extension(file.absolutePath) in imageExtensions -> imagePage(file)
        else -> readText(file)?.let { Kind.Text(it) } ?: Kind.Unsupported
    }

    private fun imagePage(file: File): Kind = runCatching {
        val mediaType = when (Attachments.extension(file.absolutePath)) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> Attachments.IMAGE_MEDIA_TYPE
        }
        Kind.Pages(mediaType, listOf(base64(file.readBytes())), truncated = false)
    }.getOrDefault(Kind.Unsupported)

    /**
     * Reads the file as UTF-8 when it looks like text: NUL bytes or a high share of
     * control characters mean binary (a zip-based .docx, an archive), and pushing that
     * into the prompt would only burn tokens.
     */
    private fun readText(file: File): String? = runCatching {
        val head = file.inputStream().use { stream ->
            ByteArray(4096).let { buffer ->
                val read = stream.read(buffer)
                if (read <= 0) return null
                buffer.copyOf(read)
            }
        }
        if (head.any { it == 0.toByte() }) return null
        val decoded = String(head, Charsets.UTF_8)
        val suspicious =
            decoded.count { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }
        if (suspicious > decoded.length / 20) return null

        val text = file.readText(Charsets.UTF_8)
        if (text.isBlank()) null else text.take(MAX_TEXT_CHARS)
    }.getOrNull()

    private fun renderPdf(file: File): Kind = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val count = renderer.pageCount
                if (count == 0) return Kind.Unsupported
                val pages = (0 until minOf(count, MAX_PAGES)).map { index ->
                    renderer.openPage(index).use { page ->
                        val scale = PAGE_EDGE.toFloat() / maxOf(page.width, page.height)
                        val bitmap = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        )
                        // PDF pages render with a transparent background; as JPEG that
                        // would come out black and the OCR would see nothing.
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val bytes = ByteArrayOutputStream().also {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)
                        }.toByteArray()
                        bitmap.recycle()
                        base64(bytes)
                    }
                }
                Kind.Pages(Attachments.IMAGE_MEDIA_TYPE, pages, truncated = count > MAX_PAGES)
            }
        }
    }.getOrDefault(Kind.Unsupported)

    private fun base64(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
}
