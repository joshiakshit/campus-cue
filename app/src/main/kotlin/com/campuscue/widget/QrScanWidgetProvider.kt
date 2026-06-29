package com.campuscue.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.campuscue.R
import com.campuscue.ui.MainActivity

class QrScanWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { widgetId ->
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_SCAN_QR
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val views =
                RemoteViews(context.packageName, R.layout.widget_qr_scan).apply {
                    setOnClickPendingIntent(R.id.widget_qr_root, pendingIntent)
                }
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
