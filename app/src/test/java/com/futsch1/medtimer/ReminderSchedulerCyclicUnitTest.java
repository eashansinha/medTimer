package com.futsch1.medtimer;

import static com.futsch1.medtimer.TestHelper.assertReminded;
import static com.futsch1.medtimer.TestHelper.on;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.futsch1.medtimer.database.FullMedicine;
import com.futsch1.medtimer.database.Reminder;
import com.futsch1.medtimer.database.ReminderEvent;
import com.futsch1.medtimer.reminders.scheduling.ReminderScheduler;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * EE-30 / IMP-20: cyclic reminders (consecutiveDays active, pauseDays pause) across the cycle boundary.
 * Cycle: 2 days on, 1 day off, starting at epoch day 0 -> active days 0,1,3,4,6,7,...; pause days 2,5,...
 */
class ReminderSchedulerCyclicUnitTest {
    private FullMedicine medicine;
    private Reminder reminder;

    private List<ScheduledReminder> schedule(long todayEpochDay, List<ReminderEvent> events) {
        ReminderScheduler.TimeAccess timeAccess = mock(ReminderScheduler.TimeAccess.class);
        when(timeAccess.localDate()).thenReturn(LocalDate.ofEpochDay(todayEpochDay));
        when(timeAccess.systemZone()).thenReturn(ZoneId.of("Z"));
        medicine = TestHelper.buildFullMedicine(1, "Cyclic");
        reminder = TestHelper.buildReminder(1, 1, "1", 60, 1);
        reminder.consecutiveDays = 2;
        reminder.pauseDays = 1;
        reminder.cycleStartDay = 0;
        medicine.reminders.add(reminder);
        List<FullMedicine> medicines = new ArrayList<>();
        medicines.add(medicine);
        return new ReminderScheduler(timeAccess).schedule(medicines, events);
    }

    @Test
    void test_activeDayIsScheduledToday() {
        // epoch day 0 -> on(1, ...)
        assertReminded(schedule(0, new ArrayList<>()), on(1, 60), medicine.medicine, reminder);
        assertReminded(schedule(1, new ArrayList<>()), on(2, 60), medicine.medicine, reminder);
    }

    @Test
    void test_pauseDayIsSkippedToNextActiveDay() {
        // epoch day 2 is the pause day -> next reminder on epoch day 3 (= on(4, ...))
        assertReminded(schedule(2, new ArrayList<>()), on(4, 60), medicine.medicine, reminder);
    }

    @Test
    void test_afterLastActiveDayNextIsAfterPause() {
        // Reminded on epoch day 1 (second active day) -> skip day 2 -> next on epoch day 3
        List<ReminderEvent> events = new ArrayList<>();
        events.add(TestHelper.buildReminderEvent(1, on(2, 60).getEpochSecond()));
        assertReminded(schedule(1, events), on(4, 60), medicine.medicine, reminder);
    }

    @Test
    void test_secondCycleWrapsCorrectly() {
        // epoch day 5 is the pause day of the second cycle -> next on epoch day 6 (= on(7, ...))
        assertReminded(schedule(5, new ArrayList<>()), on(7, 60), medicine.medicine, reminder);
        // epoch day 6 and 7 are active again
        assertReminded(schedule(6, new ArrayList<>()), on(7, 60), medicine.medicine, reminder);
        assertReminded(schedule(7, new ArrayList<>()), on(8, 60), medicine.medicine, reminder);
    }
}
