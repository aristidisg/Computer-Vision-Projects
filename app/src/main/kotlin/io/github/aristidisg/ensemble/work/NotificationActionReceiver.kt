package io.github.aristidisg.ensemble.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.aristidisg.ensemble.domain.FeedbackRecorder
import io.github.aristidisg.ensemble.domain.Studio
import io.github.aristidisg.ensemble.ui.nav.PiecesArg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Wear it" from the morning notification: logs the wear without opening the app. */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var studio: Studio
    @Inject lateinit var recorder: FeedbackRecorder

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WEAR) return
        val payload = intent.getStringExtra(EXTRA_PAYLOAD) ?: return
        val parts = payload.split(';')
        if (parts.size < 3) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val sc = studio.scenario(parts[0])
                val day = parts[1].toLong()
                recorder.wear(PiecesArg.decode(parts[2]), studio.context(day, sc))
                Notifications.cancel(context, Notifications.ID_MORNING)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_WEAR = "io.github.aristidisg.ensemble.WEAR"
        const val EXTRA_PAYLOAD = "payload"
    }
}
