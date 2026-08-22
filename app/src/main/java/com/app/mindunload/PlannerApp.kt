package com.app.mindunload

import android.app.Application
import com.app.mindunload.ai.AiService
import com.app.mindunload.ai.AiUsageLog
import com.app.mindunload.ai.Prompts
import com.app.mindunload.ai.ResearchService
import com.app.mindunload.ai.SettingsStore
import com.app.mindunload.ai.WikiService
import com.app.mindunload.data.ApiUsage
import com.app.mindunload.data.AppDatabase
import com.app.mindunload.data.PlannerRepository
import com.app.mindunload.reminders.BriefingScheduler
import com.app.mindunload.reminders.ReminderScheduler
import com.app.mindunload.work.CleanupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PlannerApp : Application() {
    /** For work that must survive screen navigation (e.g. an ongoing research call). */
    val appScope = CoroutineScope(SupervisorJob())

    val repository: PlannerRepository by lazy { PlannerRepository(AppDatabase.get(this)) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val prompts: Prompts by lazy { Prompts(this) }
    val aiService: AiService by lazy { AiService(settings, prompts) }
    val researchService: ResearchService by lazy { ResearchService(aiService, prompts) }
    val wikiService: WikiService by lazy { WikiService(aiService, prompts) }

    override fun onCreate() {
        super.onCreate()
        // Write every API call into the DB for the usage dashboard.
        AiUsageLog.recorder = { feature, model, input, cacheWrite, cacheRead, output ->
            appScope.launch(Dispatchers.IO) {
                repository.apiUsageDao.insert(
                    ApiUsage(
                        feature = feature,
                        model = model,
                        inputTokens = input,
                        cacheWriteTokens = cacheWrite,
                        cacheReadTokens = cacheRead,
                        outputTokens = output,
                    ),
                )
            }
        }
        // At process start nothing can legitimately be transcribing any more.
        appScope.launch(Dispatchers.IO) {
            repository.captureDao.failStaleTranscriptions("transcription_interrupted")
        }
        ReminderScheduler.ensureChannel(this)
        BriefingScheduler.ensureChannel(this)
        BriefingScheduler.scheduleNext(this)
        CleanupWorker.schedule(this)
    }
}
