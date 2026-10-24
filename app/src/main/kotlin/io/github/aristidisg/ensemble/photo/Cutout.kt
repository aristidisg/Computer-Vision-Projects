package io.github.aristidisg.ensemble.photo

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.color.Lab
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot
import kotlin.math.sqrt

/** A foreground mask (0..1) at its own resolution. */
class Mask(val w: Int, val h: Int, val a: FloatArray) {
    fun copy() = Mask(w, h, a.copyOf())

    /** Touch-up brush: erase (0) or restore (1) within [radius] mask pixels of (x, y). */
    fun brush(x: Float, y: Float, radius: Float, restore: Boolean) {
        val r = radius.toInt() + 1
        for (dy in -r..r) for (dx in -r..r) {
            val px = (x + dx).toInt(); val py = (y + dy).toInt()
            if (px !in 0 until w || py !in 0 until h) continue
            if (hypot(dx.toFloat(), dy.toFloat()) <= radius) a[py * w + px] = if (restore) 1f else 0f
        }
    }

    /** 1-2 px feathering: a small box blur on the mask edge. */
    fun feathered(px: Int = 1): Mask {
        val out = FloatArray(a.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f; var n = 0
            for (dy in -px..px) for (dx in -px..px) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until w && yy in 0 until h) { s += a[yy * w + xx]; n++ }
            }
            out[y * w + x] = s / n
        }
        return Mask(w, h, out)
    }

    val coverage: Float get() = a.count { it >= 0.5f }.toFloat() / a.size
}

/**
 * Background removal (SPEC 3 step 4): ML Kit Subject Segmentation on device, with a fallback of
 * border-seeded region growing in OKLab (tolerance slider), largest connected component and a
 * morphological open/close. Both feed the same touch-up brush.
 */
object Cutout {
    suspend fun mlKit(b: Bitmap): Mask? = runCatching {
        val client = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build())
        try {
            val result = withTimeoutOrNull(15_000) { client.process(InputImage.fromBitmap(b, 0)).await() } ?: return@runCatching null
            val buf = result.foregroundConfidenceMask ?: return@runCatching null
            buf.rewind()
            val arr = FloatArray(b.width * b.height)
            buf.get(arr)
            Mask(b.width, b.height, arr).takeIf { it.coverage in 0.02f..0.98f }
        } finally {
            client.close()
        }
    }.getOrNull()

    /** Region growing from the border in OKLab on a ≤ 384 px copy. */
    fun fallback(b: Bitmap, tolerance: Float = 0.08f): Mask {
        val small = ImageOps.scaleToFit(b, 384)
        val w = small.width; val h = small.height
        val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        val lab = Array(px.size) { val c = px[it]; ColorScience.rgbToLab(Color.red(c), Color.green(c), Color.blue(c)) }
        val bg = BooleanArray(w * h)
        val queue = ArrayDeque<Int>()
        fun seed(i: Int) { if (!bg[i]) { bg[i] = true; queue.add(i) } }
        for (x in 0 until w) { seed(x); seed((h - 1) * w + x) }
        for (y in 0 until h) { seed(y * w); seed(y * w + w - 1) }
        // Compare against the running mean of the border so gradients in the backdrop still connect.
        var ml = 0.0; var ma = 0.0; var mb = 0.0
        queue.forEach { ml += lab[it].l; ma += lab[it].a; mb += lab[it].b }
        val border = Lab(ml / queue.size, ma / queue.size, mb / queue.size)
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val x = i % w; val y = i / w
            for ((dx, dy) in NEIGHBORS) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until w || ny !in 0 until h) continue
                val j = ny * w + nx
                if (bg[j]) continue
                val local = ColorScience.deltaE(lab[i], lab[j])
                val global = ColorScience.deltaE(border, lab[j])
                if (local < tolerance * 0.5 || global < tolerance) { bg[j] = true; queue.add(j) }
            }
        }
        var fg = BooleanArray(w * h) { !bg[it] }
        fg = close(open(fg, w, h), w, h)
        fg = largestComponent(fg, w, h)
        return Mask(w, h, FloatArray(w * h) { if (fg[it]) 1f else 0f })
    }

    private val NEIGHBORS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    private fun erode(m: BooleanArray, w: Int, h: Int) = BooleanArray(m.size) { i ->
        val x = i % w; val y = i / w
        m[i] && NEIGHBORS.all { (dx, dy) -> val nx = x + dx; val ny = y + dy; nx in 0 until w && ny in 0 until h && m[ny * w + nx] }
    }

    private fun dilate(m: BooleanArray, w: Int, h: Int) = BooleanArray(m.size) { i ->
        val x = i % w; val y = i / w
        m[i] || NEIGHBORS.any { (dx, dy) -> val nx = x + dx; val ny = y + dy; nx in 0 until w && ny in 0 until h && m[ny * w + nx] }
    }

    private fun open(m: BooleanArray, w: Int, h: Int) = dilate(erode(m, w, h), w, h)
    private fun close(m: BooleanArray, w: Int, h: Int) = erode(dilate(m, w, h), w, h)

    private fun largestComponent(m: BooleanArray, w: Int, h: Int): BooleanArray {
        val label = IntArray(m.size); var next = 0; var best = 0; var bestSize = 0
        for (s in m.indices) {
            if (!m[s] || label[s] != 0) continue
            next++; var size = 0
            val q = ArrayDeque<Int>().apply { add(s) }; label[s] = next
            while (q.isNotEmpty()) {
                val i = q.removeFirst(); size++
                val x = i % w; val y = i / w
                for ((dx, dy) in NEIGHBORS) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val j = ny * w + nx
                    if (m[j] && label[j] == 0) { label[j] = next; q.add(j) }
                }
            }
            if (size > bestSize) { bestSize = size; best = next }
        }
        return BooleanArray(m.size) { best != 0 && label[it] == best }
    }

    internal fun dist(a: Float, b: Float) = sqrt(a * a + b * b)
}
