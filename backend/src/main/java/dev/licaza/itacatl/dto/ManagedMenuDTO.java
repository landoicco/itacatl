package dev.licaza.itacatl.dto;

import dev.licaza.itacatl.domain.MenuStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * A menu as its business manages it: drafts included, every item with its catalog state. Dates and
 * times are local to the business.
 *
 * @param items sorted by name
 */
public record ManagedMenuDTO(
    Long id,
    LocalDate serviceDate,
    LocalTime startsAt,
    LocalTime endsAt,
    MenuStatus status,
    List<ManagedMenuItem> items) {

  /**
   * @param itemId the catalog item, which is also how the menu's endpoints address it
   * @param archived whether the item was archived in the catalog after it was added
   */
  public record ManagedMenuItem(
      Long itemId, String name, BigDecimal price, int quantity, boolean archived) {}
}
