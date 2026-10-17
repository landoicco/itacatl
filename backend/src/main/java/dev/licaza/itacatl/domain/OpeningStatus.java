package dev.licaza.itacatl.domain;

import java.time.LocalDateTime;

/**
 * Whether a business is open at a given moment, in its local time. The API returns these values,
 * not sentences, so the client can say "opens tomorrow at 12:00" in the user's language.
 *
 * @param closesAt when the current window ends, if open
 * @param opensAt when the next window starts, if closed and one exists within the horizon
 */
public record OpeningStatus(State state, LocalDateTime closesAt, LocalDateTime opensAt) {

  public enum State {
    OPEN,
    CLOSED
  }

  public static OpeningStatus openUntil(LocalDateTime closesAt) {
    return new OpeningStatus(State.OPEN, closesAt, null);
  }

  public static OpeningStatus closedUntil(LocalDateTime opensAt) {
    return new OpeningStatus(State.CLOSED, null, opensAt);
  }
}
