package com.futsch1.medtimer;

import static com.futsch1.medtimer.ActivityCodes.DISMISSED_ACTION;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_AMOUNT;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_NOTIFICATION_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_EVENT_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_SNOOZE_TIME;
import static com.futsch1.medtimer.ActivityCodes.REMINDER_ACTION;
import static com.futsch1.medtimer.ActivityCodes.SNOOZE_ACTION;
import static com.futsch1.medtimer.ActivityCodes.TAKEN_ACTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

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
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowNotificationManager;
import org.robolectric.shadows.ShadowPendingIntent;

import java.util.ArrayList;
import java.util.List;

import tech.apter.junit.jupiter.robolectric.RobolectricExtension;

/**
 * Pins the notification contract of the reminder module (channel, category, actions, PendingIntent
 * flags and intent-filter coverage) so that the Android 13-17 behaviour-change reviews (notification
 * permission, immutable PendingIntents, Safer Intents) are checked by a JVM test instead of by hand.
 * <p>
 * Uses {@link Shadow#extract(Object)} instead of {@code Shadows.shadowOf(...)}: on compileSdk 37 the
 * {@code shadowOf} overload set references {@code FingerprintManager} stubs that the API 37 android.jar
 * no longer ships, which breaks javac.
 */
@ExtendWith(RobolectricExtension.class)
@Config(sdk = 35)
@SuppressWarnings("java:S5786") // Required for Robolectric extension
public class NotificationsUnitTest {

    private static final int REMINDER_ID = 11;
    private static final int REMINDER_EVENT_ID = 12;
    private static final int MEDICINE_ID = 1;

    private Application context;
    private NotificationManager notificationManager;
    private ShadowNotificationManager shadowNotificationManager;
    private FullMedicine medicine;
    private Reminder reminder;

