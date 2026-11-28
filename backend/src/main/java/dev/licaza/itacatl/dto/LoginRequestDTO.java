package dev.licaza.itacatl.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Credentials sent by the sign-in form. A missing field is a 400 VALIDATION_FAILED before the rate
 * limiter or the password check see the request.
 */
public record LoginRequestDTO(@NotBlank String email, @NotBlank String password) {

  // A record prints every field by default; this keeps the password out of logs and debuggers
  @Override
  public String toString() {
    return "LoginRequestDTO[email=" + email + ", password=****]";
  }
}
