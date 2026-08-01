package com.app.ai.planner

import android.app.Application
import com.app.ai.planner.ai.AiUsageLog
import com.app.ai.planner.ai.ClaudeService
import com.app.ai.planner.ai.Prompts
import com.app.ai.planner.ai.ResearchService
import com.app.ai.planner.ai.SettingsStore
import com.app.ai.planner.ai.WikiService
import com.app.ai.planner.data.ApiUsage
import com.app.ai.planner.data.AppDatabase
import com.app.ai.planner.data.PlannerRepository
import com.app.ai.planner.reminders.BriefingScheduler
import com.app.ai.planner.reminders.ReminderScheduler
import com.app.ai.planner.work.CleanupWorker
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
    val claudeService: ClaudeService by lazy { ClaudeService(settings, prompts) }
    val researchService: ResearchService by lazy { ResearchService(claudeService, prompts) }
    val wikiService: WikiService by lazy { WikiService(claudeService, prompts) }

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
