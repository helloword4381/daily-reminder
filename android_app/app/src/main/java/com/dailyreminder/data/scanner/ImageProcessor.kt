package com.dailyreminder.data.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * 文档图像处理入口。
 *
 * 当前版本使用 Android 原生能力提供稳定的清晰度增强、黑白/彩色处理、锐化和证件照裁切。
 * 后续可在此类中继续接入 OpenCV 的边缘检测、角点排序、透视变换、阴影去除和遮挡修复。
 */
class ImageProcessor {

    fun processDocument(source: Bitmap, options: ImageProcessOptions): Bitmap {
        val base = if (options.autoCorrect) cropDocumentBounds(source) else source
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
        val result = pixels.copyOf()
        source.getPixels(pixels, 0, width, 0, 0, width, height)
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
    val removeShadow: Boolean = false,
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
