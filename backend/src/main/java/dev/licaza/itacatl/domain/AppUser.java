package dev.licaza.itacatl.domain;

import jakarta.persistence.*;
import java.util.Locale;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Someone who signs in to manage businesses: an owner or a staff member. What they can do in each
 * business comes from their {@link Membership}s. Customers have no account.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Always lowercase (the database checks it), so sign-in ignores case
  private String email;

  // Prefixed with its algorithm ({bcrypt}...) by Spring Security's DelegatingPasswordEncoder
  private String passwordHash;

  private String name;

  // Disabled accounts cannot sign in, but keep their history
  private boolean enabled = true;

  /**
   * Trims and lowercases an email, the way it is stored. Every lookup and insert goes through it.
   */
  public static String normalizeEmail(String email) {
    return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
  }
}
