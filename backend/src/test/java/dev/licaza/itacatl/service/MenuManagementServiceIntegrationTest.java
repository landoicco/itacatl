package dev.licaza.itacatl.service;

import static dev.licaza.itacatl.FixedClockConfig.TODAY;
import static dev.licaza.itacatl.MarketplaceTestData.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.licaza.itacatl.FixedClockConfig;
import dev.licaza.itacatl.TestcontainersConfiguration;
import dev.licaza.itacatl.domain.*;
import dev.licaza.itacatl.dto.AddMenuItemDTO;
import dev.licaza.itacatl.dto.ManagedMenuDTO;
import dev.licaza.itacatl.dto.MenuRequestDTO;
import dev.licaza.itacatl.dto.UpdateMenuItemDTO;
import dev.licaza.itacatl.exception.BusinessRuleException;
import dev.licaza.itacatl.exception.ResourceNotFoundException;
import dev.licaza.itacatl.repo.*;
import dev.licaza.itacatl.service.impl.MenuManagementDatabaseService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
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
  @Autowired private TestEntityManager entityManager;

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

    assertEquals("MENU_ENDED", ex.getCode());
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

  @Test
  void addItem_UsesTheSuggestedPriceUnlessOneIsGiven() {
    Item lime = itemRepository.save(item(sunny, "Lime sorbet", "40.00"));
    Item mango = itemRepository.save(item(sunny, "Mango sorbet", "45.00"));
    Menu draft = menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.DRAFT));

    menuService.addItem("sunny-scoops", draft.getId(), new AddMenuItemDTO(lime.getId(), null, 30));
    ManagedMenuDTO menu =
        menuService.addItem(
            "sunny-scoops",
            draft.getId(),
            new AddMenuItemDTO(mango.getId(), new BigDecimal("39"), 20));

    assertEquals(0, new BigDecimal("40").compareTo(menu.items().get(0).price()));
    assertEquals(0, new BigDecimal("39").compareTo(menu.items().get(1).price()));
    // The catalog keeps its own price
    assertEquals(
        0,
        new BigDecimal("45")
            .compareTo(itemRepository.findById(mango.getId()).orElseThrow().getSuggestedPrice()));
  }

  @Test
  void addItem_ThatIsArchived_IsRejected() {
    Item lime = item(sunny, "Lime sorbet", "40.00");
    lime.setArchived(true);
    Long limeId = itemRepository.save(lime).getId();
    Menu draft = menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.DRAFT));

    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class,
            () ->
                menuService.addItem(
                    "sunny-scoops", draft.getId(), new AddMenuItemDTO(limeId, null, 30)));

    assertEquals("ITEM_ARCHIVED", ex.getCode());
  }

  @Test
  void addItem_Twice_IsRejectedByTheUniqueConstraint() {
    Item lime = itemRepository.save(item(sunny, "Lime sorbet", "40.00"));
    Menu draft = menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.DRAFT));
    menuService.addItem("sunny-scoops", draft.getId(), new AddMenuItemDTO(lime.getId(), null, 30));

    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class,
            () ->
                menuService.addItem(
                    "sunny-scoops", draft.getId(), new AddMenuItemDTO(lime.getId(), null, 10)));

    assertEquals("ITEM_ALREADY_IN_MENU", ex.getCode());
  }

  @Test
  void addItem_FromAnotherBusinessesCatalog_IsNotFound() {
    Item cornersItem = itemRepository.save(item(corner, "Vanilla ice cream", "50.00"));
    Menu draft = menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.DRAFT));

    ResourceNotFoundException ex =
        assertThrows(
            ResourceNotFoundException.class,
            () ->
                menuService.addItem(
                    "sunny-scoops",
                    draft.getId(),
                    new AddMenuItemDTO(cornersItem.getId(), null, 5)));

    assertEquals("ITEM_NOT_FOUND", ex.getCode());
  }

  @Test
  void updateAndRemoveItem_ChangeOnlyThatMenu() {
    Item lime = itemRepository.save(item(sunny, "Lime sorbet", "40.00"));
    Menu published =
        menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.PUBLISHED));
    menuItemRepository.save(menuItem(published, lime, "40.00", 30));

    ManagedMenuDTO updated =
        menuService.updateItem(
            "sunny-scoops",
            published.getId(),
            lime.getId(),
            new UpdateMenuItemDTO(new BigDecimal("35"), 12));
    assertEquals(12, updated.items().getFirst().quantity());

    assertTrue(
        menuService.removeItem("sunny-scoops", published.getId(), lime.getId()).items().isEmpty());
    assertTrue(itemRepository.existsById(lime.getId()));
  }

  @Test
  void changingAPastMenu_IsRejected() {
    Item lime = itemRepository.save(item(sunny, "Lime sorbet", "40.00"));
    Menu yesterday =
        menuRepository.save(
            menu(sunny, TODAY.minusDays(1), "12:00", "20:00", MenuStatus.PUBLISHED));
    menuItemRepository.save(menuItem(yesterday, lime, "40.00", 30));

    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class,
            () -> menuService.removeItem("sunny-scoops", yesterday.getId(), lime.getId()));

    assertEquals("MENU_ENDED", ex.getCode());
  }

  @Test
  void duplicate_OfAPastMenu_CopiesItsItemsIntoANewDraft_ArchivedItemsLeftOut() {
    Item lime = itemRepository.save(item(sunny, "Lime sorbet", "40.00"));
    Item old = item(sunny, "Old flavor", "30.00");
    old.setArchived(true);
    old = itemRepository.save(old);
    Menu yesterday =
        menuRepository.save(
            menu(sunny, TODAY.minusDays(1), "12:00", "20:00", MenuStatus.PUBLISHED));
    menuItemRepository.save(menuItem(yesterday, lime, "35.00", 30));
    menuItemRepository.save(menuItem(yesterday, old, "30.00", 10));

    ManagedMenuDTO copy =
        menuService.duplicate("sunny-scoops", yesterday.getId(), request(TODAY, "13:00", "19:00"));

    assertNotEquals(yesterday.getId(), copy.id());
    assertEquals(MenuStatus.DRAFT, copy.status());
    assertEquals(LocalTime.parse("13:00"), copy.startsAt());
    assertEquals(1, copy.items().size());
    // The price last charged, not the catalog's suggestion
    assertEquals(0, new BigDecimal("35").compareTo(copy.items().getFirst().price()));
    assertEquals(30, copy.items().getFirst().quantity());
    assertEquals(2, menuItemRepository.findByMenuIdWithItem(yesterday.getId()).size());
  }

  @Test
  void duplicate_ToAPastDate_IsRejected() {
    Menu draft = menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.DRAFT));

    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class,
            () ->
                menuService.duplicate(
                    "sunny-scoops", draft.getId(), request(TODAY.minusDays(1), "12:00", "20:00")));

    assertEquals("MENU_ENDED", ex.getCode());
  }

  @Test
  void reschedule_MovesADraft() {
    Item lime = itemRepository.save(item(sunny, "Lime sorbet", "40.00"));
    Menu draft = menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.DRAFT));
    menuItemRepository.save(menuItem(draft, lime, "40.00", 30));

    ManagedMenuDTO moved =
        menuService.reschedule(
            "sunny-scoops", draft.getId(), request(TODAY.plusDays(2), "09:00", "14:00"));

    assertEquals(TODAY.plusDays(2), moved.serviceDate());
    assertEquals(LocalTime.parse("14:00"), moved.endsAt());
    assertEquals(1, moved.items().size());
  }

  @Test
  void reschedule_APublishedMenu_IsRejected() {
    Menu published =
        menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.PUBLISHED));

    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class,
            () ->
                menuService.reschedule(
                    "sunny-scoops", published.getId(), request(TODAY, "13:00", "20:00")));

    assertEquals("MENU_NOT_DRAFT", ex.getCode());
  }

  @Test
  void publish_ShowsTheMenu_AndAgainChangesNothing() {
    Item lime = itemRepository.save(item(sunny, "Lime sorbet", "40.00"));
    Menu draft = menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.DRAFT));
    menuItemRepository.save(menuItem(draft, lime, "40.00", 30));

    assertEquals(MenuStatus.PUBLISHED, menuService.publish("sunny-scoops", draft.getId()).status());
    assertEquals(MenuStatus.PUBLISHED, menuService.publish("sunny-scoops", draft.getId()).status());
  }

  @Test
  void publish_AnEmptyMenu_IsRejected() {
    Menu draft = menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.DRAFT));

    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class, () -> menuService.publish("sunny-scoops", draft.getId()));

    assertEquals("MENU_EMPTY", ex.getCode());
  }

  @Test
  void publish_APastMenu_IsRejected() {
    Item lime = itemRepository.save(item(sunny, "Lime sorbet", "40.00"));
    Menu draft =
        menuRepository.save(menu(sunny, TODAY.minusDays(1), "12:00", "20:00", MenuStatus.DRAFT));
    menuItemRepository.save(menuItem(draft, lime, "40.00", 30));

    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class, () -> menuService.publish("sunny-scoops", draft.getId()));

    assertEquals("MENU_ENDED", ex.getCode());
  }

  @Test
  void deleteDraft_RemovesItWithItsItems_EvenInThePast() {
    Item lime = itemRepository.save(item(sunny, "Lime sorbet", "40.00"));
    Menu draft =
        menuRepository.save(menu(sunny, TODAY.minusDays(1), "12:00", "20:00", MenuStatus.DRAFT));
    menuItemRepository.save(menuItem(draft, lime, "40.00", 30));
    // Like a new request: the menu item saved above must not linger in the persistence context,
    // pointing at the deleted menu
    entityManager.flush();
    entityManager.clear();

    menuService.deleteDraft("sunny-scoops", draft.getId());
    entityManager.flush();

    assertFalse(menuRepository.existsById(draft.getId()));
    assertTrue(menuItemRepository.findByMenuIdWithItem(draft.getId()).isEmpty());
    assertTrue(itemRepository.existsById(lime.getId()));
  }

  @Test
  void deleteDraft_OfAPublishedMenu_IsRejected() {
    Menu published =
        menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.PUBLISHED));

    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class,
            () -> menuService.deleteDraft("sunny-scoops", published.getId()));

    assertEquals("MENU_NOT_DRAFT", ex.getCode());
  }

  @Test
  void createDraft_ForAWindowThatClosedEarlierToday_IsRejected() {
    // The clock says 11:30
    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class,
            () -> menuService.createDraft("sunny-scoops", request(TODAY, "08:00", "11:30")));

    assertEquals("MENU_ENDED", ex.getCode());
  }

  @Test
  void publish_WhenEveryItemWasArchived_IsRejected() {
    Item lime = itemRepository.save(item(sunny, "Lime sorbet", "40.00"));
    Menu draft = menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.DRAFT));
    menuItemRepository.save(menuItem(draft, lime, "40.00", 30));
    lime.setArchived(true);

    BusinessRuleException ex =
        assertThrows(
            BusinessRuleException.class, () -> menuService.publish("sunny-scoops", draft.getId()));

    assertEquals("MENU_EMPTY", ex.getCode());
  }

  @Test
  void publish_AgainAfterTheMenuEnded_ChangesNothing() {
    Menu yesterday =
        menuRepository.save(
            menu(sunny, TODAY.minusDays(1), "12:00", "20:00", MenuStatus.PUBLISHED));

    assertEquals(
        MenuStatus.PUBLISHED, menuService.publish("sunny-scoops", yesterday.getId()).status());
  }

  private static MenuRequestDTO request(LocalDate date, String startsAt, String endsAt) {
    return new MenuRequestDTO(date, LocalTime.parse(startsAt), LocalTime.parse(endsAt));
  }
}
