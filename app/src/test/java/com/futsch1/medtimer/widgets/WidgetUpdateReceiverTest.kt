package com.futsch1.medtimer.widgets

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.ViewGroup
import com.futsch1.medtimer.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [35])
class WidgetUpdateReceiverTest {
    private lateinit var application: Application

    @BeforeEach
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
    }

    /** IMP-07: the action the app broadcasts must be the one the manifest intent-filter declares. */
    @Test
    fun refreshActionMatchesTheManifestIntentFilter() {
        val intent = Intent(WidgetUpdateReceiver.ACTION_WIDGET_UPDATE).setPackage(application.packageName)

        val receivers = application.packageManager.queryBroadcastReceivers(intent, 0)

        assertEquals(1, receivers.size, "exactly one receiver must accept $intent")
        assertEquals(WidgetUpdateReceiver::class.java.name, receivers[0].activityInfo.name)
    }

    @Test
    fun onReceiveUpdatesEveryPlacedWidgetOfBothProviders() {
        val appWidgetManager = AppWidgetManager.getInstance(application)
        val shadowManager = shadowOf(appWidgetManager)
        val nextId = shadowManager.createWidget(
            NextRemindersWidgetProvider::class.java, R.layout.next_reminders_widget_small
        )
        val latestId = shadowManager.createWidget(
            LatestRemindersWidgetProvider::class.java, R.layout.latest_reminders_widget_small
        )
        val nextBefore = shadowManager.getViewFor(nextId)
        val latestBefore = shadowManager.getViewFor(latestId)
        // Make every updateAppWidget() inflate a fresh view so an update is observable.
        shadowManager.setAlwaysRecreateViewsDuringUpdate(true)

        WidgetUpdateReceiver().onReceive(application, Intent(WidgetUpdateReceiver.ACTION_WIDGET_UPDATE))

        val nextAfter = shadowManager.getViewFor(nextId)
        val latestAfter = shadowManager.getViewFor(latestId)
        assertNotSame(nextBefore, nextAfter, "next-reminders widget was not updated")
        assertNotSame(latestBefore, latestAfter, "latest-reminders widget was not updated")
        // The real line providers ran against the (empty) Room database: one (empty) line slot each.
        assertEquals(1, lineContainer(nextAfter).childCount)
        assertEquals(1, lineContainer(latestAfter).childCount)
    }

    private fun lineContainer(root: android.view.View?): ViewGroup {
        assertNotNull(root)
        return root!!.findViewById(R.id.widgetLine1)
    }
}
