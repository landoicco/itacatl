package dev.licaza.itacatl.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.DayOfWeek;

/**
 * Stores {@link DayOfWeek} as its ISO-8601 number (1 = Monday ... 7 = Sunday). JPA's default
 * ordinal mapping would store 0-6, which reads wrong in SQL and breaks if the enum order changed.
 */
@Converter
public class DayOfWeekConverter implements AttributeConverter<DayOfWeek, Short> {

  @Override
  public Short convertToDatabaseColumn(DayOfWeek day) {
    return day == null ? null : (short) day.getValue();
  }

  @Override
  public DayOfWeek convertToEntityAttribute(Short value) {
    return value == null ? null : DayOfWeek.of(value);
  }
}
