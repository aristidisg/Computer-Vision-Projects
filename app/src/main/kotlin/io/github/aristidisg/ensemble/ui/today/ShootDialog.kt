package io.github.aristidisg.ensemble.ui.today

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.engine.tables.Issue
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.ui.common.Collage
import io.github.aristidisg.ensemble.ui.common.coverLineText
import io.github.aristidisg.ensemble.ui.common.hexColor
import io.github.aristidisg.ensemble.ui.common.issueName
import io.github.aristidisg.ensemble.ui.common.techniqueName
import io.github.aristidisg.ensemble.ui.common.tipText
import io.github.aristidisg.ensemble.ui.theme.Fraunces
import io.github.aristidisg.ensemble.ui.theme.LocalEnsembleColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ink = Color(0xFF111111)
private val Ivory = Color(0xFFF4EFE6)

/** Shoot mode: the cover look as a magazine cover, saved to Pictures/Ensemble (or shared). */
@Composable
fun ShootDialog(card: ScoredOutfit, items: Map<String, Item>, issue: Issue?, epochDay: Long, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val accent = LocalEnsembleColors.current.accent
    val placed = card.pieces.mapNotNull { p -> items[p.itemId]?.let { it to p.slot } }
    val palette = placed.mapNotNull { (it, _) -> it.colors.firstOrNull()?.let { c -> hexColor(c.hex) } }.distinct()
    val saved = stringResource(R.string.shoot_saved)
    val failed = stringResource(R.string.shoot_failed)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Ink).padding(16.dp), verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(0.8f)
                    .drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }
                    .background(Ink)
                    .background(Brush.verticalGradient(listOf((palette.getOrNull(0) ?: accent).copy(alpha = 0.45f), Ink))),
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text(stringResource(R.string.shoot_masthead), color = Ivory, fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, letterSpacing = 6.sp, maxLines = 1)
                    Row(Modifier.fillMaxWidth()) {
                        Text((issue?.let { issueName(it) } ?: techniqueName(card.technique)).uppercase(), color = accent, style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp), modifier = Modifier.weight(1f))
                        Text(LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)).uppercase(), color = Ivory, style = MaterialTheme.typography.labelSmall)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp)) { Collage(placed, Modifier.fillMaxSize(), software = true) }
                    Text(techniqueName(card.technique), color = Ivory, fontFamily = Fraunces, fontSize = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    card.cover?.notes.orEmpty().take(2).forEach { n -> Text("— " + coverLineText(n), color = Ivory, style = MaterialTheme.typography.labelMedium, maxLines = 2) }
                    card.tips.take(1).forEach { t -> Text("— " + tipText(t), color = accent, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Ivory) }
                Button(
                    onClick = {
                        scope.launch {
                            val ok = runCatching { saveCover(ctx, layer.toImageBitmap().asAndroidBitmap()) }.getOrDefault(false)
                            Toast.makeText(ctx, if (ok) saved else failed, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = LocalEnsembleColors.current.onAccent),
                ) { Text(stringResource(R.string.shoot_save)) }
            }
        }
    }
}

/** Writes the cover as a PNG to Pictures/Ensemble (Android 10+); older phones get the share sheet. */
private suspend fun saveCover(ctx: Context, image: Bitmap): Boolean = withContext(Dispatchers.IO) {
    val bmp = image.copy(Bitmap.Config.ARGB_8888, false)
    val name = "ensemble_cover_${System.currentTimeMillis()}.png"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Ensemble")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return@withContext false
        ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        true
    } else {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        withContext(Dispatchers.Main) { ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        true
    }
}
