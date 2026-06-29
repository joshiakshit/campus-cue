@file:Suppress("TooManyFunctions")

package com.campuscue.ui.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.campuscue.ui.attendance.AttendanceUiState
import com.campuscue.ui.timetable.TimetableUiState
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal object PdfPage {
    const val WIDTH = 595
    const val HEIGHT = 842
    const val MARGIN = 36f
    const val USABLE_WIDTH = WIDTH - MARGIN * 2
}

internal object PdfColors {
    val BRAND = Color.rgb(37, 99, 235)
    val BRAND_LIGHT = Color.rgb(238, 244, 255)
    val TITLE = Color.rgb(16, 24, 40)
    val SUBTITLE = Color.rgb(102, 112, 133)
    val HEADING = Color.rgb(37, 99, 235)
    val BODY = Color.rgb(52, 64, 84)
    val MUTED = Color.rgb(102, 112, 133)
    val BORDER = Color.rgb(217, 226, 239)
    val TABLE_HEADER_BG = Color.rgb(37, 99, 235)
    val TABLE_HEADER_TEXT = Color.WHITE
    val TABLE_ALT_ROW = Color.rgb(248, 250, 252)
    val OK = Color.rgb(22, 163, 74)
    val WARN = Color.rgb(217, 119, 6)
    val BAD = Color.rgb(220, 38, 38)
    val WHITE = Color.WHITE
}

fun exportAttendancePdf(
    context: Context,
    state: AttendanceUiState,
) {
    val doc = PdfDocument()
    val renderer = AttendancePdfRenderer(state)
    renderer.render(doc)
    sharePdf(context, doc, "campuscue-attendance", "Attendance Report")
}

fun exportTimetablePdf(
    context: Context,
    state: TimetableUiState,
) {
    val doc = PdfDocument()
    val renderer = TimetablePdfRenderer(state)
    renderer.render(doc)
    sharePdf(context, doc, "campuscue-timetable", "Timetable Report")
}

@Suppress("TooManyFunctions")
internal class PageManager(private val doc: PdfDocument) {
    var canvas: Canvas = dummyCanvas()
    var y: Float = PdfPage.MARGIN
    private var pageNumber = 0
    private var page: PdfDocument.Page? = null

    init {
        newPage()
    }

    fun newPage() {
        page?.let { doc.finishPage(it) }
        pageNumber++
        val info = PdfDocument.PageInfo.Builder(PdfPage.WIDTH, PdfPage.HEIGHT, pageNumber).create()
        val p = doc.startPage(info)
        page = p
        canvas = p.canvas
        y = PdfPage.MARGIN
        if (pageNumber > 1) drawPageFooter()
    }

    fun advance(dy: Float) {
        y += dy
    }

    fun ensureSpace(needed: Float) {
        if (y + needed > PdfPage.HEIGHT - PdfPage.MARGIN - 20f) newPage()
    }

    fun finish() {
        drawPageFooter()
        page?.let { doc.finishPage(it) }
    }

    fun drawBrandBar() {
        canvas.drawRect(0f, 0f, PdfPage.WIDTH.toFloat(), 6f, fillPaint(PdfColors.BRAND))
    }

    fun drawTitle(text: String) {
        y = PdfPage.MARGIN + 24f
        canvas.drawText(text, PdfPage.MARGIN, y, boldPaint(22f, PdfColors.TITLE))
        y += 6f
    }

    fun drawSubtitle(text: String) {
        y += 16f
        canvas.drawText(text, PdfPage.MARGIN, y, regularPaint(11f, PdfColors.SUBTITLE))
    }

    fun drawDivider() {
        canvas.drawLine(PdfPage.MARGIN, y, PdfPage.WIDTH - PdfPage.MARGIN, y, strokePaint(PdfColors.BORDER, 0.5f))
    }

