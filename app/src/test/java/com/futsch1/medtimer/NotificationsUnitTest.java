package com.futsch1.medtimer;

import static com.futsch1.medtimer.ActivityCodes.DISMISSED_ACTION;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_NOTIFICATION_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_EVENT_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_SNOOZE_TIME;
import static com.futsch1.medtimer.ActivityCodes.REMINDER_ACTION;
import static com.futsch1.medtimer.ActivityCodes.SNOOZE_ACTION;
import static com.futsch1.medtimer.ActivityCodes.TAKEN_ACTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;

import androidx.preference.PreferenceManager;

import com.futsch1.medtimer.database.FullMedicine;
import com.futsch1.medtimer.database.Reminder;
import com.futsch1.medtimer.database.ReminderEvent;
import com.futsch1.medtimer.reminders.Notifications;
import com.futsch1.medtimer.reminders.ReminderProcessor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPendingIntent;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import tech.apter.junit.jupiter.robolectric.RobolectricExtension;

/**
 * Pins the notification contract of the reminder module (channel, category, actions and
 * PendingIntent shapes) so that Android 16/17 behaviour-change reviews (notification trampolines,
 * Safer Intents, immutable PendingIntents) are checked by a JVM test instead of by hand.
 */
@ExtendWith(RobolectricExtension.class)
@Config(sdk = 34)
@SuppressWarnings("java:S5786") // Required for Robolectric extension
public class NotificationsUnitTest {

    private static final int REMINDER_ID = 11;
    private static final int REMINDER_EVENT_ID = 12;
    private static final int MEDICINE_ID = 1;

    private Application context;
    private NotificationManager notificationManager;
    private FullMedicine medicine;
    private Reminder reminder;

    @BeforeEach
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        notificationManager = context.getSystemService(NotificationManager.class);
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().apply();

