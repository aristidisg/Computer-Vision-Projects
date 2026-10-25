package io.github.aristidisg.ensemble

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import io.github.aristidisg.ensemble.domain.WardrobeCleaner
import io.github.aristidisg.ensemble.engine.ItemFactory
import io.github.aristidisg.ensemble.work.Notifications
import io.github.aristidisg.ensemble.work.Scheduler
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class EnsembleApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scheduler: Scheduler
    @Inject lateinit var cleaner: WardrobeCleaner
    @Inject lateinit var settings: SettingsRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            scheduler.scheduleAll()
            // One-time fix of stored color names when the naming rules change.
            if (settings.current().colorNamesVersion < ItemFactory.COLOR_NAMES_VERSION) {
                runCatching { cleaner.repairColorNames() }
                    .onSuccess { settings.update { it.copy(colorNamesVersion = ItemFactory.COLOR_NAMES_VERSION) } }
            }
        }
    }
}
