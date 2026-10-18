package dev.licaza.itacatl.service;

import static dev.licaza.itacatl.FixedClockConfig.TODAY;
import static dev.licaza.itacatl.MarketplaceTestData.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.licaza.itacatl.FixedClockConfig;
import dev.licaza.itacatl.TestcontainersConfiguration;
import dev.licaza.itacatl.domain.*;
import dev.licaza.itacatl.dto.ManagedMenuDTO;
import dev.licaza.itacatl.dto.MenuRequestDTO;
import dev.licaza.itacatl.exception.BusinessRuleException;
import dev.licaza.itacatl.exception.ResourceNotFoundException;
import dev.licaza.itacatl.repo.*;
import dev.licaza.itacatl.service.impl.MenuManagementDatabaseService;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// Menu rules without the security layer, with the clock frozen at Saturday 11:30 in Mexico City
@DataJpaTest
@Import({
  TestcontainersConfiguration.class,
  FixedClockConfig.class,
  MenuManagementDatabaseService.class
})
@ActiveProfiles("test")
class MenuManagementServiceIntegrationTest {

  @Autowired private MenuManagementService menuService;
  @Autowired private BusinessRepository businessRepository;
  @Autowired private ItemRepository itemRepository;
  @Autowired private MenuRepository menuRepository;
  @Autowired private MenuItemRepository menuItemRepository;

  private Business sunny;
  private Business corner;

  @BeforeEach
  void setUp() {
    sunny = businessRepository.save(business("sunny-scoops", "Sunny Scoops"));
    corner = businessRepository.save(business("cold-corner", "Cold Corner"));
  }

  @Test
  void createDraft_StartsEmptyAndUnpublished() {
    ManagedMenuDTO draft =
        menuService.createDraft("sunny-scoops", request(TODAY.plusDays(1), "12:00", "20:00"));

    assertEquals(MenuStatus.DRAFT, draft.status());
    assertTrue(draft.items().isEmpty());
    assertEquals(sunny.getId(), menuRepository.findById(draft.id()).orElseThrow().getTenantId());
  }

  @Test
  void createDraft_ForToday_IsAllowed() {
    assertDoesNotThrow(
        () -> menuService.createDraft("sunny-scoops", request(TODAY, "12:00", "20:00")));
  }

  @Test
  void createDraft_WithAWindowThatDoesNotEndAfterItStarts_IsRejected() {
    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class,
            () -> menuService.createDraft("sunny-scoops", request(TODAY, "20:00", "20:00")));

    assertEquals("INVALID_TIME_WINDOW", ex.getCode());
  }

  @Test
  void createDraft_ForAPastDate_IsRejected() {
    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class,
            () ->
                menuService.createDraft(
                    "sunny-scoops", request(TODAY.minusDays(1), "12:00", "20:00")));

    assertEquals("MENU_DATE_IN_PAST", ex.getCode());
  }

  @Test
  void getMenus_ListsUpcomingMenusWithTheirItems_DraftsIncluded() {
    Item lime = itemRepository.save(item(sunny, "Lime sorbet", "40.00"));
    menuRepository.save(menu(sunny, TODAY.minusDays(1), "12:00", "20:00", MenuStatus.PUBLISHED));
    Menu published =
        menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.PUBLISHED));
    menuItemRepository.save(menuItem(published, lime, "40.00", 30));
    menuRepository.save(menu(sunny, TODAY.plusDays(1), "12:00", "20:00", MenuStatus.DRAFT));
    menuRepository.save(menu(corner, TODAY, "12:00", "20:00", MenuStatus.PUBLISHED));

    List<ManagedMenuDTO> menus = menuService.getMenus("sunny-scoops", null);

    assertEquals(
        List.of(MenuStatus.PUBLISHED, MenuStatus.DRAFT),
        menus.stream().map(ManagedMenuDTO::status).toList());
    assertEquals("Lime sorbet", menus.getFirst().items().getFirst().name());
    // Asking from yesterday brings back the past menu too
    assertEquals(3, menuService.getMenus("sunny-scoops", TODAY.minusDays(1)).size());
  }

  @Test
  void aMenuOfAnotherBusiness_IsNotFound() {
    Menu cornersMenu =
        menuRepository.save(menu(corner, TODAY, "12:00", "20:00", MenuStatus.PUBLISHED));

    ResourceNotFoundException ex =
        assertThrows(
            ResourceNotFoundException.class,
            () -> menuService.getMenu("sunny-scoops", cornersMenu.getId()));

    assertEquals("MENU_NOT_FOUND", ex.getCode());
  }

  private static MenuRequestDTO request(java.time.LocalDate date, String startsAt, String endsAt) {
    return new MenuRequestDTO(date, LocalTime.parse(startsAt), LocalTime.parse(endsAt));
  }
}
