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

  // A session whose account was deleted after sign-in
  public static ResourceNotFoundException account(Long id) {
    return new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "No account with ID " + id);
  }

  // Also for an item of another business: its ID is not revealed to exist
  public static ResourceNotFoundException item(Long id) {
    return new ResourceNotFoundException("ITEM_NOT_FOUND", "No item with ID " + id);
  }

  public static ResourceNotFoundException menu(Long id) {
    // The public API answers this for drafts too, so customers cannot discover them
    return new ResourceNotFoundException("MENU_NOT_FOUND", "No menu with ID " + id);
  }
}
