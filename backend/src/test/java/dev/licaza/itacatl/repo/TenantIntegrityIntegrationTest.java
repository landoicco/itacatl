package dev.licaza.itacatl.repo;

import static dev.licaza.itacatl.FixedClockConfig.TODAY;
import static dev.licaza.itacatl.MarketplaceTestData.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.licaza.itacatl.TestcontainersConfiguration;
import dev.licaza.itacatl.domain.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
  // Joins the test's transaction, so it runs on the same connection as the repositories
  @Autowired private DataSource dataSource;

  @Test
  void menuItem_CannotOfferAnItemFromAnotherBusiness() {
    Business sunny = businessRepository.save(business("sunny-scoops", "Sunny Scoops"));
    Business corner = businessRepository.save(business("cold-corner", "Cold Corner"));
    Item cornersItem = itemRepository.save(item(corner, "Lime sorbet", "40.00"));
    Menu sunnysMenu =
        menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.PUBLISHED));

    // Sunny's menu offering Corner's item: the composite foreign key (item_id, tenant_id) fails
    menuItemRepository.saveAndFlush(menuItem(sunnysMenu, cornersItem, "40.00", 10));

    // That key is checked at commit (see V1), and this test rolls back instead, so run the check
    // now, as the commit would
    assertThrows(
        DataIntegrityViolationException.class,
        () -> new JdbcTemplate(dataSource).execute("SET CONSTRAINTS ALL IMMEDIATE"));
  }

  @Test
  void menuItem_FromTheSameBusiness_IsAccepted() {
    Business sunny = businessRepository.save(business("sunny-scoops", "Sunny Scoops"));
    Item lime = itemRepository.save(item(sunny, "Lime sorbet", "40.00"));
    Menu menu = menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.PUBLISHED));

    menuItemRepository.saveAndFlush(menuItem(menu, lime, "40.00", 10));

    assertDoesNotThrow(() -> new JdbcTemplate(dataSource).execute("SET CONSTRAINTS ALL IMMEDIATE"));
  }

  @Test
  void business_RejectsSlugsThatAreNotUrlSafe() {
    Business invalid = business("Sunny Scoops!", "Sunny Scoops");

    assertThrows(
        DataIntegrityViolationException.class, () -> businessRepository.saveAndFlush(invalid));
  }
}
