package dev.licaza.itacatl.exception;

import java.time.Duration;
import lombok.Getter;

/**
 * Too many sign-in attempts from one address, or for one email from one address
 * (SignInRateLimiter). Answered with 429 TOO_MANY_ATTEMPTS and a Retry-After header. Not an
 * AuthenticationException, so it is never mistaken for wrong credentials.
 */
@Getter
public class TooManyAttemptsException extends RuntimeException {

  /**
   * Whole seconds until the next attempt is allowed, rounded up so a client that waits that long is
   * never rejected again. The message and the Retry-After header both use it.
   */
  private final long retryAfterSeconds;

  public TooManyAttemptsException(Duration wait) {
    this(Math.max(1, (wait.toMillis() + 999) / 1000));
  }

  private TooManyAttemptsException(long retryAfterSeconds) {
    super("Too many sign-in attempts; try again in " + retryAfterSeconds + " seconds");
    this.retryAfterSeconds = retryAfterSeconds;
  }
}
