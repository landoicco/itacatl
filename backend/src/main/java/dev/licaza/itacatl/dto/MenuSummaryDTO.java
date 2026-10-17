package dev.licaza.itacatl.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record MenuSummaryDTO(
    Long id, LocalDate serviceDate, LocalTime startsAt, LocalTime endsAt) {}