    @BeforeEach
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        notificationManager = context.getSystemService(NotificationManager.class);
        shadowNotificationManager = Shadow.extract(notificationManager);
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
        Notification notification = shadowNotificationManager.getNotification(notificationId);
        assertNotNull(notification);
        return notification;
    }

    private static ShadowPendingIntent shadow(PendingIntent pendingIntent) {
        assertNotNull(pendingIntent);
        return Shadow.extract(pendingIntent);
    }

    private static void assertImmutable(PendingIntent pendingIntent) {
        int flags = shadow(pendingIntent).getFlags();
        assertTrue((flags & PendingIntent.FLAG_IMMUTABLE) != 0, "PendingIntent must be FLAG_IMMUTABLE");
        assertEquals(0, flags & PendingIntent.FLAG_MUTABLE, "PendingIntent must not be FLAG_MUTABLE");
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

    private void assertNotificationActionActivityIntent(PendingIntent pendingIntent, String expectedAction) {
        ShadowPendingIntent shadowPendingIntent = shadow(pendingIntent);
        assertTrue(shadowPendingIntent.isActivity(), "Expected an activity PendingIntent for " + expectedAction);
        Intent intent = shadowPendingIntent.getSavedIntent();
        assertEquals(new ComponentName(context.getPackageName(), Notifications.NOTIFICATION_ACTION_ACTIVITY), intent.getComponent());
        assertEquals(expectedAction, intent.getAction());
        assertEquals(REMINDER_EVENT_ID, intent.getIntExtra(EXTRA_REMINDER_EVENT_ID, -1));
        assertImmutable(pendingIntent);
    }

    private void assertEveryPendingIntentImmutable(Notification notification) {
        assertImmutable(notification.contentIntent);
        assertImmutable(notification.deleteIntent);
        for (Notification.Action action : notification.actions) {
            assertImmutable(action.actionIntent);
        }
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
        // Re-initialising must be idempotent (channels are looked up, not recreated)
        ReminderNotificationChannelManager.Companion.initialize(context);
        assertEquals(2, notificationManager.getNotificationChannels().size());
    }

    @Test
    public void testContentIntentIsDirectActivityStart() {
        Notification notification = showReminderNotification();

        // Notification taps must start an activity directly (no broadcast/service trampoline, Android 12+)
        ShadowPendingIntent contentIntent = shadow(notification.contentIntent);
        assertTrue(contentIntent.isActivity());
        assertEquals(MainActivity.class.getName(), contentIntent.getSavedIntent().getComponent().getClassName());
        assertImmutable(notification.contentIntent);
        // No full-screen intent: USE_FULL_SCREEN_INTENT is not auto-granted to non-alarm apps on Android 14+
        assertNull(notification.fullScreenIntent);
        assertEquals(0, notification.flags & Notification.FLAG_ONGOING_EVENT);
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
        assertEquals(REMINDER_ID, snoozeIntent.getIntExtra(EXTRA_REMINDER_ID, -1));
        assertTrue(snoozeIntent.getIntExtra(EXTRA_NOTIFICATION_ID, 0) > 0);
        assertReceiverBroadcast(notification.deleteIntent, DISMISSED_ACTION);
        assertEveryPendingIntentImmutable(notification);
    }

    @Test
    public void testDismissActionPreferenceVariants() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString("dismiss_notification_action", "1").apply();
        Notification notification = showReminderNotification();
        assertEquals(2, notification.actions.length);
        assertEquals(context.getString(R.string.taken), notification.actions[0].title.toString());
        assertReceiverBroadcast(notification.actions[0].actionIntent, TAKEN_ACTION);
        assertEquals(context.getString(R.string.skipped), notification.actions[1].title.toString());
        assertReceiverBroadcast(notification.actions[1].actionIntent, DISMISSED_ACTION);
        assertReceiverBroadcast(notification.deleteIntent, SNOOZE_ACTION);
        assertEveryPendingIntentImmutable(notification);

        PreferenceManager.getDefaultSharedPreferences(context).edit().putString("dismiss_notification_action", "2").apply();
        notification = showReminderNotification();
        assertEquals(2, notification.actions.length);
        assertEquals(context.getString(R.string.skipped), notification.actions[0].title.toString());
        assertReceiverBroadcast(notification.actions[0].actionIntent, DISMISSED_ACTION);
        assertEquals(context.getString(R.string.snooze), notification.actions[1].title.toString());
        assertReceiverBroadcast(notification.actions[1].actionIntent, SNOOZE_ACTION);
        assertReceiverBroadcast(notification.deleteIntent, TAKEN_ACTION);
        assertEveryPendingIntentImmutable(notification);
    }

    @Test
    public void testVariableAmountAndCustomSnoozeTargetNotificationActionAlias() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString("snooze_duration", "-1").apply();
        reminder.variableAmount = true;
        Notification notification = showReminderNotification();

        assertEquals(2, notification.actions.length);
        assertNotificationActionActivityIntent(notification.actions[0].actionIntent, "VARIABLE_AMOUNT");
        assertEquals(reminder.amount, shadow(notification.actions[0].actionIntent).getSavedIntent().getStringExtra(EXTRA_AMOUNT));
        assertNotificationActionActivityIntent(notification.actions[1].actionIntent, "CUSTOM_SNOOZE");
        assertEquals(REMINDER_ID, shadow(notification.actions[1].actionIntent).getSavedIntent().getIntExtra(EXTRA_REMINDER_ID, -1));
        assertReceiverBroadcast(notification.deleteIntent, DISMISSED_ACTION);
        assertEveryPendingIntentImmutable(notification);
    }

    @Test
    public void testNotificationActionAliasIsNonExportedMainActivityAlias() throws PackageManager.NameNotFoundException {
        PackageManager packageManager = context.getPackageManager();
        ComponentName alias = new ComponentName(context.getPackageName(), Notifications.NOTIFICATION_ACTION_ACTIVITY);

        ActivityInfo aliasInfo = packageManager.getActivityInfo(alias, PackageManager.MATCH_DISABLED_COMPONENTS);
        assertEquals(MainActivity.class.getName(), aliasInfo.targetActivity);
        assertFalse(aliasInfo.exported, "NotificationActionActivity must not be exported");
        ActivityInfo mainActivityInfo = packageManager.getActivityInfo(new ComponentName(context, MainActivity.class), 0);
        assertTrue(mainActivityInfo.exported);
    }

    @Test
    public void testNotificationActionIntentsMatchAliasFilter() {
        // Android 16 "Safer Intents" (strict, opt-in matching): an explicit intent with a custom action must match
        // an intent-filter of its target component. Resolve both activity intents through the PackageManager
        // and check that the alias filter (and not the exported launcher filter) is what matches them.
        PackageManager packageManager = context.getPackageManager();
        Intent variableAmount = ReminderProcessor.getVariableAmountActionIntent(context, REMINDER_EVENT_ID, "1");
        Intent customSnooze = ReminderProcessor.getCustomSnoozeActionIntent(context, REMINDER_ID, REMINDER_EVENT_ID, 1);
        for (Intent intent : new Intent[]{variableAmount, customSnooze}) {
            Intent implicitIntent = new Intent(intent.getAction()).setPackage(context.getPackageName());
            List<ResolveInfo> resolved = packageManager.queryIntentActivities(implicitIntent, PackageManager.MATCH_DEFAULT_ONLY);
            assertEquals(1, resolved.size(), intent.getAction() + " must resolve to exactly one component");
            assertEquals(Notifications.NOTIFICATION_ACTION_ACTIVITY, resolved.get(0).activityInfo.name);
            assertEquals(MainActivity.class.getName(), resolved.get(0).activityInfo.targetActivity);
        }
        // The launcher filter of MainActivity itself must stay MAIN/LAUNCHER only
        List<IntentFilter> mainFilters = ((org.robolectric.shadows.ShadowPackageManager) Shadow.extract(packageManager))
                .getIntentFiltersForActivity(new ComponentName(context, MainActivity.class));
        assertEquals(1, mainFilters.size());
        assertTrue(mainFilters.get(0).hasAction(Intent.ACTION_MAIN));
        assertFalse(mainFilters.get(0).hasAction("VARIABLE_AMOUNT"));
        assertFalse(mainFilters.get(0).hasAction("CUSTOM_SNOOZE"));
    }

    @Test
    public void testReceiverActionsDeclaredInManifest() {
        // Android 16 "Safer Intents": every custom action sent to ReminderProcessor must be declared in its intent-filter
        List<IntentFilter> filters = ((org.robolectric.shadows.ShadowPackageManager) Shadow.extract(context.getPackageManager()))
                .getIntentFiltersForReceiver(new ComponentName(context, ReminderProcessor.class));
        assertEquals(1, filters.size());
        for (String action : new String[]{REMINDER_ACTION, TAKEN_ACTION, DISMISSED_ACTION, SNOOZE_ACTION}) {
            assertTrue(filters.get(0).hasAction(action), action + " is sent to ReminderProcessor but not declared in its intent-filter");
        }
    }
}
