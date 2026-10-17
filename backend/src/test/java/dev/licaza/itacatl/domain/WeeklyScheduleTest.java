package dev.licaza.itacatl.domain;

import static java.time.DayOfWeek.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

// Pure unit tests: no Spring, no database, every date fixed
class WeeklyScheduleTest {

  // A Saturday
  private static final LocalDate DATE = LocalDate.of(2026, 10, 17);

  private static BusinessHours hours(DayOfWeek day, String opens, String closes) {
    return new BusinessHours(1L, day, LocalTime.parse(opens), LocalTime.parse(closes));
  }

  private static ScheduleException exception(LocalDate date, String opens, String closes) {
    ScheduleException exception = new ScheduleException();
    exception.setDate(date);
    exception.setOpensAt(opens == null ? null : LocalTime.parse(opens));
    exception.setClosesAt(closes == null ? null : LocalTime.parse(closes));
    return exception;
  }

  private static LocalDateTime at(String time) {
    return DATE.atTime(LocalTime.parse(time));
  }

  @Test
  void openInsideAWindow_ReportsWhenItCloses() {
    var schedule = new WeeklySchedule(List.of(hours(SATURDAY, "12:00", "20:00")), List.of());

    OpeningStatus status = schedule.statusAt(at("15:00"));

    assertEquals(OpeningStatus.State.OPEN, status.state());
    assertEquals(at("20:00"), status.closesAt());
    assertNull(status.opensAt());
  }

  @Test
  void beforeOpening_ReportsTodaysOpening() {
    var schedule = new WeeklySchedule(List.of(hours(SATURDAY, "12:00", "20:00")), List.of());

    OpeningStatus status = schedule.statusAt(at("11:59"));

    assertEquals(OpeningStatus.State.CLOSED, status.state());
    assertEquals(at("12:00"), status.opensAt());
  }

  @Test
  void closingTimeIsExclusive() {
    var schedule = new WeeklySchedule(List.of(hours(SATURDAY, "12:00", "20:00")), List.of());

    assertEquals(OpeningStatus.State.CLOSED, schedule.statusAt(at("20:00")).state());
  }

  @Test
  void betweenSplitShifts_ReportsTheSecondShift() {
    var schedule =
        new WeeklySchedule(
            List.of(hours(SATURDAY, "17:00", "21:00"), hours(SATURDAY, "08:00", "12:00")),
            List.of());

    OpeningStatus status = schedule.statusAt(at("13:00"));

    assertEquals(OpeningStatus.State.CLOSED, status.state());
    assertEquals(at("17:00"), status.opensAt());
  }

  @Test
  void afterClosing_ReportsTheNextOpenDay() {
    var schedule =
        new WeeklySchedule(
            List.of(hours(SATURDAY, "12:00", "20:00"), hours(TUESDAY, "13:00", "19:00")),
            List.of());

    OpeningStatus status = schedule.statusAt(at("21:00"));

    // Sunday and Monday are closed, so the next opening is Tuesday
    assertEquals(DATE.plusDays(3).atTime(13, 0), status.opensAt());
  }

  @Test
  void closedException_SkipsThatDay() {
    var schedule =
        new WeeklySchedule(
            List.of(hours(SATURDAY, "12:00", "20:00"), hours(SUNDAY, "12:00", "20:00")),
            List.of(exception(DATE, null, null)));

    OpeningStatus status = schedule.statusAt(at("15:00"));

    assertEquals(OpeningStatus.State.CLOSED, status.state());
    assertEquals(DATE.plusDays(1).atTime(12, 0), status.opensAt());
  }

  @Test
  void specialHoursException_ReplacesTheWeeklyHours() {
    var schedule =
        new WeeklySchedule(
            List.of(hours(SATURDAY, "12:00", "20:00")), List.of(exception(DATE, "09:00", "13:00")));

    OpeningStatus status = schedule.statusAt(at("10:00"));

    assertEquals(OpeningStatus.State.OPEN, status.state());
    assertEquals(at("13:00"), status.closesAt());
  }

  @Test
  void noOpeningWithinTheHorizon_IsClosedWithoutDate() {
    var schedule = new WeeklySchedule(List.of(), List.of());

    OpeningStatus status = schedule.statusAt(at("12:00"));

    assertEquals(OpeningStatus.State.CLOSED, status.state());
    assertNull(status.opensAt());
  }

  @Test
  void overnightWindow_IsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TimeWindow(LocalTime.of(20, 0), LocalTime.of(2, 0)));
  }
}
