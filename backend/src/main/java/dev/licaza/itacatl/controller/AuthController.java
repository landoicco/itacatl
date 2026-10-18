package dev.licaza.itacatl.controller;

import dev.licaza.itacatl.dto.CurrentUserDTO;
import dev.licaza.itacatl.dto.LoginRequestDTO;
import dev.licaza.itacatl.security.AccountPrincipal;
import dev.licaza.itacatl.security.SessionLogin;
import dev.licaza.itacatl.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Sign-in and the current user. Sign-out is Spring Security's LogoutFilter on POST /api/auth/logout
 * (see SecurityConfig). How the pieces fit: docs/security.md.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final SessionLogin sessionLogin;
  private final AccountService accountService;

  public AuthController(SessionLogin sessionLogin, AccountService accountService) {
    this.sessionLogin = sessionLogin;
    this.accountService = accountService;
  }

  @PostMapping("/login")
  public ResponseEntity<CurrentUserDTO> login(
      @RequestBody LoginRequestDTO credentials,
      HttpServletRequest request,
      HttpServletResponse response) {
    AccountPrincipal principal =
        sessionLogin.signIn(credentials.email(), credentials.password(), request, response);
    return ResponseEntity.ok(accountService.getCurrentUser(principal.getUserId()));
  }

  // Anonymous callers get 401 AUTHENTICATION_REQUIRED from SecurityConfig, which the app reads as
  // "signed out"
  @GetMapping("/me")
  public ResponseEntity<CurrentUserDTO> me(@AuthenticationPrincipal AccountPrincipal principal) {
    return ResponseEntity.ok(accountService.getCurrentUser(principal.getUserId()));
  }
}
