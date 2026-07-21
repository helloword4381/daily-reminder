package com.dailyreminder.data.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
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
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
        return if (options.sharpen > 0) sharpen(output, options.sharpen) else output
    }

    fun createIdPhoto(source: Bitmap, spec: IdPhotoSpec): Bitmap {
        val crop = centerCrop(source, spec.widthPx, spec.heightPx)
        return Bitmap.createScaledBitmap(crop, spec.widthPx, spec.heightPx, true)
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
