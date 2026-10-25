package dev.licaza.itacatl.exception;

import java.time.LocalDate;
import java.time.LocalTime;
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

  // Its window has closed in the business's own time zone, today included. Also for changing an
  // ended menu: it is history
  public static BusinessRuleException menuEnded(LocalDate date, LocalTime endsAt) {
    return new BusinessRuleException(
        "MENU_ENDED", "Cannot plan or change a menu that ended on " + date + " at " + endsAt);
  }

  public static BusinessRuleException itemArchived(Long itemId) {
    return new BusinessRuleException(
        "ITEM_ARCHIVED", "Item " + itemId + " is archived; unarchive it to offer it");
  }

  public static BusinessRuleException itemAlreadyInMenu(Long itemId) {
    return new BusinessRuleException(
        "ITEM_ALREADY_IN_MENU", "Item " + itemId + " is already in this menu");
  }

  // Rescheduling and deleting are for drafts: customers may already be planning around a
  // published menu
  public static BusinessRuleException menuNotDraft(Long menuId) {
    return new BusinessRuleException("MENU_NOT_DRAFT", "Menu " + menuId + " is already published");
  }

  public static BusinessRuleException menuEmpty(Long menuId) {
    return new BusinessRuleException(
        "MENU_EMPTY", "Menu " + menuId + " has no items that are not archived");
  }
}
