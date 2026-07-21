package com.dailyreminder.data.scanner

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class OCRResult(
    val fullText: String,
    val lines: List<String>
)

class OCRProcessor {

    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    suspend fun recognize(bitmap: Bitmap): OCRResult = suspendCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val lines = visionText.textBlocks
                    .flatMap { block -> block.lines }
                    .map { line -> line.text.trim() }
                    .filter { it.isNotBlank() }
                continuation.resume(
                    OCRResult(
                        fullText = visionText.text.trim(),
                        lines = lines
                    )
                )
            }
            .addOnFailureListener { error ->
                continuation.resumeWithException(error)
            }
    }
}
