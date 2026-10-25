package dev.licaza.itacatl.service;

import static dev.licaza.itacatl.MarketplaceTestData.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.licaza.itacatl.TestcontainersConfiguration;
import dev.licaza.itacatl.domain.Business;
import dev.licaza.itacatl.domain.Item;
import dev.licaza.itacatl.dto.CatalogItemDTO;
import dev.licaza.itacatl.dto.ItemRequestDTO;
import dev.licaza.itacatl.exception.ResourceNotFoundException;
import dev.licaza.itacatl.repo.BusinessRepository;
import dev.licaza.itacatl.repo.ItemRepository;
import dev.licaza.itacatl.service.impl.CatalogDatabaseService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// The catalog's rules, without the security layer (permissions: CatalogControllerIntegrationTest)
@DataJpaTest
@Import({TestcontainersConfiguration.class, CatalogDatabaseService.class})
@ActiveProfiles("test")
class CatalogServiceIntegrationTest {

  @Autowired private CatalogService catalogService;
  @Autowired private BusinessRepository businessRepository;
  @Autowired private ItemRepository itemRepository;

  private Business sunny;
  private Item cornersItem;

  @BeforeEach
  void setUp() {
    sunny = businessRepository.save(business("sunny-scoops", "Sunny Scoops"));
    Business corner = businessRepository.save(business("cold-corner", "Cold Corner"));
    cornersItem = itemRepository.save(item(corner, "Vanilla ice cream", "50.00"));
  }

  @Test
  void createItem_TrimsTextAndDropsABlankDescription() {
    CatalogItemDTO created =
        catalogService.createItem(
            "sunny-scoops",
            new ItemRequestDTO("  Lime sorbet ", "  ", new BigDecimal("40"), false));

    assertEquals("Lime sorbet", created.name());
    assertNull(created.description());
    assertFalse(created.archived());
    assertEquals(sunny.getId(), itemRepository.findById(created.id()).orElseThrow().getTenantId());
  }

  @Test
  void getItems_ListsOnlyTheBusinessesOwnItemsByName_ArchivedIncluded() {
    itemRepository.save(item(sunny, "Strawberry ice cream", "45.00"));
    Item lime = itemRepository.save(item(sunny, "Lime sorbet", "40.00"));
    catalogService.archiveItem("sunny-scoops", lime.getId());

    List<CatalogItemDTO> items = catalogService.getItems("sunny-scoops");

    assertEquals(
        List.of("Lime sorbet", "Strawberry ice cream"),
        items.stream().map(CatalogItemDTO::name).toList());
    assertTrue(items.getFirst().archived());
  }

  @Test
  void updateItem_ReplacesItsFields() {
    Item lime = itemRepository.save(item(sunny, "Lime sorbet", "40.00"));

    CatalogItemDTO updated =
        catalogService.updateItem(
            "sunny-scoops",
            lime.getId(),
            new ItemRequestDTO(
                "Lime and mint sorbet", "With fresh mint.", new BigDecimal("42"), false));

    assertEquals("Lime and mint sorbet", updated.name());
    assertEquals(0, new BigDecimal("42").compareTo(updated.suggestedPrice()));
  }

  @Test
  void unarchiveItem_BringsItBack() {
    Item lime = itemRepository.save(item(sunny, "Lime sorbet", "40.00"));
    catalogService.archiveItem("sunny-scoops", lime.getId());

    assertFalse(catalogService.unarchiveItem("sunny-scoops", lime.getId()).archived());
  }

  @Test
  void anItemOfAnotherBusiness_IsNotFound() {
    ResourceNotFoundException ex =
        assertThrows(
            ResourceNotFoundException.class,
            () -> catalogService.archiveItem("sunny-scoops", cornersItem.getId()));

    assertEquals("ITEM_NOT_FOUND", ex.getCode());
    assertFalse(itemRepository.findById(cornersItem.getId()).orElseThrow().isArchived());
  }
}
