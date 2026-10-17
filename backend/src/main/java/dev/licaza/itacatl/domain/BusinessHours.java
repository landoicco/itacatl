package dev.licaza.itacatl.domain;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One opening window of the weekly schedule. A day can have several (split shifts). */
@Entity
@Table(name = "business_hours")
@Getter
@Setter
@NoArgsConstructor
public class BusinessHours {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long tenantId;

  @Convert(converter = DayOfWeekConverter.class)
  private DayOfWeek dayOfWeek;

  private LocalTime opensAt;

  private LocalTime closesAt;

  public BusinessHours(Long tenantId, DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
    this.tenantId = tenantId;
    this.dayOfWeek = dayOfWeek;
    this.opensAt = opensAt;
    this.closesAt = closesAt;
  }

  public TimeWindow window() {
    return new TimeWindow(opensAt, closesAt);
  }
}
