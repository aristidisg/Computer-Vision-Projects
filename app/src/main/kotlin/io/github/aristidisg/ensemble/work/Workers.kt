package io.github.aristidisg.ensemble.work

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aristidisg.ensemble.BuildConfig
import io.github.aristidisg.ensemble.data.files.Backup
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import io.github.aristidisg.ensemble.data.repo.WearRepository
import io.github.aristidisg.ensemble.domain.Studio
import io.github.aristidisg.ensemble.domain.TimeSource
import io.github.aristidisg.ensemble.domain.WeatherService
import io.github.aristidisg.ensemble.ui.nav.PiecesArg
import io.github.aristidisg.ensemble.widget.WidgetState
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Morning: refresh the forecast, pick today's top outfit, notify, update the widget. */
@HiltWorker
class MorningWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val studio: Studio,
    private val weather: WeatherService,
    private val settings: SettingsRepository,
    private val time: TimeSource,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        weather.refresh()
        val day = time.today()
        val sc = studio.scenario(studio.defaultScenarioId(day))
        val w = studio.dayWeather(day, sc)
        val (ctx, deck) = studio.deck(day, sc, 0, emptyList(), weatherOverride = w.window)
        val top = deck.cards.firstOrNull()
        if (top != null) WidgetState.save(applicationContext, top, ctx.items, w.window, sc.name)
        if (settings.current().morningNotification && top != null) {
            val dry = if (w.window.rainProbMax < 30) "dry" else "${w.window.rainProbMax.roundToInt()}% rain"
            val title = "${w.window.feelsMin.roundToInt()} → ${w.window.feelsMax.roundToInt()} °C, $dry"
            val body = "${sc.name}: " + top.pieces.filter { it.slot.isCore }.mapNotNull { ctx.items[it.itemId]?.name?.lowercase() }.joinToString(", ")
            Notifications.morning(applicationContext, title, body.replaceFirstChar { it.uppercase() }, "${sc.id};$day;${PiecesArg.encode(top.pieces)}")
        }
        return Result.success()
    }
}

/** Evening check-in, only on days you wore something and haven't checked in yet. */
@HiltWorker
class CheckInWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val wear: WearRepository,
    private val settings: SettingsRepository,
    private val time: TimeSource,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!settings.current().eveningCheckIn) return Result.success()
        val today = time.today()
        val log = wear.all().filter { it.epochDay == today }.lastOrNull() ?: return Result.success()
        if (wear.allCheckIns().any { it.epochDay == today && it.outfitSignature == log.outfitSignature }) return Result.success()
        Notifications.checkIn(applicationContext, log.id)
        return Result.success()
    }
}

/** Weekly backup zip into the folder the user picked (for example a Google Drive folder). */
@Singleton
class AutoBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: Backup,
    private val settings: SettingsRepository,
    private val time: TimeSource,
) {
    suspend fun run() {
        val tree = settings.current().autoBackupUri?.let(Uri::parse) ?: return
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = DocumentsContract.createDocument(context.contentResolver, parent, "application/zip", "ensemble-${LocalDate.now()}.zip")
            ?: error("Could not create the backup file")
        context.contentResolver.openOutputStream(doc)?.use { backup.export(it, BuildConfig.VERSION_NAME, time.nowMillis()) }
        settings.update { it.copy(autoBackupLastMillis = time.nowMillis()) }
    }

    fun schedule() = WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "auto-backup", ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build(),
    )

    fun cancel() = WorkManager.getInstance(context).cancelUniqueWork("auto-backup")
}

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val auto: AutoBackup,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { auto.run() }.fold({ Result.success() }, { Result.retry() })
}

/** Schedules the daily workers at the user's chosen times. */
@Singleton
class Scheduler @Inject constructor(@ApplicationContext private val context: Context, private val settings: SettingsRepository) {
    private fun delayUntil(minuteOfDay: Int): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMinutes()
    }

    suspend fun scheduleAll() {
        val s = settings.current()
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "morning", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<MorningWorker>(1, TimeUnit.DAYS).setInitialDelay(delayUntil(s.morningMinute), TimeUnit.MINUTES).build(),
        )
        wm.enqueueUniquePeriodicWork(
            "checkin", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CheckInWorker>(1, TimeUnit.DAYS).setInitialDelay(delayUntil(s.eveningMinute), TimeUnit.MINUTES).build(),
        )
        if (s.autoBackupUri != null) wm.enqueueUniquePeriodicWork(
            "auto-backup", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build(),
        )
    }
}
