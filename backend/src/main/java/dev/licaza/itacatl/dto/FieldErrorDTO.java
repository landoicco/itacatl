package dev.licaza.itacatl.dto;

/**
 * One invalid field of a request body, inside a VALIDATION_FAILED Problem Detail.
 *
 * @param field the JSON property, e.g. {@code name} or {@code items[0].price}
 * @param code the Bean Validation constraint that failed, e.g. {@code NotBlank}, which the client
 *     translates next to the input
 */
public record FieldErrorDTO(String field, String code) {}
