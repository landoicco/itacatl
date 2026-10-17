package dev.licaza.itacatl.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** A published menu with its items, as a customer sees it. */
public record MenuDetailDTO(
    Long id,
    String businessSlug,
    String businessName,
    LocalDate serviceDate,
    LocalTime startsAt,
    LocalTime endsAt,
    List<MenuItemDTO> items) {}
