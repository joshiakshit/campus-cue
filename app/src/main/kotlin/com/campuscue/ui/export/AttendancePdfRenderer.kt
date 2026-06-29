package com.campuscue.ui.export

import android.graphics.pdf.PdfDocument
import com.campuscue.domain.usecase.AttendanceTone
import com.campuscue.ui.attendance.AttendanceUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Suppress("LongMethod", "CyclomaticComplexMethod")
internal class AttendancePdfRenderer(private val state: AttendanceUiState) {
    private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy")

    fun render(doc: PdfDocument) {
        val pager = PageManager(doc)

        pager.drawBrandBar()
        pager.drawTitle("CampusCue Attendance Report")
        pager.drawSubtitle("Generated ${LocalDate.now().format(dateFmt)}  •  Threshold ${state.threshold}%")
        pager.advance(12f)
        pager.drawDivider()
        pager.advance(16f)

        drawOverallCard(pager)
        pager.advance(20f)

        pager.drawSectionHeading("Subject Breakdown")
        pager.advance(6f)
        drawSubjectTable(pager)
        pager.advance(20f)

        if (state.forecast.isNotEmpty()) {
            pager.ensureSpace(80f)
            pager.drawSectionHeading("Recovery Forecast")
            pager.advance(6f)
            drawForecastTable(pager)
        }

        pager.finish()
    }

    private fun drawOverallCard(p: PageManager) {
        p.ensureSpace(70f)
        val x = PdfPage.MARGIN
        val w = PdfPage.USABLE_WIDTH
        val cardY = p.y
        val cardH = 60f

        p.canvas.drawRect(x, cardY, x + w, cardY + cardH, fillPaint(PdfColors.BRAND_LIGHT))
        p.canvas.drawRect(x, cardY, x + w, cardY + cardH, strokePaint(PdfColors.BRAND, 1f))

        val big = boldPaint(28f, toneColor(state.overallTone))
        p.canvas.drawText(
            String.format(Locale.US, "%.1f%%", state.overallPercent),
            x + 16f,
            cardY + 38f,
            big,
        )

        val detail = regularPaint(11f, PdfColors.BODY)
        p.canvas.drawText(
            "${state.overallPresent} / ${state.overallTotal} classes attended",
            x + 16f + big.measureText("100.0%  "),
            cardY + 28f,
            detail,
        )
        val semLabel = regularPaint(10f, PdfColors.MUTED)
        if (state.semesterLabel.isNotBlank()) {
            p.canvas.drawText(state.semesterLabel, x + 16f + big.measureText("100.0%  "), cardY + 44f, semLabel)
        }
        p.advance(cardH + 8f)
    }

    private fun drawSubjectTable(p: PageManager) {
        val cols = floatArrayOf(0.30f, 0.08f, 0.14f, 0.12f, 0.12f, 0.12f, 0.12f)
        val headers = arrayOf("Subject", "Type", "Code", "Attended", "Percent", "Can Skip", "Need")

        drawTableHeader(p, cols, headers)

        state.subjects.forEachIndexed { idx, item ->
            val s = item.subject
            val pct = String.format(Locale.US, "%.1f%%", s.percent)
            val row = arrayOf(s.subName, s.lecType, s.subCode, "${s.present}/${s.total}", pct, "${item.bunkable}", "${item.need}")
            val toneCol = toneColor(item.tone)
            drawTableRow(p, cols, row, idx, toneCol, 4)
        }
    }

    private fun drawForecastTable(p: PageManager) {
        val cols = floatArrayOf(0.30f, 0.12f, 0.18f, 0.14f, 0.12f, 0.14f)
        val headers = arrayOf("Subject", "Type", "Reach Date", "Classes/wk", "Need", "Max Reachable")

        drawTableHeader(p, cols, headers)

        state.forecast.forEachIndexed { idx, row ->
            val reachStr = row.reachDate?.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) ?: "—"
            val maxStr = row.maxReachable?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—"
            val data = arrayOf(row.subName, row.lecType, reachStr, "${row.weeklyCount}", "${row.need}", maxStr)
            drawTableRow(p, cols, data, idx)
        }
    }
}

private fun toneColor(tone: AttendanceTone): Int =
    when (tone) {
        AttendanceTone.OK -> PdfColors.OK
        AttendanceTone.WARN -> PdfColors.WARN
        AttendanceTone.BAD -> PdfColors.BAD
    }
