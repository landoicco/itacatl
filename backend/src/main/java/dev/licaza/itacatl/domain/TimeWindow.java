package dev.licaza.itacatl.domain;

import java.time.LocalTime;

/** A local opening window within one day: [opensAt, closesAt). */
public record TimeWindow(LocalTime opensAt, LocalTime closesAt) {

  public TimeWindow {
    if (!closesAt.isAfter(opensAt)) {
      throw new IllegalArgumentException(
          "A window must close after it opens (overnight windows are not supported)");
    }
  }

  public boolean contains(LocalTime time) {
    return !time.isBefore(opensAt) && time.isBefore(closesAt);
  }
}
