package com.futsch1.medtimer.widgets

import android.app.Application
import org.robolectric.RuntimeEnvironment
import com.futsch1.medtimer.R
import com.futsch1.medtimer.ScheduledReminder
import com.futsch1.medtimer.database.FullMedicine
import com.futsch1.medtimer.database.Medicine
import com.futsch1.medtimer.database.Reminder
import com.futsch1.medtimer.database.ReminderEvent
import com.futsch1.medtimer.helpers.TimeHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [35])
class WidgetLineProviderTest {
    private lateinit var context: Application

    @BeforeEach
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    private fun todayAt(hour: Int, minute: Int): Instant =
        LocalDate.now().atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault())
            .toInstant()

    private fun scheduledReminder(
        name: String,
        amount: String,
        timestamp: Instant
    ): ScheduledReminder {
        val fullMedicine = FullMedicine()
        fullMedicine.medicine = Medicine(name)
        fullMedicine.reminders = listOf()
        fullMedicine.tags = listOf()
        val reminder = Reminder(1)
        reminder.amount = amount
        return ScheduledReminder(fullMedicine, reminder, timestamp)
    }

    private fun reminderEvent(
        name: String,
        amount: String,
        status: ReminderEvent.ReminderStatus,
        timestamp: Instant
    ): ReminderEvent {
        val event = ReminderEvent()
        event.medicineName = name
        event.amount = amount
        event.status = status
        event.remindedTimestamp = timestamp.epochSecond
        return event
    }

    private fun time(timestamp: Instant): String =
        TimeHelper.toLocalizedTimeString(context, timestamp.epochSecond)

    private fun date(timestamp: Instant): String =
        TimeHelper.toLocalizedDateString(context, timestamp.epochSecond)

    // ---- NextRemindersLineProvider ----

    @Test
    fun nextRemindersLineIsFormattedAsTimeAmountAndName() {
        val at = todayAt(8, 30)
        val provider = NextRemindersLineProvider(context) {
            listOf(scheduledReminder("Aspirin", "2", at))
        }

        assertEquals("${time(at)}: 2 Aspirin", provider.getWidgetLine(0))
    }

    @Test
    fun nextRemindersLineOnAnotherDayIsPrefixedWithTheDate() {
        val tomorrow = todayAt(9, 0).plusSeconds(24 * 60 * 60)
        val provider = NextRemindersLineProvider(context) {
            listOf(scheduledReminder("Ibuprofen", "1", tomorrow))
        }

        assertEquals("${date(tomorrow)} ${time(tomorrow)}: 1 Ibuprofen", provider.getWidgetLine(0))
    }

    @Test
    fun nextRemindersKeepTheOrderOfTheScheduledList() {
        val first = todayAt(7, 0)
        val second = todayAt(12, 0)
        val third = todayAt(20, 0)
        val provider = NextRemindersLineProvider(context) {
            listOf(
                scheduledReminder("First", "1", first),
                scheduledReminder("Second", "2", second),
                scheduledReminder("Third", "3", third)
            )
        }

        assertEquals("${time(first)}: 1 First", provider.getWidgetLine(0))
        assertEquals("${time(second)}: 2 Second", provider.getWidgetLine(1))
        assertEquals("${time(third)}: 3 Third", provider.getWidgetLine(2))
    }

    @Test
    fun nextRemindersEmptyStateYieldsEmptyLines() {
        val provider = NextRemindersLineProvider(context) { emptyList() }

        for (line in 0 until WidgetImpl.LARGE_WIDGET_LINES) {
            assertEquals("", provider.getWidgetLine(line))
        }
    }

    @Test
    fun nextRemindersLinesBeyondTheListAreEmpty() {
        val provider = NextRemindersLineProvider(context) {
            listOf(scheduledReminder("Only", "1", todayAt(8, 0)))
        }

        assertTrue(provider.getWidgetLine(0).isNotEmpty())
        assertEquals("", provider.getWidgetLine(1))
        assertEquals("", provider.getWidgetLine(WidgetImpl.LARGE_WIDGET_LINES))
    }

    @Test
    fun nextRemindersLongListStaysAccessibleBeyondTheWidgetLineLimit() {
        val provider = NextRemindersLineProvider(context) {
            (0 until 10).map { scheduledReminder("Med$it", "$it", todayAt(it + 6, 0)) }
        }

        // The provider itself does not truncate; WidgetImpl renders only LARGE_WIDGET_LINES.
        assertEquals("${time(todayAt(15, 0))}: 9 Med9", provider.getWidgetLine(9))
        assertEquals("", provider.getWidgetLine(10))
    }

    // ---- LatestRemindersLineProvider ----

    @Test
    fun latestRemindersTakenEventShowsTakenStatus() {
        val at = todayAt(8, 0)
        val provider = LatestRemindersLineProvider(context) {
            listOf(reminderEvent("Aspirin", "2", ReminderEvent.ReminderStatus.TAKEN, at))
        }

        val taken = context.getString(R.string.taken)
        assertEquals("${time(at)}: $taken 2 Aspirin", provider.getWidgetLine(0))
    }

    @Test
    fun latestRemindersSkippedEventShowsSkippedStatus() {
        val at = todayAt(8, 0)
        val provider = LatestRemindersLineProvider(context) {
            listOf(reminderEvent("Aspirin", "2", ReminderEvent.ReminderStatus.SKIPPED, at))
        }

        val skipped = context.getString(R.string.skipped)
        assertEquals("${time(at)}: $skipped 2 Aspirin", provider.getWidgetLine(0))
    }

    @Test
    fun latestRemindersRaisedEventHasNoStatusPrefix() {
        val at = todayAt(8, 0)
        val provider = LatestRemindersLineProvider(context) {
            listOf(reminderEvent("Aspirin", "2", ReminderEvent.ReminderStatus.RAISED, at))
        }

        assertEquals("${time(at)}: 2 Aspirin", provider.getWidgetLine(0))
    }

    @Test
    fun latestRemindersEventOnAnotherDayIsPrefixedWithTheDate() {
        val yesterday = todayAt(18, 0).minusSeconds(24 * 60 * 60)
        val provider = LatestRemindersLineProvider(context) {
            listOf(reminderEvent("Vitamin", "1", ReminderEvent.ReminderStatus.TAKEN, yesterday))
        }

        val taken = context.getString(R.string.taken)
        assertEquals(
            "${date(yesterday)} ${time(yesterday)}: $taken 1 Vitamin",
            provider.getWidgetLine(0)
        )
    }

    @Test
    fun latestRemindersKeepTheOrderOfTheLoadedList() {
        val newest = todayAt(20, 0)
        val older = todayAt(8, 0)
        val provider = LatestRemindersLineProvider(context) {
            listOf(
                reminderEvent("Newest", "1", ReminderEvent.ReminderStatus.TAKEN, newest),
                reminderEvent("Older", "1", ReminderEvent.ReminderStatus.TAKEN, older)
            )
        }

        assertTrue(provider.getWidgetLine(0).endsWith("Newest"))
        assertTrue(provider.getWidgetLine(1).endsWith("Older"))
    }

    @Test
    fun latestRemindersEmptyStateYieldsEmptyLines() {
        val provider = LatestRemindersLineProvider(context) { emptyList() }

        for (line in 0 until WidgetImpl.LARGE_WIDGET_LINES) {
            assertEquals("", provider.getWidgetLine(line))
        }
    }

    @Test
    fun latestRemindersLinesBeyondTheListAreEmpty() {
        val provider = LatestRemindersLineProvider(context) {
            (0 until 10).map {
                reminderEvent("Med$it", "1", ReminderEvent.ReminderStatus.TAKEN, todayAt(it + 6, 0))
            }
        }

        assertTrue(provider.getWidgetLine(9).endsWith("Med9"))
        assertEquals("", provider.getWidgetLine(10))
    }

    // ---- getDayString ----

    @Test
    fun dayStringIsEmptyForTodayAndDatePlusSpaceOtherwise() {
        val today = todayAt(10, 0)
        val tomorrow = today.plusSeconds(24 * 60 * 60)

        assertEquals("", getDayString(context, today.epochSecond))
        assertEquals("${date(tomorrow)} ", getDayString(context, tomorrow.epochSecond))
    }
}