        medicine = TestHelper.buildFullMedicine(MEDICINE_ID, "TestMedicine");
        medicine.tags = new ArrayList<>();
        reminder = TestHelper.buildReminder(MEDICINE_ID, REMINDER_ID, "1", 480, 1);
    }

    private Notification showReminderNotification() {
        ReminderEvent reminderEvent = new ReminderEvent();
        reminderEvent.reminderId = REMINDER_ID;
        reminderEvent.reminderEventId = REMINDER_EVENT_ID;
        int notificationId = new Notifications(context).showNotification("08:00", medicine, reminder, reminderEvent);
        Notification notification = shadowOf(notificationManager).getNotification(notificationId);
        assertNotNull(notification);
        return notification;
    }

    private static ShadowPendingIntent shadow(PendingIntent pendingIntent) {
        assertNotNull(pendingIntent);
        return shadowOf(pendingIntent);
    }

    private static void assertImmutable(PendingIntent pendingIntent) {
        assertTrue((shadow(pendingIntent).getFlags() & PendingIntent.FLAG_IMMUTABLE) != 0, "PendingIntent must be FLAG_IMMUTABLE");
    }

    private static void assertReceiverBroadcast(PendingIntent pendingIntent, String expectedAction) {
        ShadowPendingIntent shadowPendingIntent = shadow(pendingIntent);
        assertTrue(shadowPendingIntent.isBroadcast(), "Expected a broadcast PendingIntent for " + expectedAction);
        Intent intent = shadowPendingIntent.getSavedIntent();
        assertEquals(ReminderProcessor.class.getName(), intent.getComponent().getClassName());
        assertEquals(expectedAction, intent.getAction());
        assertEquals(REMINDER_EVENT_ID, intent.getIntExtra(EXTRA_REMINDER_EVENT_ID, -1));
        assertImmutable(pendingIntent);
    }

    private static void assertMainActivityIntent(PendingIntent pendingIntent, String expectedAction) {
        ShadowPendingIntent shadowPendingIntent = shadow(pendingIntent);
        assertTrue(shadowPendingIntent.isActivity(), "Expected an activity PendingIntent for " + expectedAction);
        Intent intent = shadowPendingIntent.getSavedIntent();
        assertEquals(MainActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals(expectedAction, intent.getAction());
        assertImmutable(pendingIntent);
    }

    @Test
    public void testChannelAndCategory() {
        Notification notification = showReminderNotification();

        assertEquals(String.valueOf(NotificationManager.IMPORTANCE_DEFAULT), notification.getChannelId());
        assertEquals(Notification.CATEGORY_REMINDER, notification.category);
        NotificationChannel channel = notificationManager.getNotificationChannel(notification.getChannelId());
        assertNotNull(channel);
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.getImportance());

        medicine.medicine.notificationImportance = NotificationManager.IMPORTANCE_HIGH;
        notification = showReminderNotification();
        assertEquals(String.valueOf(NotificationManager.IMPORTANCE_HIGH), notification.getChannelId());
        channel = notificationManager.getNotificationChannel(notification.getChannelId());
        assertNotNull(channel);
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.getImportance());
    }

    @Test
    public void testInitializeCreatesDefaultAndHighChannels() {
        NotificationChannel legacy = new NotificationChannel("com.futsch1.medTimer.NOTIFICATION1", "legacy", NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(legacy);

        ReminderNotificationChannelManager.Companion.initialize(context);

        assertNull(notificationManager.getNotificationChannel(legacy.getId()));
        for (ReminderNotificationChannelManager.Importance importance : ReminderNotificationChannelManager.Importance.values()) {
            NotificationChannel channel = notificationManager.getNotificationChannel(String.valueOf(importance.getValue()));
            assertNotNull(channel, "Channel for " + importance + " not created");
            assertEquals(importance.getValue(), channel.getImportance());
        }
    }

    @Test
    public void testContentIntentIsDirectActivityStart() {
        Notification notification = showReminderNotification();

        // Notification taps must start an activity directly (no broadcast/service trampoline, Android 12+)
        ShadowPendingIntent contentIntent = shadow(notification.contentIntent);
        assertTrue(contentIntent.isActivity());
        assertEquals(MainActivity.class.getName(), contentIntent.getSavedIntent().getComponent().getClassName());
        assertImmutable(notification.contentIntent);
    }

    @Test
    public void testDefaultActionsTakenSnoozeDismissOnSwipe() {
        Notification notification = showReminderNotification();

        assertEquals(2, notification.actions.length);
        assertEquals(context.getString(R.string.taken), notification.actions[0].title.toString());
        assertReceiverBroadcast(notification.actions[0].actionIntent, TAKEN_ACTION);
        assertEquals(context.getString(R.string.snooze), notification.actions[1].title.toString());
        assertReceiverBroadcast(notification.actions[1].actionIntent, SNOOZE_ACTION);
        Intent snoozeIntent = shadow(notification.actions[1].actionIntent).getSavedIntent();
        assertEquals(15, snoozeIntent.getIntExtra(EXTRA_SNOOZE_TIME, -1));
        assertTrue(snoozeIntent.getIntExtra(EXTRA_NOTIFICATION_ID, 0) > 0);
        assertReceiverBroadcast(notification.deleteIntent, DISMISSED_ACTION);
    }

    @Test
    public void testDismissActionPreferenceVariants() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString("dismiss_notification_action", "1").apply();
        Notification notification = showReminderNotification();
        assertEquals(2, notification.actions.length);
        assertReceiverBroadcast(notification.actions[0].actionIntent, TAKEN_ACTION);
        assertEquals(context.getString(R.string.skipped), notification.actions[1].title.toString());
        assertReceiverBroadcast(notification.actions[1].actionIntent, DISMISSED_ACTION);
        assertReceiverBroadcast(notification.deleteIntent, SNOOZE_ACTION);

        PreferenceManager.getDefaultSharedPreferences(context).edit().putString("dismiss_notification_action", "2").apply();
        notification = showReminderNotification();
        assertEquals(2, notification.actions.length);
        assertReceiverBroadcast(notification.actions[0].actionIntent, DISMISSED_ACTION);
        assertReceiverBroadcast(notification.actions[1].actionIntent, SNOOZE_ACTION);
        assertReceiverBroadcast(notification.deleteIntent, TAKEN_ACTION);
    }

    @Test
    public void testVariableAmountAndCustomSnoozeOpenMainActivity() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString("snooze_duration", "-1").apply();
        reminder.variableAmount = true;
        Notification notification = showReminderNotification();

        assertEquals(2, notification.actions.length);
        assertMainActivityIntent(notification.actions[0].actionIntent, "VARIABLE_AMOUNT");
        assertMainActivityIntent(notification.actions[1].actionIntent, "CUSTOM_SNOOZE");
        assertReceiverBroadcast(notification.deleteIntent, DISMISSED_ACTION);
    }

    @Test
    public void testReceiverActionsDeclaredInManifest() throws Exception {
        // Android 16 "Safer Intents": explicit intents with a custom action should match an
        // intent-filter on the target component. Pin every ReminderProcessor action against the manifest.
        File manifest = new File("src/main/AndroidManifest.xml");
        if (!manifest.exists()) {
            manifest = new File("app/src/main/AndroidManifest.xml");
        }
        assertTrue(manifest.exists(), "AndroidManifest.xml not found from " + new File(".").getAbsolutePath());

        Set<String> declaredActions = new HashSet<>();
        NodeList receivers = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest).getElementsByTagName("receiver");
        for (int i = 0; i < receivers.getLength(); i++) {
            Element receiver = (Element) receivers.item(i);
            if (!receiver.getAttribute("android:name").endsWith("ReminderProcessor")) {
                continue;
            }
            NodeList actions = receiver.getElementsByTagName("action");
            for (int j = 0; j < actions.getLength(); j++) {
                declaredActions.add(((Element) actions.item(j)).getAttribute("android:name"));
            }
        }

        for (String action : new String[]{REMINDER_ACTION, TAKEN_ACTION, DISMISSED_ACTION, SNOOZE_ACTION}) {
            assertTrue(declaredActions.contains(action), action + " is sent to ReminderProcessor but not declared in its intent-filter");
        }
    }
}
