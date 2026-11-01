package dev.licaza.itacatl.seed;

import static dev.licaza.itacatl.MarketplaceTestData.inScopeOf;
import static org.junit.jupiter.api.Assertions.*;

import dev.licaza.itacatl.AppRoleTestcontainersConfiguration;
import dev.licaza.itacatl.domain.Business;
import dev.licaza.itacatl.repo.*;
import dev.licaza.itacatl.tenant.TenantContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

/**
 * The seed as it runs at startup: as itacatl_app, without a scope, committing each business on its
 * own. MarketplaceSeederIntegrationTest checks the files' contents; this one checks that the seed
 * gets past Row-Level Security and cleans up after a failure.
 */
@SpringBootTest
@Import(AppRoleTestcontainersConfiguration.class)
@ActiveProfiles("test")
class MarketplaceSeederRowLevelSecurityIntegrationTest {

  private static final String PASSWORD = "demo-password";

  @Autowired private MarketplaceSeeder seeder;
  @Autowired private BusinessRepository businessRepository;
  @Autowired private ItemRepository itemRepository;
  @Autowired private BusinessHoursRepository hoursRepository;
  @Autowired private AppUserRepository userRepository;
  @Autowired private MembershipRepository membershipRepository;

  // The context, and its database, is shared with other full-application tests
  @BeforeEach
  void emptyMarketplace() {
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    businessRepository.deleteAll();
  }

  @Test
  void seed_WritesEveryBusinessInItsOwnScope() {
    seedWithoutAScope("seed/ice-cream-shops.en.json");

    List<Business> businesses = businessRepository.findAll();
    assertEquals(3, businesses.size());
    // Every row landed in its business: each scope sees its own, and together they are all of them
    long itemsInScopes =
        businesses.stream().mapToLong(b -> inScopeOf(b, itemRepository::count)).sum();
    // Menus depend on the real day of the week, so the schedule stands in for the business's rows
    assertTrue(businesses.stream().allMatch(b -> inScopeOf(b, hoursRepository::count) > 0));
    assertEquals(18, itemsInScopes);
    assertEquals(18, itemRepository.count());
  }

  @Test
  void failedSeed_RemovesWhatItCreated() {
    // The second business's menu uses an unknown item, after the first one is committed
    assertThrows(IllegalStateException.class, () -> seedWithoutAScope("seed/broken.json"));

    assertEquals(0, businessRepository.count());
    assertEquals(0, userRepository.count());
    assertEquals(0, itemRepository.count());
  }

  // At startup the seed runs on a thread with no scope
  private void seedWithoutAScope(String file) {
    try (TenantContext.Scope scope = TenantContext.open(TenantContext.NONE)) {
      seeder.seed(new ClassPathResource(file), PASSWORD);
    }
  }
}
