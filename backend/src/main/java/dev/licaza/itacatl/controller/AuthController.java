package dev.licaza.itacatl.controller;

import dev.licaza.itacatl.dto.CurrentUserDTO;
import dev.licaza.itacatl.dto.LoginRequestDTO;
import dev.licaza.itacatl.security.AccountPrincipal;
import dev.licaza.itacatl.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

/**
 * Sign-in and the current user. Sign-out is Spring Security's LogoutFilter on POST /api/auth/logout
 * (see SecurityConfig). How the pieces fit: docs/security.md.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private static final Logger log = LoggerFactory.getLogger(AuthController.class);

  private final SecurityContextHolderStrategy contextHolder =
      SecurityContextHolder.getContextHolderStrategy();

  private final AuthenticationManager authenticationManager;
  private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
  private final SecurityContextRepository securityContextRepository;
  private final AccountService accountService;

  public AuthController(
      AuthenticationManager authenticationManager,
      SessionAuthenticationStrategy sessionAuthenticationStrategy,
      SecurityContextRepository securityContextRepository,
      AccountService accountService) {
    this.authenticationManager = authenticationManager;
    this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    this.securityContextRepository = securityContextRepository;
    this.accountService = accountService;
  }

  // The same steps a filter-based login runs, done by hand because the client sends JSON. The
  // request and response are needed for the session, which is why this stays in the web layer.
  @PostMapping("/login")
  public ResponseEntity<CurrentUserDTO> login(
      @RequestBody LoginRequestDTO credentials,
      HttpServletRequest request,
      HttpServletResponse response) {
    Authentication authentication;
    try {
      authentication =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(
                  credentials.email(), credentials.password()));
    } catch (AuthenticationException ex) {
      // No email in the log: it is personal data, and users sometimes type their password in it
      log.warn("❌ Sign-in failed: {}", ex.getClass().getSimpleName());
      // GlobalExceptionHandler answers 401 INVALID_CREDENTIALS
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
    return ResponseEntity.ok(accountService.getCurrentUser(principal.getUserId()));
  }

  // Anonymous callers get 401 AUTHENTICATION_REQUIRED from SecurityConfig, which the app reads as
  // "signed out"
  @GetMapping("/me")
  public ResponseEntity<CurrentUserDTO> me(@AuthenticationPrincipal AccountPrincipal principal) {
    return ResponseEntity.ok(accountService.getCurrentUser(principal.getUserId()));
  }
}
