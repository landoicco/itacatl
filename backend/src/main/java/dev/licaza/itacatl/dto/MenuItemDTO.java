package dev.licaza.itacatl.dto;

import java.math.BigDecimal;

/**
 * @param available portions left to reserve. Equal to the menu item's quantity until orders exist
 *     (slice 4), when it becomes quantity - sold - active holds
 */
public record MenuItemDTO(
    Long id, String name, String description, BigDecimal price, int available, boolean alcoholic) {}
