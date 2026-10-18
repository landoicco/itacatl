package dev.licaza.itacatl.domain;

/** What a user can do in a business. See the roles in docs/product-spec.md. */
public enum MembershipRole {
  // Manages the business: profile, schedule, catalog, menus and staff
  OWNER,
  // Runs the day: availability and orders
  STAFF
}
