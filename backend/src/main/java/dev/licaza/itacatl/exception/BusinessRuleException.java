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

  // "Past" in the business's own time zone
  public static BusinessRuleException menuDateInPast(LocalDate date) {
    return new BusinessRuleException("MENU_DATE_IN_PAST", "Cannot plan a menu for " + date);
  }
}
