package dev.licaza.itacatl.dto;

import dev.licaza.itacatl.domain.OpeningStatus;
import java.util.List;

/**
 * A business as listed on the marketplace's home page. {@code timeZone} lets the client tell
 * whether a local opening time is today or tomorrow for that business.
 *
 * @param featuredItems names of the items in the business's current or next published menu within a
 *     week, sorted by name; empty when it has none
 */
public record BusinessSummaryDTO(
    String slug,
    String name,
    String description,
    String timeZone,
    OpeningStatus openingStatus,
    List<String> featuredItems) {}
