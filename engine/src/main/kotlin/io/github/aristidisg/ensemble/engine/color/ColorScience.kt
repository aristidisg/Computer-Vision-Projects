package io.github.aristidisg.ensemble.engine.color

import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.HueWheel
import io.github.aristidisg.ensemble.model.Temperature
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class Rgb(val r: Int, val g: Int, val b: Int) {
    fun toHex(): String = "#%02X%02X%02X".format(r, g, b)
}

data class Lab(val l: Double, val a: Double, val b: Double) {
    val chroma: Double get() = sqrt(a * a + b * b)

    /** OKLCH hue in degrees 0..360. */
    val hue: Double get() = (Math.toDegrees(atan2(b, a)) + 360.0) % 360.0
}

/** Everything the rules need to know about one color. */
data class ColorInfo(
    val lab: Lab,
    val hue: Double,
    val family: ColorFamily,
    val neutralness: Double,
    val temperature: Temperature,
) {
    val l: Double get() = lab.l
    val c: Double get() = lab.chroma
}

/** sRGB <-> OKLab/OKLCH (Björn Ottosson), artist (RYB-style) hue wheel and per-color attributes. */
object ColorScience {

    fun parseHex(hex: String): Rgb {
        val h = hex.trim().removePrefix("#")
        require(h.length == 6) { "Expected #RRGGBB, got $hex" }
        return Rgb(h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
    }

    private fun toLinear(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun fromLinear(v: Double): Int {
        val c = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055
        return (c * 255.0).roundToInt().coerceIn(0, 255)
    }

    fun rgbToLab(rgb: Rgb): Lab = rgbToLab(rgb.r, rgb.g, rgb.b)

    fun rgbToLab(r8: Int, g8: Int, b8: Int): Lab {
        val r = toLinear(r8); val g = toLinear(g8); val b = toLinear(b8)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return Lab(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    fun labToRgb(lab: Lab): Rgb {
        val l_ = lab.l + 0.3963377774 * lab.a + 0.2158037573 * lab.b
        val m_ = lab.l - 0.1055613458 * lab.a - 0.0638541728 * lab.b
        val s_ = lab.l - 0.0894841775 * lab.a - 1.2914855480 * lab.b
        val l = l_ * l_ * l_; val m = m_ * m_ * m_; val s = s_ * s_ * s_
        return Rgb(
            fromLinear(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
            fromLinear(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
            fromLinear(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s),
        )
    }

    fun hexToLab(hex: String): Lab = rgbToLab(parseHex(hex))

    fun labToHex(lab: Lab): String = labToRgb(lab).toHex()

    fun deltaE(x: Lab, y: Lab): Double {
        val dl = x.l - y.l; val da = x.a - y.a; val db = x.b - y.b
        return sqrt(dl * dl + da * da + db * db)
    }

    /** HSV hue of an sRGB color in degrees; 0 for greys. */
    fun hsvHue(rgb: Rgb): Double {
        val r = rgb.r / 255.0; val g = rgb.g / 255.0; val b = rgb.b / 255.0
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        if (d < 1e-9) return 0.0
        val h = when (mx) {
            r -> 60.0 * (((g - b) / d) % 6.0)
            g -> 60.0 * (((b - r) / d) + 2.0)
            else -> 60.0 * (((r - g) / d) + 4.0)
        }
        return (h + 360.0) % 360.0
    }

    /**
     * Maps an HSV hue onto a painter's (RYB-style) wheel so red/green, blue/orange and
     * yellow/purple sit opposite each other. Piecewise linear: 0-30 -> 0-60, 30-60 -> 60-120,
     * 60-120 -> 120-180, 120-240 -> 180-240, 240-360 unchanged.
     */
    fun artistHueFromHsv(h: Double): Double {
        val x = ((h % 360.0) + 360.0) % 360.0
        return when {
            x < 30.0 -> x * 2.0
            x < 60.0 -> 60.0 + (x - 30.0) * 2.0
            x < 120.0 -> 120.0 + (x - 60.0)
            x < 240.0 -> 180.0 + (x - 120.0) * 0.5
            else -> x
        }
    }

    fun artistHue(rgb: Rgb): Double = artistHueFromHsv(hsvHue(rgb))

    fun hue(rgb: Rgb, wheel: HueWheel): Double =
        if (wheel == HueWheel.ARTIST) artistHue(rgb) else rgbToLab(rgb).hue

    /** Smallest angle between two hues in degrees (0..180). */
    fun hueDistance(h1: Double, h2: Double): Double {
        val d = abs(h1 - h2) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    fun nearestFamily(lab: Lab): ColorFamily =
        ColorTables.families.minBy { deltaE(lab, it.lab) }.family

    fun neutralness(lab: Lab, family: ColorFamily): Double =
        max(ColorTables.family(family).neutralness, 1.0 - lab.chroma / 0.06).coerceIn(0.0, 1.0)

    /** Warm for artist hue 330..135, cool for 165..300, neutral between; neutrals use their family's. */
    fun temperature(artistHue: Double, neutralness: Double, family: ColorFamily): Temperature {
        if (neutralness >= 0.6) return ColorTables.family(family).temperature
        return when {
            artistHue >= 330.0 || artistHue <= 135.0 -> Temperature.WARM
            artistHue in 165.0..300.0 -> Temperature.COOL
            else -> Temperature.NEUTRAL
        }
    }

    fun info(hex: String, wheel: HueWheel = HueWheel.ARTIST): ColorInfo {
        val rgb = parseHex(hex)
        val lab = rgbToLab(rgb)
        return info(lab, rgb, wheel)
    }

    fun info(lab: Lab, rgb: Rgb = labToRgb(lab), wheel: HueWheel = HueWheel.ARTIST, family: ColorFamily? = null): ColorInfo {
        val fam = family ?: nearestFamily(lab)
        val n = neutralness(lab, fam)
        val h = if (wheel == HueWheel.ARTIST) artistHue(rgb) else lab.hue
        return ColorInfo(lab, h, fam, n, temperature(h, n, fam))
    }
}
