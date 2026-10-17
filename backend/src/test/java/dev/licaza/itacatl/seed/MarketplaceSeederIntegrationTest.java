package dev.licaza.itacatl.seed;

import static dev.licaza.itacatl.FixedClockConfig.TODAY;
import static org.junit.jupiter.api.Assertions.*;

import dev.licaza.itacatl.FixedClockConfig;
import dev.licaza.itacatl.TestcontainersConfiguration;
import dev.licaza.itacatl.domain.Menu;
import dev.licaza.itacatl.repo.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

// Loads every shipped seed file, so a typo in any language breaks the build instead of the demo.
// The clock is Saturday: every shop opens today and Sunday, and the weekend-only one skips Monday.
@DataJpaTest
@Import({TestcontainersConfiguration.class, FixedClockConfig.class, MarketplaceSeeder.class})
@ActiveProfiles("test")
class MarketplaceSeederIntegrationTest {

  @Autowired private MarketplaceSeeder seeder;
  @Autowired private BusinessRepository businessRepository;
  @Autowired private ItemRepository itemRepository;
  @Autowired private MenuRepository menuRepository;
  @Autowired private MenuItemRepository menuItemRepository;

  @ParameterizedTest
  @ValueSource(strings = {"seed/ice-cream-shops.en.json", "seed/ice-cream-shops.es-MX.json"})
  void seed_LoadsShopsWithMenusOnTheirOpenDaysOnly(String file) {
    seeder.seed(new ClassPathResource(file));

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
  void seed_IntoAMarketplaceWithData_DoesNothing(String file) {
    seeder.seed(new ClassPathResource(file));
    seeder.seed(new ClassPathResource(file));

    assertEquals(3, businessRepository.count());
  }
}
