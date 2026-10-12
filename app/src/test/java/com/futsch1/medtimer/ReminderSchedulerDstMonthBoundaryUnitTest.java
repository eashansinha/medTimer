package com.futsch1.medtimer;

import static com.futsch1.medtimer.TestHelper.assertReminded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.futsch1.medtimer.database.FullMedicine;
import com.futsch1.medtimer.database.Reminder;
import com.futsch1.medtimer.database.ReminderEvent;
import com.futsch1.medtimer.reminders.scheduling.ReminderScheduler;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * EE-30 / IMP-20: next-alarm computation of StandardScheduling across DST changes and month,
 * leap-day and year boundaries. Zone is fixed to Europe/Berlin so the expected UTC instants are
 * independent of the host's zone.
 */
class ReminderSchedulerDstMonthBoundaryUnitTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private ReminderScheduler.TimeAccess timeAccess;
    private FullMedicine medicine;
    private Reminder reminder;

    private List<ScheduledReminder> schedule(LocalDate today, int timeInMinutes, List<ReminderEvent> events) {
        timeAccess = mock(ReminderScheduler.TimeAccess.class);
        when(timeAccess.localDate()).thenReturn(today);
        when(timeAccess.systemZone()).thenReturn(BERLIN);
        medicine = TestHelper.buildFullMedicine(1, "DST");
        reminder = TestHelper.buildReminder(1, 1, "1", timeInMinutes, 1);
        medicine.reminders.add(reminder);
        List<FullMedicine> medicines = new ArrayList<>();
        medicines.add(medicine);
        return new ReminderScheduler(timeAccess).schedule(medicines, events);
    }

    private static ReminderEvent eventAt(String isoInstant) {
        return TestHelper.buildReminderEvent(1, Instant.parse(isoInstant).getEpochSecond());
    }

    @Test
    void test_offsetChangesAcrossSpringForward() {
        // 2024-03-30 is CET (+1), 2024-03-31 is CEST (+2) after 02:00
        List<ScheduledReminder> before = schedule(LocalDate.of(2024, 3, 30), 8 * 60, new ArrayList<>());
        assertReminded(before, Instant.parse("2024-03-30T07:00:00Z"), medicine.medicine, reminder);

        List<ScheduledReminder> after = schedule(LocalDate.of(2024, 3, 31), 8 * 60, new ArrayList<>());
        assertReminded(after, Instant.parse("2024-03-31T06:00:00Z"), medicine.medicine, reminder);
    }

    @Test
    void test_reminderInSpringForwardGapIsShiftedForward() {
        // 02:30 does not exist on 2024-03-31 in Berlin; java.time moves it to 03:30 CEST = 01:30Z
        List<ScheduledReminder> scheduled = schedule(LocalDate.of(2024, 3, 31), 2 * 60 + 30, new ArrayList<>());
        assertReminded(scheduled, Instant.parse("2024-03-31T01:30:00Z"), medicine.medicine, reminder);
        assertEquals(1, scheduled.size());
    }

    @Test
    void test_reminderInFallBackOverlapUsesEarlierOffset() {
        // 02:30 exists twice on 2024-10-27 in Berlin; java.time picks the earlier offset (CEST, +2) = 00:30Z
        List<ScheduledReminder> scheduled = schedule(LocalDate.of(2024, 10, 27), 2 * 60 + 30, new ArrayList<>());
        assertReminded(scheduled, Instant.parse("2024-10-27T00:30:00Z"), medicine.medicine, reminder);
    }

    @Test
    void test_nextReminderAfterFallBackDayIs25HoursLater() {
        // Reminded on 2024-10-26 08:00 CEST (06:00Z) -> next on 2024-10-27 08:00 CET (07:00Z): 25 h gap
        List<ReminderEvent> events = new ArrayList<>();
        events.add(eventAt("2024-10-26T06:00:00Z"));
        List<ScheduledReminder> scheduled = schedule(LocalDate.of(2024, 10, 26), 8 * 60, events);
        assertReminded(scheduled, Instant.parse("2024-10-27T07:00:00Z"), medicine.medicine, reminder);
        assertEquals(25 * 3600, scheduled.get(0).timestamp().getEpochSecond() - Instant.parse("2024-10-26T06:00:00Z").getEpochSecond());
    }

    @Test
    void test_monthBoundary() {
        List<ReminderEvent> events = new ArrayList<>();
        events.add(eventAt("2024-01-31T07:00:00Z"));
        List<ScheduledReminder> scheduled = schedule(LocalDate.of(2024, 1, 31), 8 * 60, events);
        assertReminded(scheduled, Instant.parse("2024-02-01T07:00:00Z"), medicine.medicine, reminder);
    }

    @Test
    void test_leapDay() {
        List<ReminderEvent> events = new ArrayList<>();
        events.add(eventAt("2024-02-28T07:00:00Z"));
        List<ScheduledReminder> scheduled = schedule(LocalDate.of(2024, 2, 28), 8 * 60, events);
        assertReminded(scheduled, Instant.parse("2024-02-29T07:00:00Z"), medicine.medicine, reminder);

        events.clear();
        events.add(eventAt("2023-02-28T07:00:00Z"));
        scheduled = schedule(LocalDate.of(2023, 2, 28), 8 * 60, events);
        assertReminded(scheduled, Instant.parse("2023-03-01T07:00:00Z"), medicine.medicine, reminder);
    }

    @Test
    void test_yearBoundary() {
        List<ReminderEvent> events = new ArrayList<>();
        events.add(eventAt("2023-12-31T07:00:00Z"));
        List<ScheduledReminder> scheduled = schedule(LocalDate.of(2023, 12, 31), 8 * 60, events);
        assertReminded(scheduled, Instant.parse("2024-01-01T07:00:00Z"), medicine.medicine, reminder);
    }
}
