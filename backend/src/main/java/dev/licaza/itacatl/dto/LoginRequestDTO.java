package dev.licaza.itacatl.dto;

/** Credentials sent by the sign-in form. */
public record LoginRequestDTO(String email, String password) {

  // A record prints every field by default; this keeps the password out of logs and debuggers
  @Override
  public String toString() {
    return "LoginRequestDTO[email=" + email + ", password=****]";
  }
}
