package dev.licaza.itacatl.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Admits only requests that went through this deployment's CloudFront distribution, which adds a
 * secret header to every request it forwards. The instance's security group admits all of
 * CloudFront, including distributions of other AWS accounts, so the address alone proves nothing.
 * Registered only when the secret is set, in AWS (OriginVerificationConfig). See docs/security.md.
 */
public class OriginVerificationFilter extends OncePerRequestFilter {

  /** The header CloudFront adds; the frontend Terraform module sets the same name. */
  public static final String HEADER = "X-Origin-Verify";

  private static final Logger log = LoggerFactory.getLogger(OriginVerificationFilter.class);

  private final byte[] secret;

  public OriginVerificationFilter(String secret) {
    // An empty secret would admit any request with an empty header: refuse to start instead
    if (secret.isBlank()) {
      throw new IllegalArgumentException("itacatl.origin.secret is set but blank");
    }
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(HEADER);
    // Constant time, so the response time does not tell how much of a guess was right
    if (header != null && MessageDigest.isEqual(secret, header.getBytes(StandardCharsets.UTF_8))) {
      chain.doFilter(request, response);
      return;
    }
    log.warn("❌ Request without CloudFront's origin header rejected");
    // No Problem Detail: only clients that skipped CloudFront see it, never the app
    response.sendError(HttpStatus.FORBIDDEN.value());
  }

  // The instance checks its own health on localhost, without CloudFront
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/actuator/health");
  }
}
