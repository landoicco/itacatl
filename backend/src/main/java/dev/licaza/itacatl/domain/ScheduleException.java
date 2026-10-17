package dev.licaza.itacatl.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Replaces the weekly schedule on one date: closed all day, or open with special hours. */
@Entity
@Table(name = "schedule_exception")
@Getter
@Setter
@NoArgsConstructor
public class ScheduleException {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long tenantId;

  private LocalDate date;

  // Both null means closed all day
  private LocalTime opensAt;

  private LocalTime closesAt;

  public Optional<TimeWindow> window() {
    return opensAt == null ? Optional.empty() : Optional.of(new TimeWindow(opensAt, closesAt));
  }
}
