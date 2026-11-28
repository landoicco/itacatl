package dev.licaza.itacatl.security;

import static org.junit.jupiter.api.Assertions.*;

import dev.licaza.itacatl.exception.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SignInRateLimiterTest {

  private static final Duration WINDOW = Duration.ofMinutes(15);

  private final MovableClock clock = new MovableClock();

  @Test
  void attemptsOverTheEmailsLimit_AreRejectedWithAWait() {
    SignInRateLimiter limiter = limiter(10, 2, false);

    limiter.check("ana@example.com", from("10.0.0.1"));
    limiter.check("ana@example.com", from("10.0.0.1"));
    TooManyAttemptsException ex =
        assertThrows(
            TooManyAttemptsException.class,
            () -> limiter.check("ana@example.com", from("10.0.0.1")));

    assertTrue(ex.getRetryAfterSeconds() >= 1, "the client must be told how long to wait");
    assertTrue(ex.getRetryAfterSeconds() <= WINDOW.toSeconds());
    assertTrue(ex.getMessage().contains(ex.getRetryAfterSeconds() + " seconds"));
  }

  @Test
  void anEmailLimitedFromOneAddress_StillSignsInFromAnother() {
    SignInRateLimiter limiter = limiter(10, 1, false);

    limiter.check("ana@example.com", from("203.0.113.9"));
    assertThrows(
        TooManyAttemptsException.class,
        () -> limiter.check("ana@example.com", from("203.0.113.9")));

    // Whoever spent the attempts cannot lock Ana out of her own account elsewhere
    assertDoesNotThrow(() -> limiter.check("ana@example.com", from("198.51.100.7")));
  }

  @Test
  void anEmail_SharesItsBucketWhateverItsCaseAndSpaces() {
    SignInRateLimiter limiter = limiter(10, 1, false);

    limiter.check("ana@example.com", from("10.0.0.1"));

    assertThrows(
        TooManyAttemptsException.class, () -> limiter.check(" Ana@Example.com ", from("10.0.0.1")));
  }

  @Test
  void anotherEmail_HasItsOwnBucket() {
    SignInRateLimiter limiter = limiter(10, 1, false);

    limiter.check("ana@example.com", from("10.0.0.1"));

    assertDoesNotThrow(() -> limiter.check("luis@example.com", from("10.0.0.1")));
  }

  @Test
  void oneAddressTryingManyEmails_IsLimited() {
    SignInRateLimiter limiter = limiter(2, 10, false);

    limiter.check("a@example.com", from("10.0.0.1"));
    limiter.check("b@example.com", from("10.0.0.1"));

    assertThrows(
        TooManyAttemptsException.class, () -> limiter.check("c@example.com", from("10.0.0.1")));
    assertDoesNotThrow(() -> limiter.check("c@example.com", from("10.0.0.2")));
  }

  @Test
  void anAttemptRefusedForItsEmail_DoesNotSpendTheAddresss() {
    SignInRateLimiter limiter = limiter(3, 1, false);

    limiter.check("ana@example.com", from("10.0.0.1"));
    for (int i = 0; i < 5; i++) {
      assertThrows(
          TooManyAttemptsException.class, () -> limiter.check("ana@example.com", from("10.0.0.1")));
    }

    // Only the first attempt took a token from the address: two are left for other accounts
    limiter.check("luis@example.com", from("10.0.0.1"));
    limiter.check("eva@example.com", from("10.0.0.1"));
    assertThrows(
        TooManyAttemptsException.class, () -> limiter.check("sol@example.com", from("10.0.0.1")));
  }

  @Test
  void ipv6Addresses_AreCountedByTheirSlash64() {
    SignInRateLimiter limiter = limiter(1, 10, false);

    limiter.check("a@example.com", from("2001:db8:1:2::1"));

    // Another address in the same /64 is the same client
    assertThrows(
        TooManyAttemptsException.class,
        () -> limiter.check("b@example.com", from("2001:db8:1:2:ffff::9")));
    assertDoesNotThrow(() -> limiter.check("b@example.com", from("2001:db8:1:3::1")));
  }

  @Test
  void attempts_ComeBackOverTheWindow() {
    SignInRateLimiter limiter = limiter(10, 1, false);
    limiter.check("ana@example.com", from("10.0.0.1"));

    clock.advance(WINDOW);

    assertDoesNotThrow(() -> limiter.check("ana@example.com", from("10.0.0.1")));
  }

  @Test
  void behindCloudFront_TheLastForwardedAddressIsTheClient() {
    SignInRateLimiter limiter = limiter(1, 10, true);
    // The first entry is whatever the client sent; CloudFront appended the last one
    limiter.check("a@example.com", forwardedFor("203.0.113.9", "198.51.100.7"));

    assertThrows(
        TooManyAttemptsException.class,
        () -> limiter.check("b@example.com", forwardedFor("192.0.2.1", "198.51.100.7")));
    assertDoesNotThrow(
        () -> limiter.check("c@example.com", forwardedFor("203.0.113.9", "198.51.100.8")));
  }

  @Test
  void withoutTrust_TheForwardedHeaderIsIgnored() {
    SignInRateLimiter limiter = limiter(1, 10, false);
    limiter.check("a@example.com", forwardedFor("198.51.100.7"));

    // A different forged address does not buy another attempt from the same machine
    assertThrows(
        TooManyAttemptsException.class,
        () -> limiter.check("b@example.com", forwardedFor("198.51.100.8")));
  }

  private SignInRateLimiter limiter(int perAddress, int perEmail, boolean trustForwardedFor) {
    return new SignInRateLimiter(perAddress, perEmail, WINDOW, trustForwardedFor, clock);
  }

  private static MockHttpServletRequest from(String address) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
    request.setRemoteAddr(address);
    return request;
  }

  // Every request arrives from CloudFront's address; only the header tells clients apart
  private static MockHttpServletRequest forwardedFor(String... addresses) {
    MockHttpServletRequest request = from("130.176.0.1");
    request.addHeader("X-Forwarded-For", String.join(", ", addresses));
    return request;
  }

  private static final class MovableClock extends Clock {

    private Instant now = Instant.parse("2026-10-17T17:30:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }
}
