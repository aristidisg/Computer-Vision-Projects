package io.github.aristidisg.ensemble.photo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.color.GarmentColors
import io.github.aristidisg.ensemble.engine.color.KMeans
import io.github.aristidisg.ensemble.engine.color.Lab
import io.github.aristidisg.ensemble.engine.color.PatternHint
import io.github.aristidisg.ensemble.engine.color.Sample
import io.github.aristidisg.ensemble.model.ItemColor
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Per-channel gains for white balance. */
data class Gains(val r: Float, val g: Float, val b: Float) {
    companion object { val IDENTITY = Gains(1f, 1f, 1f) }
}

/** Bitmap helpers for SPEC 3 steps 2, 3, 5, 6 and 9. */
object ImageOps {
    const val MAX_EDGE = 2048
    const val THUMB_EDGE = 512

    /** Decode, fix EXIF orientation and downscale to 2048 px on the long edge. Re-encoding drops all EXIF, GPS included. */
    fun loadNormalized(resolver: ContentResolver, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Could not read the image")
        val orientation = resolver.openInputStream(uri)?.use {
            runCatching { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrNull()
        } ?: ExifInterface.ORIENTATION_NORMAL
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        }
        val oriented = if (m.isIdentity) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        return scaleToFit(oriented, MAX_EDGE)
    }

    fun scaleToFit(b: Bitmap, edge: Int): Bitmap {
        val longEdge = max(b.width, b.height)
        if (longEdge <= edge) return b
        val s = edge.toFloat() / longEdge
        return Bitmap.createScaledBitmap(b, (b.width * s).roundToInt().coerceAtLeast(1), (b.height * s).roundToInt().coerceAtLeast(1), true)
    }

    /**
     * Gray-world on the background: assumes the backdrop is neutral and averages only pixels
     * outside the garment [mask] (a border frame without one; saturated pixels ignored).
     * Averaging the garment too would "correct" a brown jacket toward grey.
     */
    fun grayWorldGains(b: Bitmap, mask: Mask? = null): Gains {
        val small = scaleToFit(b, 256)
        val w = small.width; val h = small.height
        val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        var r = 0.0; var g = 0.0; var bl = 0.0; var n = 0
        for (i in px.indices) {
            val x = i % w; val y = i / w
            val background = if (mask != null) {
                mask.a[(y * mask.h / h).coerceIn(0, mask.h - 1) * mask.w + (x * mask.w / w).coerceIn(0, mask.w - 1)] < 0.5f
            } else x < w / 8 || x >= w - w / 8 || y < h / 8 || y >= h - h / 8
            if (!background) continue
            val c = px[i]
            val cr = Color.red(c); val cg = Color.green(c); val cb = Color.blue(c)
            if (max(cr, max(cg, cb)) >= 250 || cr + cg + cb < 30) continue
            r += cr; g += cg; bl += cb; n++
        }
        if (n < 50) return Gains.IDENTITY
        val mean = (r + g + bl) / (3 * n)
        return Gains((mean / (r / n)).toFloat().coerceIn(0.6f, 1.6f), (mean / (g / n)).toFloat().coerceIn(0.6f, 1.6f), (mean / (bl / n)).toFloat().coerceIn(0.6f, 1.6f))
    }

    /** "Tap something white or grey": gains that make a 9×9 patch neutral. */
    fun tapGains(b: Bitmap, x: Int, y: Int): Gains {
        var r = 0.0; var g = 0.0; var bl = 0.0; var n = 0
        for (dy in -4..4) for (dx in -4..4) {
            val px = (x + dx).coerceIn(0, b.width - 1); val py = (y + dy).coerceIn(0, b.height - 1)
            val c = b.getPixel(px, py); r += Color.red(c); g += Color.green(c); bl += Color.blue(c); n++
        }
        val mean = (r + g + bl) / (3 * n)
        fun gain(v: Double) = (mean / (v / n).coerceAtLeast(1.0)).toFloat().coerceIn(0.5f, 2f)
        return Gains(gain(r), gain(g), gain(bl))
    }

    fun applyGains(b: Bitmap, gains: Gains): Bitmap {
        if (gains == Gains.IDENTITY) return b
        val out = b.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(out.width * out.height)
        out.getPixels(px, 0, out.width, 0, 0, out.width, out.height)
        for (i in px.indices) {
            val c = px[i]
            px[i] = Color.argb(Color.alpha(c), (Color.red(c) * gains.r).roundToInt().coerceIn(0, 255),
                (Color.green(c) * gains.g).roundToInt().coerceIn(0, 255), (Color.blue(c) * gains.b).roundToInt().coerceIn(0, 255))
        }
        out.setPixels(px, 0, out.width, 0, 0, out.width, out.height)
        return out
    }

