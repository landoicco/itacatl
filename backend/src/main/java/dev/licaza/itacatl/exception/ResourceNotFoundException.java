package dev.licaza.itacatl.exception;

import lombok.Getter;

/**
 * A requested resource does not exist, or is not visible to the caller. Carries a stable error code
 * that clients translate, instead of showing the English message to users.
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {

  private final String code;

  private ResourceNotFoundException(String code, String message) {
    super(message);
    this.code = code;
  }

  public static ResourceNotFoundException business(String slug) {
    return new ResourceNotFoundException("BUSINESS_NOT_FOUND", "No business with slug " + slug);
  }

  public static ResourceNotFoundException menu(Long id) {
    return new ResourceNotFoundException("MENU_NOT_FOUND", "No published menu with ID " + id);
  }
}
