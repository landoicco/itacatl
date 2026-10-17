package dev.licaza.itacatl.dto;

import dev.licaza.itacatl.domain.OpeningStatus;
import java.util.List;

/**
 * A business's public profile. Dates and times are local to {@code timeZone}, so the client shows
 * them as the business sees them, wherever the customer is.
 */
public record BusinessDetailDTO(
    String slug,
    String name,
    String description,
    String timeZone,
    boolean acceptsPickup,
    boolean acceptsDineIn,
    OpeningStatus openingStatus,
    List<BusinessHoursDTO> weeklyHours,
    List<MenuSummaryDTO> upcomingMenus) {}
