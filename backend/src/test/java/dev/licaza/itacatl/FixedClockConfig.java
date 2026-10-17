package dev.licaza.itacatl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Freezes "now" so time-dependent rules (open, closed, upcoming menus) are deterministic. */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfig {

  public static final ZoneId MEXICO_CITY = ZoneId.of("America/Mexico_City");

  // Saturday 17 October 2026, 11:30 in Mexico City (UTC-6, no daylight saving time since 2022)
  public static final Instant NOW = Instant.parse("2026-10-17T17:30:00Z");

  public static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

  @Bean
  Clock clock() {
    return Clock.fixed(NOW, ZoneOffset.UTC);
  }
}
