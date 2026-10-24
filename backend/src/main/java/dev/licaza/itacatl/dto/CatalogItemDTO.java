package dev.licaza.itacatl.dto;

import java.math.BigDecimal;

/** A catalog item as its business sees it, archived ones included. */
public record CatalogItemDTO(
    Long id,
    String name,
    String description,
    BigDecimal suggestedPrice,
    boolean alcoholic,
    boolean archived) {}
