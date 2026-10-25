package dev.licaza.itacatl.security;

import dev.licaza.itacatl.domain.MembershipRole;
import dev.licaza.itacatl.repo.MembershipRepository;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Per-business permissions, used by method security on the services:
 *
 * <pre>{@code @PreAuthorize("@businessAccess.canManage(authentication, #slug)")}</pre>
 *
 * <p>Memberships are read on every check instead of cached in the session, so a removed membership
 * takes effect on the next request. See docs/security.md.
 */
@Component("businessAccess")
public class BusinessAccess {

  private final MembershipRepository membershipRepository;

  public BusinessAccess(MembershipRepository membershipRepository) {
    this.membershipRepository = membershipRepository;
  }

  /** Owners only: changing the catalog and the menus. */
  public boolean canManage(Authentication authentication, String slug) {
    return hasRole(authentication, slug, EnumSet.of(MembershipRole.OWNER));
  }

  /** Owners and staff: reading the catalog and the menus, drafts included. */
  public boolean canView(Authentication authentication, String slug) {
    return hasRole(authentication, slug, EnumSet.allOf(MembershipRole.class));
  }

  private boolean hasRole(Authentication authentication, String slug, Set<MembershipRole> roles) {
    // Anonymous requests carry an AnonymousAuthenticationToken, whose principal is a String
    if (authentication == null
        || !(authentication.getPrincipal() instanceof AccountPrincipal user)) {
      return false;
    }
    return membershipRepository.existsByUserIdAndBusiness_SlugAndRoleIn(
        user.getUserId(), slug, roles);
  }
}
