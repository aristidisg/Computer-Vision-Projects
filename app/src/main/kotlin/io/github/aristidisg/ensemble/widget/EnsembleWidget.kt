package io.github.aristidisg.ensemble.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.aristidisg.ensemble.MainActivity
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.WindowWeather
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToInt

/** What the widget shows, written by the morning worker and the Today tab. */
@Serializable
data class WidgetData(val scenario: String, val feelsMin: Int, val feelsMax: Int, val rain: Int, val pieces: List<Pair<String, String>>, val match: Int)

object WidgetState {
    private val json = Json { ignoreUnknownKeys = true }
    private fun file(c: Context) = File(c.filesDir, "widget.json")

    fun save(c: Context, top: ScoredOutfit, items: Map<String, Item>, w: WindowWeather, scenario: String) {
        val data = WidgetData(
            scenario, w.feelsMin.roundToInt(), w.feelsMax.roundToInt(), w.rainProbMax.roundToInt(),
            top.pieces.filter { it.slot.isCore }.mapNotNull { p -> items[p.itemId]?.let { it.name to (it.primaryColor?.hex ?: "#808080") } },
            top.matchPct,
        )
        runCatching { file(c).writeText(json.encodeToString(WidgetData.serializer(), data)) }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching {
                val ids = GlanceAppWidgetManager(c).getGlanceIds(EnsembleWidget::class.java)
                ids.forEach { EnsembleWidget().update(c, it) }
            }
        }
    }

    fun load(c: Context): WidgetData? = runCatching { json.decodeFromString(WidgetData.serializer(), file(c).readText()) }.getOrNull()
}

/** Glance widget: today's top pick and the forecast; tap to open. */
class EnsembleWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetState.load(context)
        val empty = context.getString(R.string.widget_empty)
        provideContent { Content(data, empty) }
    }

    @Composable
    private fun Content(d: WidgetData?, empty: String) {
        val ink = ColorProvider(Color(0xFF111111))
        Column(
            GlanceModifier.fillMaxSize().background(Color(0xFFF5F5F4)).cornerRadius(20.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            if (d == null) { Text(empty, style = TextStyle(color = ink)); return@Column }
            Text("${d.feelsMin} → ${d.feelsMax} °C · ${d.rain}% rain", style = TextStyle(color = ink, fontWeight = FontWeight.Bold, fontSize = 15.sp))
            Text("${d.scenario} · ${d.match}%", style = TextStyle(color = ColorProvider(Color(0xFF555555)), fontSize = 12.sp))
            Spacer(GlanceModifier.height(8.dp))
            d.pieces.forEach { (name, hex) ->
                Row(GlanceModifier.padding(vertical = 2.dp)) {
                    val c = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
                    Spacer(GlanceModifier.size(12.dp).background(c).cornerRadius(6.dp))
                    Spacer(GlanceModifier.size(8.dp))
                    Text(name, style = TextStyle(color = ink, fontSize = 13.sp), maxLines = 1)
                }
            }
        }
    }
}

class EnsembleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EnsembleWidget()
}
