import { ApiError } from "./client";

/*
 * API error codes the UI has a message for, each with a key in the apiErrors catalog
 * (docs/domain-model.md#errors, docs/security.md#a-rejected-request). Any other code shows
 * UNKNOWN_ERROR, so a new backend code is never shown raw.
 */
const KNOWN_CODES = [
  "NETWORK_ERROR",
  "BUSINESS_NOT_FOUND",
  "MENU_NOT_FOUND",
  "ITEM_NOT_FOUND",
  "MENU_ITEM_NOT_FOUND",
  "AUTHENTICATION_REQUIRED",
  "INVALID_CREDENTIALS",
  "CSRF_TOKEN_INVALID",
  "ACCESS_DENIED",
  "VALIDATION_FAILED",
  "MALFORMED_REQUEST",
  "INVALID_TIME_WINDOW",
  "MENU_ENDED",
  "MENU_NOT_DRAFT",
  "MENU_EMPTY",
  "ITEM_ARCHIVED",
  "ITEM_ALREADY_IN_MENU",
] as const;

export type ErrorCode = (typeof KNOWN_CODES)[number] | "UNKNOWN_ERROR";

/** The code to translate for any error, API or not */
export function errorCode(error: unknown): ErrorCode {
  return error instanceof ApiError && (KNOWN_CODES as readonly string[]).includes(error.code)
    ? (error.code as ErrorCode)
    : "UNKNOWN_ERROR";
}

// Bean Validation constraints the forms have a message for; others show "Invalid"
const KNOWN_FIELD_CODES = ["NotBlank", "NotNull", "Size", "PositiveOrZero", "Digits"] as const;

export type FieldErrorCode = (typeof KNOWN_FIELD_CODES)[number] | "Invalid";

/** The field errors of a VALIDATION_FAILED error, by field name; empty for any other error */
export function fieldErrors(error: unknown): Record<string, FieldErrorCode> {
  if (!(error instanceof ApiError)) return {};
  return Object.fromEntries(
    error.fieldErrors.map(({ field, code }) => [
      field,
      (KNOWN_FIELD_CODES as readonly string[]).includes(code)
        ? (code as FieldErrorCode)
        : "Invalid",
    ])
  );
}
