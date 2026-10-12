package com.futsch1.medtimer.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.futsch1.medtimer.MainActivity
import com.futsch1.medtimer.R


data class WidgetIds(
    val widgetId: Int,
    val widgetLayoutId: Int,
    val smallWidgetLayoutId: Int
)

/**
 * Builds the RemoteViews for the home-screen widgets.
 *
 * Android 17 (targetSdk 37) enforces a hard limit on the Bitmaps/Icons carried in a RemoteViews
 * parcel (1.5 x screen width x screen height x 4 bytes); exceeding it throws a fatal
 * IllegalArgumentException from AppWidgetManager.updateAppWidget. The widgets therefore only use
 * text and resource references (no setImageViewBitmap/setIcon); WidgetImplTest guards this.
 */
class WidgetImpl(
    val context: Context,
    private val lineProvider: WidgetLineProvider,
    private val widgetIds: WidgetIds
) {
    companion object {
        const val LARGE_WIDGET_LINES = 4
        const val SMALL_WIDGET_LINES = 1
    }

    internal fun updateAppWidget(
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews())
    }

    internal fun buildRemoteViews(): RemoteViews {
        val containerView = buildLargeRemoteViews()
        val containerViewSmall = buildSmallRemoteViews()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val viewMapping: Map<SizeF, RemoteViews> = mapOf(
                SizeF(110f, 50f) to containerViewSmall,
                SizeF(110f, 150f) to containerView
            )
            RemoteViews(viewMapping)
        } else {
            containerViewSmall
        }
    }

    internal fun buildLargeRemoteViews(): RemoteViews {
        val containerView = RemoteViews(context.packageName, widgetIds.widgetLayoutId)
        createNextReminderWidgetLines(containerView, LARGE_WIDGET_LINES)
        containerView.setOnClickPendingIntent(
            widgetIds.widgetId,
            getOpenAppPendingIntent()
        )
        return containerView
    }

    internal fun buildSmallRemoteViews(): RemoteViews {
        val containerViewSmall =
            RemoteViews(context.packageName, widgetIds.smallWidgetLayoutId)
        createNextReminderWidgetLines(containerViewSmall, SMALL_WIDGET_LINES)
        containerViewSmall.setOnClickPendingIntent(
            widgetIds.widgetId,
            getOpenAppPendingIntent()
        )
        return containerViewSmall
    }

    private fun getOpenAppPendingIntent(): PendingIntent? {
        return PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

    }

    private fun createNextReminderWidgetLines(
        containerViews: RemoteViews,
        numLines: Int
    ) {
        val viewIds = intArrayOf(
            R.id.widgetLine1,
            R.id.widgetLine2,
            R.id.widgetLine3,
            R.id.widgetLine4
        )
        for (i in 0..<numLines) {
            val views = RemoteViews(context.packageName, R.layout.widget_line)
            val text = lineProvider.getWidgetLine(i)
            views.setTextViewText(R.id.widgetLineText, text)
            containerViews.addView(viewIds[i], views)
            containerViews.setViewVisibility(
                viewIds[i],
                if (text.isNotEmpty()) View.VISIBLE else View.INVISIBLE
            )
        }
    }
}

fun performWidgetUpdate(
    widgetImpl: WidgetImpl,
    appWidgetIds: IntArray,
    appWidgetManager: AppWidgetManager
) {
    for (appWidgetId in appWidgetIds) {
        widgetImpl.updateAppWidget(appWidgetManager, appWidgetId)
    }
}
