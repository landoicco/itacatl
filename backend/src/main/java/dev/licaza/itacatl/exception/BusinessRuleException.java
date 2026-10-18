package dev.licaza.itacatl.exception;

import java.time.LocalDate;
import lombok.Getter;

/**
 * A well-formed request that breaks a business rule, such as a menu window that ends before it
 * starts. Answered with 422 and a stable code the client translates. Field-level format errors are
 * Bean Validation's job instead (VALIDATION_FAILED).
 */
@Getter
public class BusinessRuleException extends RuntimeException {

  private final String code;

  private BusinessRuleException(String code, String message) {
    super(message);
    this.code = code;
  }

  public static BusinessRuleException invalidTimeWindow() {
    return new BusinessRuleException("INVALID_TIME_WINDOW", "A menu must end after it starts");
  }

  // "Past" in the business's own time zone. Also for changing a past menu: it is history
  public static BusinessRuleException menuDateInPast(LocalDate date) {
    return new BusinessRuleException(
        "MENU_DATE_IN_PAST", "Cannot plan or change a menu for " + date);
  }

  public static BusinessRuleException itemArchived(Long itemId) {
    return new BusinessRuleException(
        "ITEM_ARCHIVED", "Item " + itemId + " is archived; unarchive it to offer it");
  }

  public static BusinessRuleException itemAlreadyInMenu(Long itemId) {
    return new BusinessRuleException(
        "ITEM_ALREADY_IN_MENU", "Item " + itemId + " is already in this menu");
  }
}
