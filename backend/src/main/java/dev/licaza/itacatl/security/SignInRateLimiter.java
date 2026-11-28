package dev.licaza.itacatl.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.licaza.itacatl.domain.AppUser;
import dev.licaza.itacatl.exception.TooManyAttemptsException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.EstimationProbe;
import io.github.bucket4j.TimeMeter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limits sign-in attempts with token buckets (Bucket4j): one per client address, which slows a
 * single machine trying many accounts, and one per email from each address, which slows guessing
 * one account. The second is keyed by address too, so nobody can lock an owner out of their own
 * account from somewhere else. Every attempt takes a token from both, successful or not, and tokens
 * come back gradually over the window. The buckets live in memory, which is enough for one
 * instance; they are lost when it is replaced. See docs/security.md.
 */
@Component
public class SignInRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(SignInRateLimiter.class);

  // Bounds the memory a flood of keys can take, per cache. Caffeine evicts the keys least likely
  // to be used again (W-TinyLFU), and an evicted key only gets a fresh bucket. Separate caches, so
  // a flood of one kind never evicts the other
  private static final int MAX_KEYS = 10_000;

  // Only an IP literal is parsed, so a malformed header never triggers a DNS lookup
  private static final Pattern IP_LITERAL = Pattern.compile("[0-9A-Fa-f:.]+");

  private final int perAddress;
  private final int perEmail;
  private final Duration window;
  private final boolean trustForwardedFor;
  private final TimeMeter timeMeter;
  private final Cache<String, Bucket> addressBuckets;
  private final Cache<String, Bucket> emailBuckets;

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
    this.addressBuckets = newCache();
    this.emailBuckets = newCache();
  }

  /**
   * Takes one attempt from the client's address and from the email at that address, only if both
   * have one left, so an attempt refused by one bucket does not also drain the other.
   *
   * @throws TooManyAttemptsException if either has none left; GlobalExceptionHandler answers 429
   *     TOO_MANY_ATTEMPTS with Retry-After
   */
  public void check(String email, HttpServletRequest request) {
    String address = clientAddress(request);
    Bucket byAddress = addressBuckets.get(address, ignored -> newBucket(perAddress));
    // Normalized like the stored email, so " Ana@Example.com " and "ana@example.com" share a bucket
    Bucket byEmail =
        emailBuckets.get(
            AppUser.normalizeEmail(email) + " " + address, ignored -> newBucket(perEmail));

    EstimationProbe addressProbe = byAddress.estimateAbilityToConsume(1);
    EstimationProbe emailProbe = byEmail.estimateAbilityToConsume(1);
    if (!addressProbe.canBeConsumed() || !emailProbe.canBeConsumed()) {
      // The kind only: addresses and emails are personal data
      log.warn(
          "❌ Sign-in attempts limited for {}",
          addressProbe.canBeConsumed() ? "an email" : "an address");
      long waitNanos =
          Math.max(addressProbe.getNanosToWaitForRefill(), emailProbe.getNanosToWaitForRefill());
      throw new TooManyAttemptsException(Duration.ofNanos(waitNanos));
    }
    // Two concurrent attempts may both pass the estimate and take the last token each: at most one
    // attempt over the limit, which does not matter here
    byAddress.tryConsume(1);
    byEmail.tryConsume(1);
  }

  // A key idle for a whole window has a full bucket again, so it can be forgotten
  private Cache<String, Bucket> newCache() {
    return Caffeine.newBuilder().maximumSize(MAX_KEYS).expireAfterAccess(window).build();
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
      return network(forwardedFor.substring(forwardedFor.lastIndexOf(',') + 1).strip());
    }
    return network(request.getRemoteAddr());
  }

  // An IPv6 client usually gets a whole /64 and can pick any address in it, so its addresses are
  // counted as one. IPv4 addresses stay whole: many clients may share one behind a NAT
  private static String network(String address) {
    if (address == null || !IP_LITERAL.matcher(address).matches()) {
      return String.valueOf(address);
    }
    try {
      if (InetAddress.getByName(address) instanceof Inet6Address ipv6) {
        byte[] prefix = new byte[8];
        System.arraycopy(ipv6.getAddress(), 0, prefix, 0, prefix.length);
        return HexFormat.of().formatHex(prefix) + "::/64";
      }
    } catch (UnknownHostException ex) {
      // Not a valid literal after all: count it as given
    }
    return address;
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
