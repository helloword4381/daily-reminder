package com.dailyreminder.data.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 文档图像处理入口。
 *
 * 当前版本使用 Android 原生能力提供稳定的清晰度增强、黑白/彩色处理、锐化和证件照裁切。
 * 后续可在此类中继续接入 OpenCV 的边缘检测、角点排序、透视变换、阴影去除和遮挡修复。
 */
class ImageProcessor {

    fun processDocument(source: Bitmap, options: ImageProcessOptions): Bitmap {
        val base = if (options.autoCorrect) autoCorrectDocument(source) else source
        val shadowFixed = if (options.removeShadow || options.removeOcclusion) normalizeLighting(base, options.removeOcclusion) else base
        val output = Bitmap.createBitmap(shadowFixed.width, shadowFixed.height, Bitmap.Config.ARGB_8888)
        val matrix = ColorMatrix().apply {
            setSaturation(if (options.colorMode == ColorMode.BLACK_WHITE) 0f else 1f)
            val contrast = 1f + options.clarity * 0.18f
            val brightness = options.quality * 7f
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, brightness,
                        0f, contrast, 0f, 0f, brightness,
                        0f, 0f, contrast, 0f, brightness,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(output).drawBitmap(shadowFixed, 0f, 0f, paint)
        return if (options.sharpen > 0) sharpen(output, options.sharpen) else output
    }

    fun createIdPhoto(source: Bitmap, spec: IdPhotoSpec, background: IdPhotoBackground, suit: Boolean): Bitmap {
        val crop = centerCrop(source, spec.widthPx, spec.heightPx)
        val scaled = Bitmap.createScaledBitmap(crop, spec.widthPx, spec.heightPx, true)
        val output = Bitmap.createBitmap(spec.widthPx, spec.heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(background.color)
        val subjectRect = Rect(spec.widthPx / 8, spec.heightPx / 18, spec.widthPx * 7 / 8, spec.heightPx)
        canvas.drawBitmap(scaled, null, subjectRect, Paint(Paint.ANTI_ALIAS_FLAG))
        if (suit) drawSuit(canvas, spec.widthPx, spec.heightPx)
        return output
    }

    private fun autoCorrectDocument(source: Bitmap): Bitmap {
        val quad = detectDocumentQuad(source)
        return if (quad != null) {
            warpDocument(source, quad)
        } else {
            cropDocumentBounds(source)
        }
    }

    private fun detectDocumentQuad(source: Bitmap): Quad? {
        val maxDetectSide = 460f
        val scale = if (max(source.width, source.height) > maxDetectSide) {
            maxDetectSide / max(source.width, source.height).toFloat()
        } else {
            1f
        }
        val detectWidth = max(80, (source.width * scale).toInt())
        val detectHeight = max(80, (source.height * scale).toInt())
        val small = Bitmap.createScaledBitmap(source, detectWidth, detectHeight, true)
        val gray = IntArray(detectWidth * detectHeight)
        for (y in 0 until detectHeight) {
            for (x in 0 until detectWidth) {
                gray[y * detectWidth + x] = brightness(small.getPixel(x, y))
            }
        }
        val blurred = boxBlur(gray, detectWidth, detectHeight)
        val magnitudes = IntArray(detectWidth * detectHeight)
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until detectHeight - 1) {
            for (x in 1 until detectWidth - 1) {
                val gx =
                    -blurred[(y - 1) * detectWidth + (x - 1)] - 2 * blurred[y * detectWidth + (x - 1)] - blurred[(y + 1) * detectWidth + (x - 1)] +
                        blurred[(y - 1) * detectWidth + (x + 1)] + 2 * blurred[y * detectWidth + (x + 1)] + blurred[(y + 1) * detectWidth + (x + 1)]
                val gy =
                    -blurred[(y - 1) * detectWidth + (x - 1)] - 2 * blurred[(y - 1) * detectWidth + x] - blurred[(y - 1) * detectWidth + (x + 1)] +
                        blurred[(y + 1) * detectWidth + (x - 1)] + 2 * blurred[(y + 1) * detectWidth + x] + blurred[(y + 1) * detectWidth + (x + 1)]
                val mag = min(255, abs(gx) + abs(gy))
                magnitudes[y * detectWidth + x] = mag
                sum += mag
                sumSq += mag * mag
                count++
            }
        }

        val mean = sum / max(1, count)
        val variance = (sumSq / max(1, count)) - mean * mean
        val threshold = max(42, (mean + sqrt(max(0.0, variance)) * 1.15).toInt())
        val marginX = max(4, detectWidth / 35)
        val marginY = max(4, detectHeight / 35)

        var tl = DetectPoint(0f, 0f, Float.MAX_VALUE)
        var tr = DetectPoint(0f, 0f, -Float.MAX_VALUE)
        var br = DetectPoint(0f, 0f, -Float.MAX_VALUE)
        var bl = DetectPoint(0f, 0f, -Float.MAX_VALUE)
        var edgeCount = 0

        for (y in marginY until detectHeight - marginY) {
            for (x in marginX until detectWidth - marginX) {
                if (magnitudes[y * detectWidth + x] >= threshold) {
                    edgeCount++
                    val xf = x.toFloat()
                    val yf = y.toFloat()
                    val sumScore = xf + yf
                    val diffScore = xf - yf
                    val antiDiffScore = yf - xf
                    if (sumScore < tl.score) tl = DetectPoint(xf, yf, sumScore)
                    if (diffScore > tr.score) tr = DetectPoint(xf, yf, diffScore)
                    if (sumScore > br.score) br = DetectPoint(xf, yf, sumScore)
                    if (antiDiffScore > bl.score) bl = DetectPoint(xf, yf, antiDiffScore)
                }
            }
        }

        if (edgeCount < (detectWidth * detectHeight) * 0.006f) return null
        val invScale = 1f / scale
        val quad = Quad(
            tl = Point2(tl.x * invScale, tl.y * invScale),
            tr = Point2(tr.x * invScale, tr.y * invScale),
            br = Point2(br.x * invScale, br.y * invScale),
            bl = Point2(bl.x * invScale, bl.y * invScale)
        )
        return if (isValidQuad(quad, source.width, source.height)) quad else null
    }

    private fun isValidQuad(quad: Quad, width: Int, height: Int): Boolean {
        val area = polygonArea(quad)
        val imageArea = width.toFloat() * height
        val minArea = imageArea * 0.16f
        val maxArea = imageArea * 0.98f
        if (area !in minArea..maxArea) return false
        val top = distance(quad.tl, quad.tr)
        val bottom = distance(quad.bl, quad.br)
        val left = distance(quad.tl, quad.bl)
        val right = distance(quad.tr, quad.br)
        if (min(top, bottom) < width * 0.25f) return false
        if (min(left, right) < height * 0.25f) return false
        return true
    }

    private fun warpDocument(source: Bitmap, quad: Quad): Bitmap {
        val targetWidth = max(distance(quad.tl, quad.tr), distance(quad.bl, quad.br)).toInt().coerceIn(320, 2200)
        val targetHeight = max(distance(quad.tl, quad.bl), distance(quad.tr, quad.br)).toInt().coerceIn(320, 2600)
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(targetWidth * targetHeight)
        for (y in 0 until targetHeight) {
            val v = if (targetHeight == 1) 0f else y.toFloat() / (targetHeight - 1)
            val left = lerp(quad.tl, quad.bl, v)
            val right = lerp(quad.tr, quad.br, v)
            for (x in 0 until targetWidth) {
                val u = if (targetWidth == 1) 0f else x.toFloat() / (targetWidth - 1)
                val point = lerp(left, right, u)
                pixels[y * targetWidth + x] = sampleBilinear(source, point.x, point.y)
            }
        }
        output.setPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        return output
    }

    private fun boxBlur(gray: IntArray, width: Int, height: Int): IntArray {
        val out = gray.copyOf()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var total = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        total += gray[(y + dy) * width + (x + dx)]
                    }
                }
                out[y * width + x] = total / 9
            }
        }
        return out
    }

    private fun sampleBilinear(bitmap: Bitmap, x: Float, y: Float): Int {
        val safeX = x.coerceIn(0f, (bitmap.width - 1).toFloat())
        val safeY = y.coerceIn(0f, (bitmap.height - 1).toFloat())
        val x0 = safeX.toInt().coerceIn(0, bitmap.width - 1)
        val y0 = safeY.toInt().coerceIn(0, bitmap.height - 1)
        val x1 = (x0 + 1).coerceAtMost(bitmap.width - 1)
        val y1 = (y0 + 1).coerceAtMost(bitmap.height - 1)
        val wx = safeX - x0
        val wy = safeY - y0
        val c00 = bitmap.getPixel(x0, y0)
        val c10 = bitmap.getPixel(x1, y0)
        val c01 = bitmap.getPixel(x0, y1)
        val c11 = bitmap.getPixel(x1, y1)
        fun channel(extract: (Int) -> Int): Int {
            val top = extract(c00) * (1 - wx) + extract(c10) * wx
            val bottom = extract(c01) * (1 - wx) + extract(c11) * wx
            return (top * (1 - wy) + bottom * wy).toInt().coerceIn(0, 255)
        }
        return Color.argb(
            channel { Color.alpha(it) },
            channel { Color.red(it) },
            channel { Color.green(it) },
            channel { Color.blue(it) }
        )
    }

    private fun lerp(a: Point2, b: Point2, t: Float): Point2 {
        return Point2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }

    private fun distance(a: Point2, b: Point2): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun polygonArea(quad: Quad): Float {
        val points = listOf(quad.tl, quad.tr, quad.br, quad.bl)
        var area = 0f
        for (i in points.indices) {
            val current = points[i]
            val next = points[(i + 1) % points.size]
            area += current.x * next.y - next.x * current.y
        }
        return abs(area) / 2f
    }

    private fun cropDocumentBounds(source: Bitmap): Bitmap {
        val sampleStep = max(4, min(source.width, source.height) / 180)
        var minX = source.width
        var minY = source.height
        var maxX = 0
        var maxY = 0
        val centerBrightness = averageBrightness(source, source.width / 4, source.height / 4, source.width * 3 / 4, source.height * 3 / 4, sampleStep)
        val threshold = if (centerBrightness > 150) 105 else 135

        for (y in 0 until source.height step sampleStep) {
            for (x in 0 until source.width step sampleStep) {
                val b = brightness(source.getPixel(x, y))
                if (kotlin.math.abs(b - centerBrightness) < threshold && b > 55) {
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                }
            }
        }

        val padX = source.width / 40
        val padY = source.height / 40
        minX = (minX - padX).coerceAtLeast(0)
        minY = (minY - padY).coerceAtLeast(0)
        maxX = (maxX + padX).coerceAtMost(source.width)
        maxY = (maxY + padY).coerceAtMost(source.height)
        val cropWidth = maxX - minX
        val cropHeight = maxY - minY
        val coversEnough = cropWidth > source.width * 0.35 && cropHeight > source.height * 0.35
        val actuallyCrops = cropWidth < source.width * 0.95 || cropHeight < source.height * 0.95
        return if (coversEnough && actuallyCrops) {
            Bitmap.createBitmap(source, minX, minY, cropWidth, cropHeight)
        } else {
            source
        }
    }

    private fun normalizeLighting(source: Bitmap, softenDarkBlocks: Boolean): Bitmap {
        val width = source.width
        val height = source.height
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val avg = pixels.map { brightness(it) }.average().toInt().coerceIn(90, 205)
        val result = IntArray(pixels.size)
        for (i in pixels.indices) {
            val color = pixels[i]
            val b = brightness(color)
            val boost = when {
                softenDarkBlocks && b < avg * 0.45 -> 1.75f
                b < avg -> 1f + ((avg - b) / 255f) * 0.75f
                else -> 1f
            }
            result[i] = adjustColor(color, boost)
        }
        output.setPixels(result, 0, width, 0, 0, width, height)
        return output
    }

    private fun averageBrightness(source: Bitmap, left: Int, top: Int, right: Int, bottom: Int, step: Int): Int {
        var total = 0L
        var count = 0
        for (y in top until bottom step step) {
            for (x in left until right step step) {
                total += brightness(source.getPixel(x, y))
                count++
            }
        }
        return if (count == 0) 128 else (total / count).toInt()
    }

    private fun brightness(color: Int): Int {
        return ((Color.red(color) * 0.299f) + (Color.green(color) * 0.587f) + (Color.blue(color) * 0.114f)).toInt()
    }

    private fun adjustColor(color: Int, boost: Float): Int {
        val r = (Color.red(color) * boost).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * boost).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * boost).toInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(color), r, g, b)
    }

    private fun drawSuit(canvas: Canvas, width: Int, height: Int) {
        val suitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(24, 28, 36) }
        val shirtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val tiePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 70, 135) }
        val suit = Path().apply {
            moveTo(width * 0.18f, height.toFloat())
            lineTo(width * 0.36f, height * 0.62f)
            lineTo(width * 0.5f, height * 0.75f)
            lineTo(width * 0.64f, height * 0.62f)
            lineTo(width * 0.82f, height.toFloat())
            close()
        }
        canvas.drawPath(suit, suitPaint)
        val shirt = Path().apply {
            moveTo(width * 0.39f, height * 0.64f)
            lineTo(width * 0.5f, height * 0.82f)
            lineTo(width * 0.61f, height * 0.64f)
            lineTo(width * 0.58f, height.toFloat())
            lineTo(width * 0.42f, height.toFloat())
            close()
        }
        canvas.drawPath(shirt, shirtPaint)
        val tie = Path().apply {
            moveTo(width * 0.48f, height * 0.73f)
            lineTo(width * 0.52f, height * 0.73f)
            lineTo(width * 0.56f, height.toFloat())
            lineTo(width * 0.44f, height.toFloat())
            close()
        }
        canvas.drawPath(tie, tiePaint)
    }

    private fun centerCrop(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val sourceRatio = source.width.toFloat() / source.height
        val targetRatio = targetWidth.toFloat() / targetHeight
        val rect = if (sourceRatio > targetRatio) {
            val width = (source.height * targetRatio).toInt()
            val left = (source.width - width) / 2
            Rect(left, 0, left + width, source.height)
        } else {
            val height = (source.width / targetRatio).toInt()
            val top = max(0, (source.height - height) / 3)
            Rect(0, top, source.width, min(source.height, top + height))
        }
        return Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())
    }

    private fun sharpen(source: Bitmap, amount: Int): Bitmap {
        val width = source.width
        val height = source.height
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val factor = amount.coerceIn(1, 3)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val result = pixels.copyOf()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                val center = pixels[index]
                val left = pixels[index - 1]
                val right = pixels[index + 1]
                val top = pixels[index - width]
                val bottom = pixels[index + width]
                result[index] = sharpenPixel(center, left, right, top, bottom, factor)
            }
        }
        output.setPixels(result, 0, width, 0, 0, width, height)
        return output
    }

    private fun sharpenPixel(center: Int, left: Int, right: Int, top: Int, bottom: Int, factor: Int): Int {
        val a = center and -0x1000000
        fun channel(shift: Int): Int {
            val c = center shr shift and 0xff
            val blur = ((left shr shift and 0xff) + (right shr shift and 0xff) + (top shr shift and 0xff) + (bottom shr shift and 0xff)) / 4
            return (c + (c - blur) * factor).coerceIn(0, 255)
        }
        return a or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private data class Point2(val x: Float, val y: Float)

    private data class Quad(
        val tl: Point2,
        val tr: Point2,
        val br: Point2,
        val bl: Point2
    )

    private data class DetectPoint(
        val x: Float,
        val y: Float,
        val score: Float
    )
}

enum class ColorMode {
    COLOR,
    BLACK_WHITE
}

data class ImageProcessOptions(
    val colorMode: ColorMode = ColorMode.COLOR,
    val clarity: Int = 1,
    val quality: Int = 1,
    val sharpen: Int = 0,
    val autoCorrect: Boolean = true,
    val removeShadow: Boolean = true,
    val removeOcclusion: Boolean = false
)

enum class IdPhotoSpec(val label: String, val widthPx: Int, val heightPx: Int) {
    ONE_INCH("1 寸", 295, 413),
    TWO_INCH("2 寸", 413, 579)
}

enum class IdPhotoBackground(val label: String, val color: Int) {
    WHITE("白底", Color.WHITE),
    BLUE("蓝底", Color.rgb(67, 142, 219)),
    RED("红底", Color.rgb(210, 35, 45))
}
