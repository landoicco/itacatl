package dev.licaza.itacatl.dto;

import dev.licaza.itacatl.domain.OpeningStatus;

/**
 * A business as listed on the marketplace's home page. {@code timeZone} lets the client tell
 * whether a local opening time is today or tomorrow for that business.
 */
public record BusinessSummaryDTO(
    String slug, String name, String description, String timeZone, OpeningStatus openingStatus) {}
