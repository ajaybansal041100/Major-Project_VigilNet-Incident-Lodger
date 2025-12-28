package com.vigilnet.vigilnet

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class SOSWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {

            val intent = Intent(context, SosWidgetReceiver::class.java).apply {
                action = "SOS_TRIGGER"
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.sos_widget)

            // IMPORTANT — LONG PRESS ACTIVATES SOS
            views.setOnClickPendingIntent(R.id.sos_button, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
