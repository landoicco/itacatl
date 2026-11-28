package dev.licaza.itacatl.exception;

import java.time.Duration;
import lombok.Getter;

/**
 * Too many sign-in attempts from one address or for one email (SignInRateLimiter). Answered with
 * 429 TOO_MANY_ATTEMPTS and a Retry-After header. Not an AuthenticationException, so it is never
 * mistaken for wrong credentials.
 */
@Getter
public class TooManyAttemptsException extends RuntimeException {

  private final Duration retryAfter;

  public TooManyAttemptsException(Duration retryAfter) {
    super("Too many sign-in attempts; try again in " + retryAfter.toSeconds() + " seconds");
    this.retryAfter = retryAfter;
  }
}
