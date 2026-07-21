package com.dailyreminder.data.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * 文档图像处理入口。
 *
 * 当前 MVP 先做轻量级灰度增强，保证扫描流程可以稳定构建和使用。
 * 后续可在此类中接入 OpenCV 的边缘检测、角点排序和透视变换。
 */
class ImageProcessor {

    fun enhanceForDocument(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.18f, 0f, 0f, 0f, 8f,
                        0f, 1.18f, 0f, 0f, 8f,
                        0f, 0f, 1.18f, 0f, 8f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
        return output
    }
}
