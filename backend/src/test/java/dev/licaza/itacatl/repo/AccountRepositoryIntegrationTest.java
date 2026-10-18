package dev.licaza.itacatl.repo;

import static dev.licaza.itacatl.MarketplaceTestData.business;
import static org.junit.jupiter.api.Assertions.*;

import dev.licaza.itacatl.TestcontainersConfiguration;
import dev.licaza.itacatl.domain.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AccountRepositoryIntegrationTest {

  @Autowired private AppUserRepository userRepository;
  @Autowired private MembershipRepository membershipRepository;
  @Autowired private BusinessRepository businessRepository;

  @Test
  void findWithBusinessByUserId_ReturnsEveryBusinessOfTheUserByName() {
    AppUser ana = userRepository.save(user("ana@example.com"));
    AppUser luis = userRepository.save(user("luis@example.com"));
    Business sunny = businessRepository.save(business("sunny-scoops", "Sunny Scoops"));
    Business hilltop = businessRepository.save(business("hilltop-paletas", "Hilltop Paletas"));
    membershipRepository.save(new Membership(ana.getId(), sunny, MembershipRole.OWNER));
    membershipRepository.save(new Membership(ana.getId(), hilltop, MembershipRole.STAFF));
    membershipRepository.save(new Membership(luis.getId(), sunny, MembershipRole.STAFF));

    List<Membership> memberships = membershipRepository.findWithBusinessByUserId(ana.getId());

    assertEquals(
        List.of("Hilltop Paletas", "Sunny Scoops"),
        memberships.stream().map(m -> m.getBusiness().getName()).toList());
    assertEquals(MembershipRole.OWNER, memberships.get(1).getRole());
  }

  @Test
  void appUser_RejectsEmailsThatAreNotLowercase() {
    AppUser mixedCase = user("Ana@Example.com");

    assertThrows(
        DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(mixedCase));
  }

  @Test
  void membership_IsUniquePerUserAndBusiness() {
    AppUser ana = userRepository.save(user("ana@example.com"));
    Business sunny = businessRepository.save(business("sunny-scoops", "Sunny Scoops"));
    membershipRepository.saveAndFlush(new Membership(ana.getId(), sunny, MembershipRole.OWNER));

    Membership second = new Membership(ana.getId(), sunny, MembershipRole.STAFF);

    assertThrows(
        DataIntegrityViolationException.class, () -> membershipRepository.saveAndFlush(second));
  }

  private static AppUser user(String email) {
    AppUser user = new AppUser();
    user.setEmail(email);
    user.setName("Test user");
    // Not a real hash: these tests never sign in
    user.setPasswordHash("{noop}unused");
    return user;
  }
}