    /** Applies a 0..1 alpha mask (same size as the image, row-major) to make the cutout. */
    fun withMask(b: Bitmap, mask: FloatArray, mw: Int, mh: Int): Bitmap {
        val out = b.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(out.width * out.height)
        out.getPixels(px, 0, out.width, 0, 0, out.width, out.height)
        for (y in 0 until out.height) for (x in 0 until out.width) {
            val mx = (x * mw / out.width).coerceIn(0, mw - 1); val my = (y * mh / out.height).coerceIn(0, mh - 1)
            val a = (mask[my * mw + mx] * 255).roundToInt().coerceIn(0, 255)
            val i = y * out.width + x
            px[i] = (px[i] and 0x00FFFFFF) or (a shl 24)
        }
        out.setPixels(px, 0, out.width, 0, 0, out.width, out.height)
        return out
    }

    /**
     * SPEC 3 step 5, tuned for accuracy: sample only pixels well inside the garment (fully
     * opaque, with fully opaque neighbours, so cutout edges blended with the background never
     * count), at about 192 px without smoothing, then GarmentColors: k-means, shade merge and a
     * robust median per color.
     */
    fun extractColors(cutout: Bitmap): List<ItemColor> {
        val s = minOf(1f, 192f / maxOf(cutout.width, cutout.height))
        val small = if (s >= 1f) cutout else Bitmap.createScaledBitmap(cutout, (cutout.width * s).roundToInt().coerceAtLeast(1), (cutout.height * s).roundToInt().coerceAtLeast(1), false)
        val w = small.width; val h = small.height
        val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        fun solid(x: Int, y: Int) = x in 0 until w && y in 0 until h && Color.alpha(px[y * w + x]) >= 250
        val inner = ArrayList<Sample>()
        for (y in 0 until h) for (x in 0 until w) {
            if (!solid(x, y) || !solid(x - 1, y) || !solid(x + 1, y) || !solid(x, y - 1) || !solid(x, y + 1)) continue
            val c = px[y * w + x]
            inner += Sample(ColorScience.rgbToLab(Color.red(c), Color.green(c), Color.blue(c)), 1.0)
        }
        // A very thin piece (a belt, a strap) may have no inner pixels: fall back to opaque ones.
        val samples = inner.ifEmpty {
            px.filter { Color.alpha(it) >= 128 }.map { c -> Sample(ColorScience.rgbToLab(Color.red(c), Color.green(c), Color.blue(c)), 1.0) }
        }
        return GarmentColors.extract(samples)
    }

    /** SPEC 3 step 6: contrasting clusters plus dense Sobel edges on lightness suggest a pattern. */
    fun looksPatterned(cutout: Bitmap): Boolean {
        val small = scaleToFit(cutout, 128)
        val w = small.width; val h = small.height
        val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        val light = Array(h) { y -> DoubleArray(w) { x -> val c = px[y * w + x]; ColorScience.rgbToLab(Color.red(c), Color.green(c), Color.blue(c)).l } }
        val mask = Array(h) { y -> BooleanArray(w) { x -> Color.alpha(px[y * w + x]) >= 128 } }
        val samples = px.filter { Color.alpha(it) >= 128 }.map { Sample(ColorScience.rgbToLab(Color.red(it), Color.green(it), Color.blue(it)), 1.0) }
        return PatternHint.looksPatterned(KMeans.cluster(samples), PatternHint.edgeDensity(light, mask))
    }

    /** Eyedropper: average OKLab of a 5×5 patch. */
    fun pick(b: Bitmap, x: Int, y: Int): Lab {
        var l = 0.0; var a = 0.0; var bb = 0.0; var n = 0
        for (dy in -2..2) for (dx in -2..2) {
            val c = b.getPixel((x + dx).coerceIn(0, b.width - 1), (y + dy).coerceIn(0, b.height - 1))
            if (Color.alpha(c) < 128) continue
            val lab = ColorScience.rgbToLab(Color.red(c), Color.green(c), Color.blue(c)); l += lab.l; a += lab.a; bb += lab.b; n++
        }
        return if (n == 0) ColorScience.hexToLab("#808080") else Lab(l / n, a / n, bb / n)
    }

    fun saveJpeg(b: Bitmap, f: File) { f.parentFile?.mkdirs(); f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) } }

    @Suppress("DEPRECATION")
    fun saveWebp(b: Bitmap, f: File, quality: Int = 90) {
        f.parentFile?.mkdirs()
        val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        f.outputStream().use { b.compress(fmt, quality, it) }
    }
}
