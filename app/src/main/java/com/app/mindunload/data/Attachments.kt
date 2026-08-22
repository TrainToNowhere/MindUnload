package com.app.mindunload.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.graphics.Matrix
import android.net.Uri
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
