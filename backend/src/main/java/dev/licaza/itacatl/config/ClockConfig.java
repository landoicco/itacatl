package dev.licaza.itacatl.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

  // Code that needs "now" asks this bean instead of calling LocalDateTime.now(), so tests can fix
  // the time ("Saturday at 11:59") and check time-dependent rules deterministically
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
