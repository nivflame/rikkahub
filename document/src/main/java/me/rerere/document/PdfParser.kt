package me.rerere.document

import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.PDFDocument
import java.io.File

object PdfParser {
    fun parserPdf(file: File): String {
        val document = PDFDocument.openDocument(file.absolutePath).asPDF()
        val pages = document.countPages()
        val result = StringBuilder()
        for (i in 0 until pages) {
            val page = document.loadPage(i).toStructuredText()
            result.append("---")
            result.append("Page ${i + 1}:\n")
            result.append(page.asText())
            result.appendLine()
        }
        return result.toString()
    }

    fun renderCover(file: File, maxWidthPx: Int = 400): ByteArray? = runCatching {
        val document = PDFDocument.openDocument(file.absolutePath)
        try {
            if (document.countPages() == 0) return null
            val page = document.loadPage(0)
            try {
                val bounds = page.getBounds()
                val zoom = maxWidthPx / (bounds.x1 - bounds.x0).coerceAtLeast(1f)
                val pixmap = page.toPixmap(Matrix.Scale(zoom), ColorSpace.DeviceRGB, false)
                try {
                    val buffer = pixmap.asPNG()
                    try {
                        buffer.asByteArray()
                    } finally {
                        buffer.destroy()
                    }
                } finally {
                    pixmap.destroy()
                }
            } finally {
                page.destroy()
            }
        } finally {
            document.destroy()
        }
    }.getOrNull()
}
