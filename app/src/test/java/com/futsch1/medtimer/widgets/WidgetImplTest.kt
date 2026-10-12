package com.futsch1.medtimer.widgets

import android.app.Application
import android.appwidget.AppWidgetManager
import android.os.Parcel
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import org.robolectric.RuntimeEnvironment
import com.futsch1.medtimer.MainActivity
import com.futsch1.medtimer.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [35])
class WidgetImplTest {
    private lateinit var application: Application
    private lateinit var themedContext: ContextThemeWrapper

    private val nextReminderIds = WidgetIds(
        R.id.nextReminderWidget,
        R.layout.next_reminders_widget,
        R.layout.next_reminders_widget_small
    )
    private val latestReminderIds = WidgetIds(
        R.id.latestReminderWidget,
        R.layout.latest_reminders_widget,
        R.layout.latest_reminders_widget_small
    )

    @BeforeEach
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        themedContext = ContextThemeWrapper(application, R.style.Theme_MedTimer_WidgetContainer)
    }

    private fun linesProvider(lines: List<String>) = WidgetLineProvider { line ->
        lines.getOrNull(line) ?: ""
    }

    private fun inflate(remoteViews: RemoteViews): View =
        remoteViews.apply(themedContext, FrameLayout(themedContext))

    private fun lineText(root: View, lineId: Int): TextView? =
        root.findViewById<ViewGroup>(lineId)?.findViewById(R.id.widgetLineText)

    @Test
    fun largeWidgetRendersFourLinesWithProviderText() {
        val lines = listOf("08:00: 1 Aspirin", "12:00: 2 Ibuprofen", "18:00: 1 Vitamin", "22:00: 1 Zinc")
        val impl = WidgetImpl(application, linesProvider(lines), nextReminderIds)

        val root = inflate(impl.buildLargeRemoteViews())

        assertEquals(lines[0], lineText(root, R.id.widgetLine1)?.text.toString())
        assertEquals(lines[1], lineText(root, R.id.widgetLine2)?.text.toString())
        assertEquals(lines[2], lineText(root, R.id.widgetLine3)?.text.toString())
        assertEquals(lines[3], lineText(root, R.id.widgetLine4)?.text.toString())
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widgetLine4).visibility)
    }

    @Test
    fun emptyLinesAreInvisibleButKeepTheirSlot() {
        val impl = WidgetImpl(application, linesProvider(listOf("08:00: 1 Aspirin")), nextReminderIds)

        val root = inflate(impl.buildLargeRemoteViews())

        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widgetLine1).visibility)
        assertEquals(View.INVISIBLE, root.findViewById<View>(R.id.widgetLine2).visibility)
        assertEquals(View.INVISIBLE, root.findViewById<View>(R.id.widgetLine3).visibility)
        assertEquals(View.INVISIBLE, root.findViewById<View>(R.id.widgetLine4).visibility)
        assertEquals("", lineText(root, R.id.widgetLine2)?.text.toString())
    }

    @Test
    fun emptyStateRendersNoVisibleLine() {
        val impl = WidgetImpl(application, linesProvider(emptyList()), latestReminderIds)

        val root = inflate(impl.buildLargeRemoteViews())

        listOf(R.id.widgetLine1, R.id.widgetLine2, R.id.widgetLine3, R.id.widgetLine4).forEach {
            assertEquals(View.INVISIBLE, root.findViewById<View>(it).visibility)
        }
    }

    @Test
    fun longListIsTruncatedToTheWidgetLineLimit() {
        val requested = mutableListOf<Int>()
        val tenLines = (1..10).map { "line $it" }
        val provider = WidgetLineProvider { line ->
            requested.add(line)
            tenLines.getOrNull(line) ?: ""
        }
        val impl = WidgetImpl(application, provider, nextReminderIds)

        val large = inflate(impl.buildLargeRemoteViews())
        assertEquals((0 until WidgetImpl.LARGE_WIDGET_LINES).toList(), requested)
        assertEquals("line 4", lineText(large, R.id.widgetLine4)?.text.toString())

        requested.clear()
        val small = inflate(impl.buildSmallRemoteViews())
        assertEquals(listOf(0), requested)
        assertEquals("line 1", lineText(small, R.id.widgetLine1)?.text.toString())
        assertNull(small.findViewById<View>(R.id.widgetLine2))
    }

    @Test
    fun widgetRootOpensMainActivityOnClick() {
        val impl = WidgetImpl(application, linesProvider(listOf("x")), nextReminderIds)

        val root = inflate(impl.buildLargeRemoteViews())
        val clickable = root.findViewById<View>(R.id.nextReminderWidget)

        assertNotNull(clickable)
        assertTrue(clickable.hasOnClickListeners())
        clickable.performClick()
        val started = shadowOf(application).nextStartedActivity
        assertNotNull(started, "click must send the PendingIntent that opens MainActivity")
        assertEquals(MainActivity::class.java.name, started.component?.className)
    }

    /**
     * IMP-01 (Android 17, targetSdk 37): the system rejects RemoteViews whose Bitmaps/Icons
     * exceed 1.5 * screenWidth * screenHeight * 4 bytes. The widgets carry text and resource ids
     * only, so the complete parcel must stay tiny compared to that limit; this fails as soon as
     * someone adds setImageViewBitmap/setIcon payloads to the widget.
     */
    @Test
    fun sizedRemoteViewsParcelStaysFarBelowAndroid17BitmapLimit() {
        val tenLines = (1..10).map { "line $it with a fairly long medicine name" }
        val impl = WidgetImpl(application, linesProvider(tenLines), nextReminderIds)

        val remoteViews = impl.buildRemoteViews()
        val parcel = Parcel.obtain()
        val parcelSize = try {
            remoteViews.writeToParcel(parcel, 0)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }

        val metrics = application.resources.displayMetrics
        val android17Limit = 1.5 * metrics.widthPixels * metrics.heightPixels * 4
        assertTrue(parcelSize > 0, "parcel must not be empty")
        assertTrue(
            parcelSize < 64 * 1024,
            "widget parcel is $parcelSize bytes; a bitmap payload would blow this budget"
        )
        assertTrue(parcelSize < android17Limit, "parcel $parcelSize >= limit $android17Limit")
    }

    @Test
    fun performWidgetUpdatePushesOneRemoteViewsPerWidgetId() {
        val appWidgetManager = mock(AppWidgetManager::class.java)
        val impl = WidgetImpl(application, linesProvider(listOf("08:00: 1 Aspirin")), nextReminderIds)

        performWidgetUpdate(impl, intArrayOf(7, 9), appWidgetManager)

        verify(appWidgetManager).updateAppWidget(eq(7), any(RemoteViews::class.java))
        verify(appWidgetManager).updateAppWidget(eq(9), any(RemoteViews::class.java))
        verifyNoMoreInteractions(appWidgetManager)
    }
}
