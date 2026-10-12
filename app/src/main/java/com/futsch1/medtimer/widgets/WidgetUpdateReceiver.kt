package com.futsch1.medtimer.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class WidgetUpdateReceiver : BroadcastReceiver() {
    companion object {
        /** Must match the receiver's intent-filter in AndroidManifest.xml. */
        const val ACTION_WIDGET_UPDATE = "com.futsch1.medtimer.WIDGET_UPDATE"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        val appWidgetIdsNextReminders = appWidgetManager.getAppWidgetIds(
            ComponentName(
                context!!,
                NextRemindersWidgetProvider::class.java
            )
        )
        performWidgetUpdate(
            getNextReminderWidgetImpl(context), appWidgetIdsNextReminders, appWidgetManager
        )

        val appWidgetIdsLatestReminders = appWidgetManager.getAppWidgetIds(
            ComponentName(
                context,
                LatestRemindersWidgetProvider::class.java
            )
        )
        performWidgetUpdate(
            getLatestReminderWidgetImpl(context), appWidgetIdsLatestReminders, appWidgetManager
        )
    }
}