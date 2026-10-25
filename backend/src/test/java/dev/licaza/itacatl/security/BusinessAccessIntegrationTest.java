package dev.licaza.itacatl.security;

import static dev.licaza.itacatl.MarketplaceTestData.business;
import static org.junit.jupiter.api.Assertions.*;

import dev.licaza.itacatl.TestcontainersConfiguration;
import dev.licaza.itacatl.domain.AppUser;
import dev.licaza.itacatl.domain.Business;
import dev.licaza.itacatl.domain.Membership;
import dev.licaza.itacatl.domain.MembershipRole;
import dev.licaza.itacatl.repo.AppUserRepository;
import dev.licaza.itacatl.repo.BusinessRepository;
import dev.licaza.itacatl.repo.MembershipRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.ActiveProfiles;

// Who may manage or view a business, decided by memberships in the database
@DataJpaTest
@Import({TestcontainersConfiguration.class, BusinessAccess.class})
@ActiveProfiles("test")
class BusinessAccessIntegrationTest {

  @Autowired private BusinessAccess access;
  @Autowired private AppUserRepository userRepository;
  @Autowired private BusinessRepository businessRepository;
  @Autowired private MembershipRepository membershipRepository;

  private Authentication owner;
  private Authentication staff;

  @BeforeEach
  void createMembers() {
    Business sunny = businessRepository.save(business("sunny-scoops", "Sunny Scoops"));
    businessRepository.save(business("cold-corner", "Cold Corner"));
    owner = signedIn(member("ana@example.com", sunny, MembershipRole.OWNER));
    staff = signedIn(member("luis@example.com", sunny, MembershipRole.STAFF));
  }

  @Test
  void owner_CanManageAndViewTheirBusiness() {
    assertTrue(access.canManage(owner, "sunny-scoops"));
    assertTrue(access.canView(owner, "sunny-scoops"));
  }

  @Test
  void staff_CanViewButNotManage() {
    assertFalse(access.canManage(staff, "sunny-scoops"));
    assertTrue(access.canView(staff, "sunny-scoops"));
  }

  @Test
  void members_HaveNoAccessToOtherBusinesses() {
    assertFalse(access.canManage(owner, "cold-corner"));
    assertFalse(access.canView(owner, "cold-corner"));
  }

  @Test
  void anonymousRequests_HaveNoAccess() {
    Authentication anonymous =
        new AnonymousAuthenticationToken(
            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    assertFalse(access.canView(anonymous, "sunny-scoops"));
    assertFalse(access.canView(null, "sunny-scoops"));
  }

  private AppUser member(String email, Business business, MembershipRole role) {
    AppUser user = new AppUser();
    user.setEmail(email);
    user.setName(email);
    // Not a real hash: these tests never sign in
    user.setPasswordHash("{noop}unused");
    user = userRepository.save(user);
    membershipRepository.save(new Membership(user.getId(), business, role));
    return user;
  }

  // The Authentication a signed-in request carries after SessionLogin
  private static Authentication signedIn(AppUser user) {
    AccountPrincipal principal = AccountPrincipal.from(user);
    return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
  }
}
