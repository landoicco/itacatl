package dev.licaza.itacatl.repo;

import static dev.licaza.itacatl.FixedClockConfig.TODAY;
import static dev.licaza.itacatl.MarketplaceTestData.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.licaza.itacatl.TestcontainersConfiguration;
import dev.licaza.itacatl.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

// The schema itself keeps every row inside one tenant, whatever the application code does
@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TenantIntegrityIntegrationTest {

  @Autowired private BusinessRepository businessRepository;
  @Autowired private ItemRepository itemRepository;
  @Autowired private MenuRepository menuRepository;
  @Autowired private MenuItemRepository menuItemRepository;

  @Test
  void menuItem_CannotOfferAnItemFromAnotherBusiness() {
    Business sunny = businessRepository.save(business("sunny-scoops", "Sunny Scoops"));
    Business corner = businessRepository.save(business("cold-corner", "Cold Corner"));
    Item cornersItem = itemRepository.save(item(corner, "Lime sorbet", "40.00"));
    Menu sunnysMenu =
        menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.PUBLISHED));

    // Sunny's menu offering Corner's item: the composite foreign key (item_id, tenant_id) fails
    MenuItem mixed = menuItem(sunnysMenu, cornersItem, "40.00", 10);

    assertThrows(
        DataIntegrityViolationException.class, () -> menuItemRepository.saveAndFlush(mixed));
  }

  @Test
  void menuItem_FromTheSameBusiness_IsAccepted() {
    Business sunny = businessRepository.save(business("sunny-scoops", "Sunny Scoops"));
    Item lime = itemRepository.save(item(sunny, "Lime sorbet", "40.00"));
    Menu menu = menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.PUBLISHED));

    assertDoesNotThrow(() -> menuItemRepository.saveAndFlush(menuItem(menu, lime, "40.00", 10)));
  }

  @Test
  void business_RejectsSlugsThatAreNotUrlSafe() {
    Business invalid = business("Sunny Scoops!", "Sunny Scoops");

    assertThrows(
        DataIntegrityViolationException.class, () -> businessRepository.saveAndFlush(invalid));
  }
}
