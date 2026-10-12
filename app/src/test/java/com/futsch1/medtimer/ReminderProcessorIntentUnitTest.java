package com.futsch1.medtimer;

import static com.futsch1.medtimer.ActivityCodes.DISMISSED_ACTION;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_NOTIFICATION_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_DATE;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_EVENT_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_TIME;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_SNOOZE_TIME;
import static com.futsch1.medtimer.ActivityCodes.REMINDER_ACTION;
import static com.futsch1.medtimer.ActivityCodes.SNOOZE_ACTION;
import static com.futsch1.medtimer.ActivityCodes.TAKEN_ACTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import android.content.Context;
import android.content.Intent;

import org.robolectric.RuntimeEnvironment;

import com.futsch1.medtimer.reminders.ReminderProcessor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.robolectric.annotation.Config;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import tech.apter.junit.jupiter.robolectric.RobolectricExtension;

/**
 * EE-30 / IMP-07 + IMP-22: notification-action intents are explicit, target ReminderProcessor, carry
 * the expected extras, and every action is declared in the receiver's manifest intent filter
 * (Safer Intents, Android 16/17).
 */
@ExtendWith(RobolectricExtension.class)
@Config(sdk = 34)
@SuppressWarnings("java:S5786")
public class ReminderProcessorIntentUnitTest {
    private Context context;

    @BeforeEach
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    @Test
    public void test_reminderActionIntent() {
        LocalDateTime dateTime = LocalDateTime.of(2024, 3, 31, 8, 30);
        Intent intent = ReminderProcessor.getReminderAction(context, 3, 30, dateTime);
        assertEquals(REMINDER_ACTION, intent.getAction());
        assertEquals(ReminderProcessor.class.getName(), intent.getComponent().getClassName());
        assertEquals(3, intent.getIntExtra(EXTRA_REMINDER_ID, -1));
        assertEquals(30, intent.getIntExtra(EXTRA_REMINDER_EVENT_ID, -1));
        assertEquals(dateTime.toLocalDate().toEpochDay(), intent.getLongExtra(EXTRA_REMINDER_DATE, -1));
        assertEquals(8 * 3600 + 30 * 60, intent.getIntExtra(EXTRA_REMINDER_TIME, -1));
    }

    @Test
    public void test_reminderActionIntentWithoutDateTime() {
        Intent intent = ReminderProcessor.getReminderAction(context, 0, 0, null);
        assertEquals(0, intent.getLongExtra(EXTRA_REMINDER_DATE, -1));
        assertEquals(0, intent.getIntExtra(EXTRA_REMINDER_TIME, -1));
    }

    @Test
    public void test_snoozeIntent() {
        Intent intent = ReminderProcessor.getSnoozeIntent(context, 3, 30, 300, 45);
        assertEquals(SNOOZE_ACTION, intent.getAction());
        assertEquals(ReminderProcessor.class.getName(), intent.getComponent().getClassName());
        assertEquals(3, intent.getIntExtra(EXTRA_REMINDER_ID, -1));
        assertEquals(30, intent.getIntExtra(EXTRA_REMINDER_EVENT_ID, -1));
        assertEquals(300, intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1));
        assertEquals(45, intent.getIntExtra(EXTRA_SNOOZE_TIME, -1));
    }

    @Test
    public void test_takenAndDismissedIntents() {
        Intent taken = ReminderProcessor.getTakenActionIntent(context, 31);
        assertEquals(TAKEN_ACTION, taken.getAction());
        assertEquals(ReminderProcessor.class.getName(), taken.getComponent().getClassName());
        assertEquals(31, taken.getIntExtra(EXTRA_REMINDER_EVENT_ID, -1));

        Intent dismissed = ReminderProcessor.getDismissedActionIntent(context, 32);
        assertEquals(DISMISSED_ACTION, dismissed.getAction());
        assertEquals(ReminderProcessor.class.getName(), dismissed.getComponent().getClassName());
        assertEquals(32, dismissed.getIntExtra(EXTRA_REMINDER_EVENT_ID, -1));
    }

    @Test
    public void test_allReminderProcessorActionsDeclaredInManifestFilter() throws Exception {
        File manifest = new File("src/main/AndroidManifest.xml");
        if (!manifest.exists()) {
            manifest = new File("app/src/main/AndroidManifest.xml");
        }
        assertTrue(manifest.exists(), "AndroidManifest.xml not found from " + new File(".").getAbsolutePath());
        Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest).getDocumentElement();
        NodeList receivers = root.getElementsByTagName("receiver");
        Set<String> declared = new HashSet<>();
        for (int i = 0; i < receivers.getLength(); i++) {
            Element receiver = (Element) receivers.item(i);
            if (receiver.getAttribute("android:name").endsWith("ReminderProcessor")) {
                NodeList actions = receiver.getElementsByTagName("action");
                for (int j = 0; j < actions.getLength(); j++) {
                    declared.add(((Element) actions.item(j)).getAttribute("android:name"));
                }
            }
        }
        assertNotNull(declared);
        List<String> produced = List.of(
                ReminderProcessor.getReminderAction(context, 1, 1, null).getAction(),
                ReminderProcessor.getSnoozeIntent(context, 1, 1, 1, 15).getAction(),
                ReminderProcessor.getTakenActionIntent(context, 1).getAction(),
                ReminderProcessor.getDismissedActionIntent(context, 1).getAction());
        for (String action : produced) {
            assertTrue(declared.contains(action), "Action " + action + " produced by ReminderProcessor is not declared in its manifest intent filter (Safer Intents)");
        }
    }

    @Test
    public void test_mainActivityAndWidgetReceiverActionsDeclaredInManifestFilter() throws Exception {
        Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(findManifest()).getDocumentElement();
        Set<String> activityActions = declaredActions(root, "activity", "MainActivity");
        Set<String> widgetReceiverActions = declaredActions(root, "receiver", "WidgetUpdateReceiver");

        List<String> producedForMainActivity = List.of(
                ReminderProcessor.getVariableAmountActionIntent(context, 1, "2").getAction(),
                ReminderProcessor.getCustomSnoozeActionIntent(context, 1, 1, 1).getAction());
        for (String action : producedForMainActivity) {
            assertTrue(activityActions.contains(action), "Action " + action + " targets MainActivity but is not declared in its manifest intent filter (Safer Intents)");
        }
        assertTrue(widgetReceiverActions.contains("com.futsch1.medtimer.NEXT_REMINDER_WIDGET_UPDATE"),
                "RescheduleWork.updateNextReminderWidget action is not declared in the WidgetUpdateReceiver intent filter (Safer Intents)");
    }

    private static File findManifest() {
        File manifest = new File("src/main/AndroidManifest.xml");
        if (!manifest.exists()) {
            manifest = new File("app/src/main/AndroidManifest.xml");
        }
        assertTrue(manifest.exists(), "AndroidManifest.xml not found from " + new File(".").getAbsolutePath());
        return manifest;
    }

    private static Set<String> declaredActions(Element root, String tag, String componentSuffix) {
        NodeList components = root.getElementsByTagName(tag);
        Set<String> declared = new HashSet<>();
        for (int i = 0; i < components.getLength(); i++) {
            Element component = (Element) components.item(i);
            if (component.getAttribute("android:name").endsWith(componentSuffix)) {
                NodeList actions = component.getElementsByTagName("action");
                for (int j = 0; j < actions.getLength(); j++) {
                    declared.add(((Element) actions.item(j)).getAttribute("android:name"));
                }
            }
        }
        return declared;
    }
}
