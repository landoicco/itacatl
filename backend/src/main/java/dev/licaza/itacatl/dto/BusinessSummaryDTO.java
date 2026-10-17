package dev.licaza.itacatl.dto;

import dev.licaza.itacatl.domain.OpeningStatus;

/** A business as listed on the marketplace's home page. */
public record BusinessSummaryDTO(
    String slug, String name, String description, OpeningStatus openingStatus) {}
