package com.app.mindunload.data

class PlannerRepository(private val db: AppDatabase) {
    val itemDao get() = db.itemDao()
    val linkDao get() = db.linkDao()
    val researchDao get() = db.researchDao()
    val captureDao get() = db.captureDao()
    val chatMessageDao get() = db.chatMessageDao()
    val apiUsageDao get() = db.apiUsageDao()

    suspend fun enqueueCapture(
        rawText: String,
        mode: ChatMode = ChatMode.CAPTURE,
        attachmentPath: String? = null,
        attachmentKind: AttachmentKind = AttachmentKind.NONE,
        status: CaptureStatus = CaptureStatus.PENDING,
    ): Long = captureDao.insert(
        CaptureRequest(
            rawText = rawText.trim(),
            mode = mode,
            attachmentPath = attachmentPath,
            attachmentKind = attachmentKind,
            status = status,
        ),
    )

    /** Undoes a capture batch: deletes items (links cascade) and the capture itself. */
    suspend fun undoCapture(captureId: Long) {
        itemDao.deleteByCapture(captureId)
        deleteCapture(captureId)
    }

    /** Deletes a capture together with its photo/voice message. */
    suspend fun deleteCapture(captureId: Long) {
        Attachments.delete(captureDao.byId(captureId)?.attachmentPath)
        captureDao.deleteById(captureId)
    }

    /** Clears the finished chat history; their attachment files go with it. */
    suspend fun clearFinishedCaptures(): Int {
        val paths = captureDao.finishedAttachmentPaths()
        val removed = captureDao.deleteFinished()
        paths.forEach { Attachments.delete(it) }
        return removed
    }
}
