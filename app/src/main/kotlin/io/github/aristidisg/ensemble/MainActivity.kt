package io.github.aristidisg.ensemble

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import dagger.hilt.android.AndroidEntryPoint
import io.github.aristidisg.ensemble.ui.nav.EnsembleNav
import io.github.aristidisg.ensemble.ui.theme.EnsembleTheme

/** Something the activity was asked to open: shared images or a notification target. */
sealed interface LaunchRequest {
    data class Shared(val uris: List<Uri>) : LaunchRequest
    data class Open(val target: String, val arg: String?) : LaunchRequest
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val launch = mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launch.value = parse(intent)
        setContent {
            EnsembleTheme {
                EnsembleNav(launch.value) { launch.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launch.value = parse(intent)
    }

    @Suppress("DEPRECATION")
    private fun parse(i: Intent?): LaunchRequest? = when (i?.action) {
        Intent.ACTION_SEND -> {
            val uri = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)
            uri?.let { LaunchRequest.Shared(listOf(it)) }
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            val uris = if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            uris?.takeIf { it.isNotEmpty() }?.let { LaunchRequest.Shared(it) }
        }
        else -> i?.getStringExtra(EXTRA_OPEN)?.let { LaunchRequest.Open(it, i.getStringExtra(EXTRA_ARG)) }
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val EXTRA_ARG = "arg"
        const val OPEN_TODAY = "today"
        const val OPEN_CHECKIN = "checkin"
    }
}
