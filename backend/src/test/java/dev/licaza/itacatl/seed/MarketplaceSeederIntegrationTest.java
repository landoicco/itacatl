package dev.licaza.itacatl.seed;

import static dev.licaza.itacatl.FixedClockConfig.TODAY;
import static org.junit.jupiter.api.Assertions.*;

import dev.licaza.itacatl.FixedClockConfig;
import dev.licaza.itacatl.TestcontainersConfiguration;
import dev.licaza.itacatl.domain.AppUser;
import dev.licaza.itacatl.domain.Membership;
import dev.licaza.itacatl.domain.MembershipRole;
import dev.licaza.itacatl.domain.Menu;
import dev.licaza.itacatl.repo.*;
import dev.licaza.itacatl.security.AccountUserDetailsService;
import dev.licaza.itacatl.security.AuthenticationConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

// Loads every shipped seed file, so a typo in any language breaks the build instead of the demo.
// The clock is Saturday: every shop opens today and Sunday, and the weekend-only one skips Monday.
@DataJpaTest
@Import({
  TestcontainersConfiguration.class,
  FixedClockConfig.class,
  MarketplaceSeeder.class,
  // The real password encoder, so the test proves seeded accounts can sign in
  AuthenticationConfig.class,
  AccountUserDetailsService.class
})
@ActiveProfiles("test")
class MarketplaceSeederIntegrationTest {

  private static final String PASSWORD = "demo-password";

  @Autowired private MarketplaceSeeder seeder;
  @Autowired private BusinessRepository businessRepository;
  @Autowired private ItemRepository itemRepository;
  @Autowired private MenuRepository menuRepository;
  @Autowired private MenuItemRepository menuItemRepository;
  @Autowired private AppUserRepository userRepository;
  @Autowired private MembershipRepository membershipRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @ParameterizedTest
  @ValueSource(strings = {"seed/ice-cream-shops.en.json", "seed/ice-cream-shops.es-MX.json"})
  void seed_LoadsShopsWithMenusOnTheirOpenDaysOnly(String file) {
    seeder.seed(new ClassPathResource(file), PASSWORD);

    assertEquals(3, businessRepository.count());
    assertEquals(18, itemRepository.count());

    // Two menus per shop: Monday's menu of the weekend-only shop is skipped
    List<LocalDate> dates = menuRepository.findAll().stream().map(Menu::getServiceDate).toList();
    assertEquals(6, dates.size());
    assertTrue(dates.stream().allMatch(d -> d.equals(TODAY) || d.equals(TODAY.plusDays(1))));
    assertEquals(25, menuItemRepository.count());
  }

  @ParameterizedTest
  @ValueSource(strings = {"seed/ice-cream-shops.en.json", "seed/ice-cream-shops.es-MX.json"})
  void seed_CreatesAccountsThatCanSignInWithTheConfiguredPassword(String file) {
    seeder.seed(new ClassPathResource(file), PASSWORD);

    // Ana owns two shops, which shows one account running several businesses
    AppUser ana = userRepository.findByEmail("ana@example.com").orElseThrow();
    assertTrue(passwordEncoder.matches(PASSWORD, ana.getPasswordHash()));
    List<Membership> anasShops = membershipRepository.findWithBusinessByUserId(ana.getId());
    assertEquals(2, anasShops.size());
    assertTrue(anasShops.stream().allMatch(m -> m.getRole() == MembershipRole.OWNER));

    assertEquals(2, userRepository.count());
    assertEquals(4, membershipRepository.count());
  }

  @ParameterizedTest
  @ValueSource(strings = {"seed/ice-cream-shops.en.json", "seed/ice-cream-shops.es-MX.json"})
  void seed_IntoAMarketplaceWithData_DoesNothing(String file) {
    seeder.seed(new ClassPathResource(file), PASSWORD);
    seeder.seed(new ClassPathResource(file), PASSWORD);

    assertEquals(3, businessRepository.count());
  }

  @Test
  void seed_WithoutAPassword_FailsBeforeWritingAnything() {
    ClassPathResource file = new ClassPathResource("seed/ice-cream-shops.en.json");

    assertThrows(IllegalStateException.class, () -> seeder.seed(file, " "));
    assertEquals(0, businessRepository.count());
  }
}
