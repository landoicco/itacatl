package dev.licaza.itacatl.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// RFC 9457 Problem Details (application/problem+json). The "code" property is stable, so the
// client translates it instead of showing the English detail to users. Spring Security's errors
// arrive here too: SecurityConfig hands them over from its filters.
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ResourceNotFoundException.class)
  public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), ex.getCode());
  }

  // An anonymous request to an endpoint that needs a signed-in user
  @ExceptionHandler(InsufficientAuthenticationException.class)
  public ProblemDetail handleAuthenticationRequired(InsufficientAuthenticationException ex) {
    return problem(
        HttpStatus.UNAUTHORIZED,
        "Authentication required",
        "Sign in to use this endpoint",
        "AUTHENTICATION_REQUIRED");
  }

  // A failed sign-in. Unknown email, wrong password and disabled account share one code, so the
  // response never reveals which accounts exist
  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleInvalidCredentials(AuthenticationException ex) {
    return problem(
        HttpStatus.UNAUTHORIZED,
        "Invalid credentials",
        "Email or password is incorrect",
        "INVALID_CREDENTIALS");
  }

  // A state-changing request without a valid CSRF token; the client should reload the token
  @ExceptionHandler(CsrfException.class)
  public ProblemDetail handleCsrf(CsrfException ex) {
    return problem(
        HttpStatus.FORBIDDEN, "Invalid CSRF token", ex.getMessage(), "CSRF_TOKEN_INVALID");
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    return problem(
        HttpStatus.FORBIDDEN,
        "Access denied",
        "You do not have permission to do this",
        "ACCESS_DENIED");
  }

  private static ProblemDetail problem(
      HttpStatus status, String title, String detail, String code) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setProperty("code", code);
    return problem;
  }
}
