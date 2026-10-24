package dev.licaza.itacatl.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** A menu item's price and quantity for that service. The catalog item does not change. */
public record UpdateMenuItemDTO(
    @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal price,
    @NotNull @PositiveOrZero Integer quantity) {}
