package dev.licaza.itacatl.security;

import dev.licaza.itacatl.domain.AppUser;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The signed-in user, as Spring Security keeps it in the SecurityContext and the session.
 *
 * <p>Kept small on purpose: Spring Session serializes it into PostgreSQL on every request that
 * changes the session, and a session made with an older version of this class cannot be read back.
 * What a user can do in each business is not cached here; it is read from their memberships when
 * needed, so a revoked membership takes effect on the next request.
 */
public final class AccountPrincipal implements UserDetails, CredentialsContainer {

  @Serial private static final long serialVersionUID = 1L;

  private final Long userId;
  private final String email;
  private final boolean enabled;
  // Only needed while the password is checked; erased before the session is stored
  private String passwordHash;

  private AccountPrincipal(Long userId, String email, String passwordHash, boolean enabled) {
    this.userId = userId;
    this.email = email;
    this.passwordHash = passwordHash;
    this.enabled = enabled;
  }

  static AccountPrincipal from(AppUser user) {
    return new AccountPrincipal(
        user.getId(), user.getEmail(), user.getPasswordHash(), user.isEnabled());
  }

  public Long getUserId() {
    return userId;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  // No global roles: permissions depend on the business, so they come from memberships
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of();
  }

  // Called by ProviderManager right after a successful sign-in
  @Override
  public void eraseCredentials() {
    passwordHash = null;
  }
}
