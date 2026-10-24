package dev.licaza.itacatl.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Offers a catalog item in a menu.
 *
 * @param price that day's price; the item's suggested price when omitted
 * @param quantity portions available in this service
 */
public record AddMenuItemDTO(
    @NotNull Long itemId,
    @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal price,
    @NotNull @PositiveOrZero Integer quantity) {}
