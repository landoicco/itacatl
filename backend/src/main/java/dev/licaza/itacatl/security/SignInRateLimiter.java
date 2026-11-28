package dev.licaza.itacatl.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.licaza.itacatl.domain.AppUser;
import dev.licaza.itacatl.exception.TooManyAttemptsException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limits sign-in attempts with token buckets (Bucket4j), one per client address and one per email:
 * the first slows a single machine trying many accounts, the second many machines trying one
 * account. Every attempt takes a token, successful or not, and tokens come back gradually over the
 * window. The buckets live in memory, which is enough for one instance; they are lost when it is
 * replaced. See docs/security.md.
 */
@Component
public class SignInRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(SignInRateLimiter.class);

  // Bounds the memory a flood of addresses or emails can take; the least recently used key is
  // forgotten first, which only gives that key a fresh bucket
  private static final int MAX_KEYS = 10_000;

  private final int perAddress;
  private final int perEmail;
  private final Duration window;
  private final boolean trustForwardedFor;
  private final TimeMeter timeMeter;
  private final Cache<String, Bucket> buckets;

  public SignInRateLimiter(
      @Value("${itacatl.sign-in.attempts-per-address:10}") int perAddress,
      @Value("${itacatl.sign-in.attempts-per-email:5}") int perEmail,
      @Value("${itacatl.sign-in.window:15m}") Duration window,
      @Value("${itacatl.sign-in.trust-forwarded-for:false}") boolean trustForwardedFor,
      Clock clock) {
    this.perAddress = perAddress;
    this.perEmail = perEmail;
    this.window = window;
    this.trustForwardedFor = trustForwardedFor;
    this.timeMeter = new ClockTimeMeter(clock);
    // A key idle for a whole window has a full bucket again, so it can be forgotten
    this.buckets = Caffeine.newBuilder().maximumSize(MAX_KEYS).expireAfterAccess(window).build();
  }

  /**
   * Takes one attempt from the client's address and from the email.
   *
   * @throws TooManyAttemptsException if either has none left; GlobalExceptionHandler answers 429
   *     TOO_MANY_ATTEMPTS with Retry-After
   */
  public void check(String email, HttpServletRequest request) {
    consume("address:" + clientAddress(request), perAddress, "address");
    // Normalized like the stored email, so " Ana@Example.com " and "ana@example.com" share a bucket
    consume("email:" + AppUser.normalizeEmail(email), perEmail, "email");
  }

  private void consume(String key, int capacity, String kind) {
    Bucket bucket = buckets.get(key, ignored -> newBucket(capacity));
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed()) {
      // The kind only: addresses and emails are personal data
      log.warn("❌ Sign-in attempts limited for an {}", kind);
      throw new TooManyAttemptsException(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }
  }

  private Bucket newBucket(int capacity) {
    return Bucket.builder()
        .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, window))
        .withCustomTimePrecision(timeMeter)
        .build();
  }

  // Behind CloudFront every request comes from one of its addresses. CloudFront appends the
  // viewer's address to X-Forwarded-For, so its last entry is the one CloudFront saw; earlier ones
  // are whatever the client sent. Trusted only in AWS, where OriginVerificationFilter makes sure
  // the request came through CloudFront
  private String clientAddress(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (trustForwardedFor && forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.substring(forwardedFor.lastIndexOf(',') + 1).strip();
    }
    return request.getRemoteAddr();
  }

  // Bucket4j reads time through the injected Clock, like the rest of the app, so tests can move it
  private record ClockTimeMeter(Clock clock) implements TimeMeter {
    @Override
    public long currentTimeNanos() {
      return Duration.ofMillis(clock.millis()).toNanos();
    }

    @Override
    public boolean isWallClockBased() {
      return true;
    }
  }
}
