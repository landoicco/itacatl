package dev.licaza.itacatl.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * When a menu is served: a date and a window in the business's local time, like the schedule. Rules
 * that compare fields (the window ends after it starts) are checked by the service.
 */
public record MenuRequestDTO(
    @NotNull LocalDate serviceDate, @NotNull LocalTime startsAt, @NotNull LocalTime endsAt) {}
