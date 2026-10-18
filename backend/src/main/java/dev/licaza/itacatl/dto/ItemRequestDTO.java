package dev.licaza.itacatl.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * A catalog item as the owner writes it, to create or replace one. Limits match the columns of the
 * item table, so invalid input is a 400 with field errors instead of a database error.
 *
 * @param alcoholic required, so a form never leaves it out by mistake
 */
public record ItemRequestDTO(
    @NotBlank @Size(max = 120) String name,
    @Size(max = 2000) String description,
    @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal suggestedPrice,
    @NotNull Boolean alcoholic) {}
