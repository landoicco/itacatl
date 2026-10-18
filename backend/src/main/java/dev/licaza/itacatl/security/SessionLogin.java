package dev.licaza.itacatl.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Signs a user in and starts their session. These are the steps a filter-based login (formLogin)
 * runs, done here because the client sends JSON to an endpoint. Kept in this package so every piece
 * of Spring Security's mechanics lives next to the configuration it depends on. See
 * docs/security.md.
 */
@Component
public class SessionLogin {

  private static final Logger log = LoggerFactory.getLogger(SessionLogin.class);

  private final SecurityContextHolderStrategy contextHolder =
      SecurityContextHolder.getContextHolderStrategy();

  private final AuthenticationManager authenticationManager;
  private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
  private final SecurityContextRepository securityContextRepository;

  public SessionLogin(
      AuthenticationManager authenticationManager,
      SessionAuthenticationStrategy sessionAuthenticationStrategy,
      SecurityContextRepository securityContextRepository) {
    this.authenticationManager = authenticationManager;
    this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    this.securityContextRepository = securityContextRepository;
  }

  /**
   * Checks the credentials and stores the signed-in user in the session. The request and response
   * are needed because the session ID changes and cookies are written.
   *
   * @return the signed-in account
   * @throws AuthenticationException if the credentials are wrong or the account is disabled;
   *     GlobalExceptionHandler answers 401 INVALID_CREDENTIALS
   */
  public AccountPrincipal signIn(
      String email, String password, HttpServletRequest request, HttpServletResponse response) {
    Authentication authentication;
    try {
      authentication =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(email, password));
    } catch (AuthenticationException ex) {
      // No email in the log: it is personal data, and users sometimes type their password in it
      log.warn("❌ Sign-in failed: {}", ex.getClass().getSimpleName());
      throw ex;
    }

    // New session ID and CSRF token, then the SecurityContext into the session, which Spring
    // Session stores in PostgreSQL
    sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
    SecurityContext context = contextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    contextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);

    AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
    log.info("✅ User {} signed in", principal.getUserId());
    return principal;
  }
}
