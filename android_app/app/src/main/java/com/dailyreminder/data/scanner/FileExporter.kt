package com.dailyreminder.data.scanner

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Base64

class FileExporter(private val context: Context) {

    private val exportDir: File
        get() = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "scans"
        ).apply { mkdirs() }

    fun exportToPDF(bitmaps: List<Bitmap>, fileName: String = nextName("扫描件", "pdf")): File {
        val file = File(exportDir, fileName)
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        bitmaps.forEachIndexed { index, bitmap ->
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            )
            val margin = 32
            val target = fitCenter(
                bitmapWidth = bitmap.width,
                bitmapHeight = bitmap.height,
                maxWidth = pageWidth - margin * 2,
                maxHeight = pageHeight - margin * 2,
                left = margin,
                top = margin
            )
            page.canvas.drawBitmap(bitmap, null, target, null)
            document.finishPage(page)
        }
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    fun exportToWord(bitmaps: List<Bitmap>, ocrText: String, fileName: String = nextName("扫描文档", "doc")): File {
        val file = File(exportDir, fileName)
        val imageHtml = bitmaps.mapIndexed { index, bitmap ->
            """
            <div style="page-break-after: always; margin: 0 0 24px 0;">
              <p style="font-weight: bold;">第 ${index + 1} 页</p>
              <img src="data:image/jpeg;base64,${bitmap.toJpegBase64()}" style="width: 100%; max-width: 720px;" />
            </div>
            """.trimIndent()
        }.joinToString("\n")
        val html = """
            <html>
            <head><meta charset="utf-8"></head>
            <body>
            <h2>扫描文档</h2>
            <p>以下内容保留扫描图片版式；OCR 文字作为可复制文本附在文末。</p>
            $imageHtml
            <h2>OCR 文字</h2>
            <p>${escapeHtml(ocrText).lineSequence().joinToString("<br/>")}</p>
            </body>
            </html>
        """.trimIndent()
        file.writeText(html, Charsets.UTF_8)
        return file
    }

    fun exportToExcel(lines: List<String>, fileName: String = nextName("识别表格", "csv")): File {
        val file = File(exportDir, fileName)
        val csv = buildString {
            append('\uFEFF')
            if (lines.isEmpty()) {
                appendLine("内容")
            } else {
                lines.forEach { line ->
                    val cells = line.split(Regex("\\s{2,}|\\t+"))
                        .ifEmpty { listOf(line) }
                        .joinToString(",") { cell -> csvCell(cell) }
                    appendLine(cells)
                }
            }
        }
        file.writeText(csv, Charsets.UTF_8)
        return file
    }

    fun exportImage(bitmap: Bitmap, fileName: String = nextName("证件照", "jpg")): File {
        val file = File(exportDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        return file
    }

    fun shareFile(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    private fun fitCenter(
        bitmapWidth: Int,
        bitmapHeight: Int,
        maxWidth: Int,
        maxHeight: Int,
        left: Int,
        top: Int
    ): Rect {
        val scale = minOf(maxWidth / bitmapWidth.toFloat(), maxHeight / bitmapHeight.toFloat())
        val width = (bitmapWidth * scale).toInt()
        val height = (bitmapHeight * scale).toInt()
        val x = left + (maxWidth - width) / 2
        val y = top + (maxHeight - height) / 2
        return Rect(x, y, x + width, y + height)
    }

    private fun nextName(prefix: String, ext: String): String {
        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
        return "${prefix}_$time.$ext"
    }

    private fun mimeType(file: File): String = when (file.extension.lowercase(Locale.ROOT)) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "jpg", "jpeg" -> "image/jpeg"
        "csv" -> "text/csv"
        else -> "application/octet-stream"
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(',') || escaped.contains('"') || escaped.contains('\n')) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private fun Bitmap.toJpegBase64(): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 92, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
