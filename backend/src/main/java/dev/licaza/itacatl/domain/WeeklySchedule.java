package dev.licaza.itacatl.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A business's weekly schedule plus its date exceptions, in local time. Pure domain logic with no
 * database or clock access, so every rule is unit-tested with fixed dates.
 */
public class WeeklySchedule {

  // How far ahead to look for the next opening before calling a business closed indefinitely
  static final int HORIZON_DAYS = 14;

  private final Map<DayOfWeek, List<TimeWindow>> weekly;
  private final Map<LocalDate, ScheduleException> exceptions;

  public WeeklySchedule(Collection<BusinessHours> hours, Collection<ScheduleException> exceptions) {
    this.weekly =
        hours.stream()
            .collect(
                Collectors.groupingBy(
                    BusinessHours::getDayOfWeek,
                    Collectors.mapping(BusinessHours::window, Collectors.toList())));
    this.exceptions =
        exceptions.stream()
            .collect(Collectors.toMap(ScheduleException::getDate, exception -> exception));
  }

  /** The windows on a date, sorted: an exception replaces the weekly schedule for that day. */
  public List<TimeWindow> windowsOn(LocalDate date) {
    ScheduleException exception = exceptions.get(date);
    List<TimeWindow> windows =
        exception != null
            ? exception.window().stream().toList()
            : weekly.getOrDefault(date.getDayOfWeek(), List.of());
    return windows.stream().sorted(Comparator.comparing(TimeWindow::opensAt)).toList();
  }

  public OpeningStatus statusAt(LocalDateTime now) {
    LocalDate today = now.toLocalDate();
    LocalTime time = now.toLocalTime();

    for (TimeWindow window : windowsOn(today)) {
      if (window.contains(time)) {
        return OpeningStatus.openUntil(today.atTime(window.closesAt()));
      }
      if (window.opensAt().isAfter(time)) {
        return OpeningStatus.closedUntil(today.atTime(window.opensAt()));
      }
    }

    for (int offset = 1; offset <= HORIZON_DAYS; offset++) {
      LocalDate date = today.plusDays(offset);
      List<TimeWindow> windows = windowsOn(date);
      if (!windows.isEmpty()) {
        return OpeningStatus.closedUntil(date.atTime(windows.getFirst().opensAt()));
      }
    }
    return OpeningStatus.closedUntil(null);
  }
}
