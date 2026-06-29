package com.campuscue.ui.export

import android.graphics.pdf.PdfDocument
import com.campuscue.ui.timetable.TimetableUiState

@Suppress("LongMethod", "CyclomaticComplexMethod")
internal class TimetablePdfRenderer(private val state: TimetableUiState) {
    fun render(doc: PdfDocument) {
        val pager = PageManager(doc)

        pager.drawBrandBar()
        pager.drawTitle("CampusCue Timetable")
        pager.drawSubtitle(state.weekLabel.ifBlank { "Weekly Timetable" })
        pager.advance(12f)
        pager.drawDivider()
        pager.advance(16f)

        if (state.days.isEmpty()) {
            pager.drawBody("No timetable data for this week.")
        } else {
            state.days.forEach { day ->
                pager.ensureSpace(60f)
                drawDaySection(pager, day)
                pager.advance(16f)
            }
        }

        pager.finish()
    }

    private fun drawDaySection(
        p: PageManager,
        day: com.campuscue.ui.timetable.TimetableDay,
    ) {
        p.ensureSpace(50f)
        val labelPaint = boldPaint(13f, PdfColors.TITLE)
        val countPaint = regularPaint(10f, PdfColors.MUTED)
        p.canvas.drawText(day.dayName, PdfPage.MARGIN, p.y, labelPaint)
        val countStr = if (day.slots.isEmpty()) "No classes" else "${day.slots.size} class${if (day.slots.size > 1) "es" else ""}"
        p.canvas.drawText(countStr, PdfPage.MARGIN + labelPaint.measureText(day.dayName + "  "), p.y, countPaint)
        p.advance(16f)

        if (day.slots.isEmpty()) {
            p.drawBody("No classes scheduled.")
            return
        }

        val cols = floatArrayOf(0.06f, 0.22f, 0.32f, 0.14f, 0.12f, 0.14f)
        val headers = arrayOf("#", "Time", "Subject", "Code", "Room", "Type")
        drawTableHeader(p, cols, headers)

        day.slots.forEachIndexed { idx, ds ->
            val sl = ds.slot
            val timeStr = "${sl.fromTime} – ${sl.toTime}"
            val code = sl.subCode.ifBlank { sl.subjectId }
            val row = arrayOf("${sl.period.takeIf { it > 0 } ?: (idx + 1)}", timeStr, ds.displayName, code, sl.roomno, sl.lectType)
            drawTableRow(p, cols, row, idx)
        }
    }
}
