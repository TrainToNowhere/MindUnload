package com.app.mindunload.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import com.app.mindunload.data.Attachments.MAX_EDGE
import com.app.mindunload.data.Attachments.NAME_SEP
import java.io.File
import java.util.UUID

/**
 * Photos and voice messages of the chat live in the app's private storage — not in the
 * database: a blob column would bloat every chat query, and the files have to be readable
 * by the media player anyway. [CaptureRequest.attachmentPath] holds the absolute path.
 */
object Attachments {

    /** Long edge of a stored photo. Above this the model gains no detail, only tokens. */
    private const val MAX_EDGE = 1568

    private fun dir(context: Context): File =
        File(context.filesDir, "attachments").apply { mkdirs() }

    fun newFile(context: Context, extension: String): File =
        File(dir(context), "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.$extension")

    /**
     * Copies a picked photo into the app's storage — downscaled and re-encoded as JPEG.
     * The picker only grants a short-lived read permission on the original URI, so the
     * copy is what makes the image survive in the chat history at all.
     * Returns the absolute path, or null when the image could not be read.
     */
    fun importImage(context: Context, uri: Uri): String? = runCatching {
        val bitmap = decodeScaled(context, uri) ?: return null
        val target = newFile(context, "jpg")
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        target.absolutePath
    }.getOrNull()

    /**
     * Separates the generated unique prefix from the original file name of a document
     * attachment: the name is what the chat bubble shows and what the model is told the
     * file is called, so it has to survive the copy without a second database column.
     */
    private const val NAME_SEP = "__"

    /** Bigger documents are refused — base64 in a chat request, not a file upload. */
    private const val MAX_FILE_BYTES = 20L * 1024 * 1024

    /**
     * Copies a picked document into the app's storage, keeping its original name after
     * the unique prefix (see [NAME_SEP]). Unlike a photo the bytes stay untouched — the
     * extraction later on decides what to do with them.
     * Returns the absolute path, null when the file was unreadable or too big.
     */
    fun importFile(context: Context, uri: Uri): String? = runCatching {
        val name = displayNameOf(context, uri) ?: "document"
        val target = File(
            dir(context),
            "${System.currentTimeMillis()}-${
                UUID.randomUUID().toString().take(8)
            }$NAME_SEP${sanitize(name)}"
        )
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                val copied = input.copyTo(output)
                if (copied > MAX_FILE_BYTES) {
                    target.delete()
                    return null
                }
            }
        } ?: return null
        if (target.length() == 0L) {
            target.delete()
            return null
        }
        target.absolutePath
    }.getOrNull()

    /** Original file name of a document attachment, for the UI and the model prompt. */
    fun fileName(path: String): String =
        File(path).name.substringAfter(NAME_SEP, File(path).name)

    /** Extension of a stored attachment, lowercase and without the dot. */
    fun extension(path: String): String = File(path).extension.lowercase()

    internal fun sanitize(name: String): String =
        name.replace(Regex("""[/\\\n\r\t]"""), "_").replace(NAME_SEP, "_").take(80)

    private fun displayNameOf(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    /** Media type of a stored attachment for the image content block. */
    const val IMAGE_MEDIA_TYPE = "image/jpeg"

    fun delete(path: String?) {
        path?.let { runCatching { File(it).delete() } }
    }

    /**
     * Decodes at most [MAX_EDGE] on the long edge — full-resolution phone photos
     * (12 MP and up) would otherwise blow the heap before they are ever scaled down.
     * The EXIF orientation is applied, otherwise portrait shots reach the OCR sideways.
     */
    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val longEdge = maxOf(decoded.width, decoded.height)
        val scaled = if (longEdge > MAX_EDGE) {
            val factor = MAX_EDGE.toFloat() / longEdge
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * factor).toInt().coerceAtLeast(1),
                (decoded.height * factor).toInt().coerceAtLeast(1),
                true,
            ).also { if (it != decoded) decoded.recycle() }
        } else {
            decoded
        }
        return applyExifRotation(context, uri, scaled)
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap
        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(degrees) }, true,
        )
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}
