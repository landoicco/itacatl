package dev.licaza.itacatl.service;

import static dev.licaza.itacatl.FixedClockConfig.TODAY;
import static dev.licaza.itacatl.MarketplaceTestData.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.licaza.itacatl.TestcontainersConfiguration;
import dev.licaza.itacatl.domain.*;
import dev.licaza.itacatl.dto.MenuDetailDTO;
import dev.licaza.itacatl.dto.MenuItemDTO;
import dev.licaza.itacatl.exception.ResourceNotFoundException;
import dev.licaza.itacatl.repo.*;
import dev.licaza.itacatl.service.impl.MenuDatabaseService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import({TestcontainersConfiguration.class, MenuDatabaseService.class})
@ActiveProfiles("test")
class MenuServiceIntegrationTest {

  @Autowired private MenuService menuService;
  @Autowired private BusinessRepository businessRepository;
  @Autowired private ItemRepository itemRepository;
  @Autowired private MenuRepository menuRepository;
  @Autowired private MenuItemRepository menuItemRepository;

  private Business business;
  private Item lime;
  private Item passionFruit;

  @BeforeEach
  void setUp() {
    business = businessRepository.save(business("sunny-scoops", "Sunny Scoops"));
    lime = itemRepository.save(item(business, "Lime sorbet", "40.00"));
    passionFruit = itemRepository.save(item(business, "Passion fruit sorbet", "45.00"));
  }

  @Test
  void getPublishedMenu_ReturnsItsItemsWithTodaysPriceAndStock() {
    Menu menu = menuRepository.save(menu(business, TODAY, "12:00", "20:00", MenuStatus.PUBLISHED));
    // The day's price can differ from the catalog's suggested price
    menuItemRepository.save(menuItem(menu, passionFruit, "50.00", 20));
    menuItemRepository.save(menuItem(menu, lime, "40.00", 30));

    MenuDetailDTO detail = menuService.getPublishedMenu(menu.getId());

    assertEquals("sunny-scoops", detail.businessSlug());
    List<MenuItemDTO> items = detail.items();
    assertEquals(
        List.of("Lime sorbet", "Passion fruit sorbet"),
        items.stream().map(MenuItemDTO::name).toList());
    assertEquals(new BigDecimal("50.00"), items.get(1).price());
    assertEquals(20, items.get(1).available());
  }

  @Test
  void getPublishedMenu_Draft_IsReportedAsNotFound() {
    Menu draft = menuRepository.save(menu(business, TODAY, "12:00", "20:00", MenuStatus.DRAFT));

    ResourceNotFoundException ex =
        assertThrows(
            ResourceNotFoundException.class, () -> menuService.getPublishedMenu(draft.getId()));

    assertEquals("MENU_NOT_FOUND", ex.getCode());
  }
}