    fun drawSectionHeading(text: String) {
        ensureSpace(30f)
        canvas.drawText(text, PdfPage.MARGIN, y, boldPaint(14f, PdfColors.HEADING))
        y += 4f
    }

    fun drawBody(text: String) {
        canvas.drawText(text, PdfPage.MARGIN, y, regularPaint(11f, PdfColors.BODY))
        y += 16f
    }

    private fun drawPageFooter() {
        val footerY = PdfPage.HEIGHT - 18f
        canvas.drawLine(PdfPage.MARGIN, footerY - 6f, PdfPage.WIDTH - PdfPage.MARGIN, footerY - 6f, strokePaint(PdfColors.BORDER, 0.3f))
        val left = regularPaint(8f, PdfColors.MUTED)
        canvas.drawText("CampusCue", PdfPage.MARGIN, footerY, left)
        val right = regularPaint(8f, PdfColors.MUTED)
        val pageStr = "Page $pageNumber"
        canvas.drawText(pageStr, PdfPage.WIDTH - PdfPage.MARGIN - right.measureText(pageStr), footerY, right)
    }

    private fun dummyCanvas(): Canvas = Canvas()
}

internal fun drawTableHeader(
    p: PageManager,
    cols: FloatArray,
    headers: Array<String>,
) {
    p.ensureSpace(22f)
    val rowH = 20f
    val x0 = PdfPage.MARGIN
    val w = PdfPage.USABLE_WIDTH

    p.canvas.drawRect(x0, p.y, x0 + w, p.y + rowH, fillPaint(PdfColors.TABLE_HEADER_BG))
    val paint = boldPaint(8.5f, PdfColors.TABLE_HEADER_TEXT)
    var cx = x0 + 6f
    for (i in headers.indices) {
        val colW = w * cols[i]
        p.canvas.drawText(headers[i], cx, p.y + 14f, paint)
        cx += colW
    }
    p.advance(rowH)
}

internal fun drawTableRow(
    p: PageManager,
    cols: FloatArray,
    cells: Array<String>,
    rowIdx: Int,
    accentColor: Int? = null,
    accentCol: Int = -1,
) {
    val rowH = 18f
    p.ensureSpace(rowH)
    val x0 = PdfPage.MARGIN
    val w = PdfPage.USABLE_WIDTH

    if (rowIdx % 2 == 1) {
        p.canvas.drawRect(x0, p.y, x0 + w, p.y + rowH, fillPaint(PdfColors.TABLE_ALT_ROW))
    }
    p.canvas.drawLine(x0, p.y + rowH, x0 + w, p.y + rowH, strokePaint(PdfColors.BORDER, 0.3f))

    var cx = x0 + 6f
    for (i in cells.indices) {
        val colW = w * cols[i]
        val color = if (i == accentCol && accentColor != null) accentColor else PdfColors.BODY
        val paint = regularPaint(9f, color)
        val text = ellipsize(cells[i], paint, colW - 10f)
        p.canvas.drawText(text, cx, p.y + 13f, paint)
        cx += colW
    }
    p.advance(rowH)
}

internal fun ellipsize(
    text: String,
    paint: Paint,
    maxW: Float,
): String {
    if (paint.measureText(text) <= maxW) return text
    for (len in text.length - 1 downTo 1) {
        val candidate = text.substring(0, len) + "…"
        if (paint.measureText(candidate) <= maxW) return candidate
    }
    return text
}

internal fun sharePdf(
    context: Context,
    doc: PdfDocument,
    prefix: String,
    title: String,
) {
    val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
    val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
    val file = File(dir, "$prefix-$date.pdf")
    file.outputStream().use { doc.writeTo(it) }
    doc.close()

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(shareIntent, "Share $title"))
}

internal fun boldPaint(
    size: Float,
    color: Int,
): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

internal fun regularPaint(
    size: Float,
    color: Int,
): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
    }

internal fun fillPaint(color: Int): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

internal fun strokePaint(
    color: Int,
    width: Float,
): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
    }
