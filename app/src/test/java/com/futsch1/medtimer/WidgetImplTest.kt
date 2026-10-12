package com.futsch1.medtimer

import android.app.Application
import android.appwidget.AppWidgetManager
import android.os.Build
import android.widget.RemoteViews
import com.futsch1.medtimer.widgets.WidgetIds
import com.futsch1.medtimer.widgets.WidgetImpl
import com.futsch1.medtimer.widgets.WidgetLineProvider
import com.futsch1.medtimer.widgets.performWidgetUpdate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Protects the RemoteViews construction of the home-screen widgets (IMP-26): one text line per
 * reminder, no bitmaps, and a sized-view mapping on API 31+ so the widget stays within the
 * Android 17 widget memory limit.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class WidgetImplTest {

    private val widgetIds = WidgetIds(
        R.id.nextReminderWidget,
        R.layout.next_reminders_widget,
        R.layout.next_reminders_widget_small
    )

    private fun createLineProvider(lines: List<String>): WidgetLineProvider {
        val provider = mock(WidgetLineProvider::class.java)
        `when`(provider.getWidgetLine(org.mockito.ArgumentMatchers.anyInt())).thenAnswer { invocation ->
            val index = invocation.getArgument<Int>(0)
            lines.getOrElse(index) { "" }
        }
        return provider
    }

    @Test
    fun updateAppWidgetPushesSizedRemoteViews() {
        val application = mock(Application::class.java)
        `when`(application.packageName).thenReturn("com.futsch1.medtimer")
        val appWidgetManager = mock(AppWidgetManager::class.java)
        val lineProvider = createLineProvider(listOf("08:00 Medicine A", "20:00 Medicine B"))

        WidgetImpl(application, lineProvider, widgetIds).updateAppWidget(appWidgetManager, 42)

        val captor = ArgumentCaptor.forClass(RemoteViews::class.java)
        verify(appWidgetManager).updateAppWidget(org.mockito.ArgumentMatchers.eq(42), captor.capture())
        assertNotNull(captor.value)
        assertEquals("com.futsch1.medtimer", captor.value.getPackage())
        assertTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        // Large layout asks for four lines, small layout for one: 2 + 2 lines requested for 4 + 1 slots
        verify(lineProvider, times(2)).getWidgetLine(0)
        verify(lineProvider, times(1)).getWidgetLine(3)
        verify(lineProvider, never()).getWidgetLine(4)
    }

    @Test
    fun performWidgetUpdateUpdatesEveryWidgetId() {
        val application = mock(Application::class.java)
        `when`(application.packageName).thenReturn("com.futsch1.medtimer")
        val appWidgetManager = mock(AppWidgetManager::class.java)
        val widgetImpl = WidgetImpl(application, createLineProvider(emptyList()), widgetIds)

        performWidgetUpdate(widgetImpl, intArrayOf(1, 2, 3), appWidgetManager)

        verify(appWidgetManager, times(3)).updateAppWidget(
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.any(RemoteViews::class.java)
        )
    }
}
