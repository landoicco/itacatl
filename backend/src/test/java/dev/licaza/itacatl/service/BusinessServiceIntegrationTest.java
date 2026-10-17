package dev.licaza.itacatl.service;

import static dev.licaza.itacatl.FixedClockConfig.TODAY;
import static dev.licaza.itacatl.MarketplaceTestData.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.licaza.itacatl.FixedClockConfig;
import dev.licaza.itacatl.TestcontainersConfiguration;
import dev.licaza.itacatl.domain.*;
import dev.licaza.itacatl.dto.BusinessDetailDTO;
import dev.licaza.itacatl.dto.BusinessSummaryDTO;
import dev.licaza.itacatl.dto.MenuSummaryDTO;
import dev.licaza.itacatl.exception.ResourceNotFoundException;
import dev.licaza.itacatl.repo.*;
import dev.licaza.itacatl.service.impl.BusinessDatabaseService;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// Runs against PostgreSQL with the clock frozen at Saturday 11:30 in Mexico City
@DataJpaTest
@Import({TestcontainersConfiguration.class, FixedClockConfig.class, BusinessDatabaseService.class})
@ActiveProfiles("test")
class BusinessServiceIntegrationTest {

  @Autowired private BusinessService businessService;
  @Autowired private BusinessRepository businessRepository;
  @Autowired private BusinessHoursRepository hoursRepository;
  @Autowired private MenuRepository menuRepository;
  @Autowired private ItemRepository itemRepository;
  @Autowired private MenuItemRepository menuItemRepository;

  private Business sunny;

  @BeforeEach
  void setUp() {
    // Opens at noon: closed now, opens in 30 minutes
    sunny = businessRepository.save(business("sunny-scoops", "Sunny Scoops"));
    hoursRepository.save(
        new BusinessHours(
            sunny.getId(), DayOfWeek.SATURDAY, LocalTime.of(12, 0), LocalTime.of(20, 0)));

    // Opened at 10:00: open now, until 14:00
    Business corner = businessRepository.save(business("cold-corner", "Cold Corner"));
    hoursRepository.save(
        new BusinessHours(
            corner.getId(), DayOfWeek.SATURDAY, LocalTime.of(10, 0), LocalTime.of(14, 0)));
  }

  @Test
  void getBusinesses_SortsByNameWithTheirLocalOpeningStatus() {
    List<BusinessSummaryDTO> businesses = businessService.getBusinesses();

    assertEquals(
        List.of("cold-corner", "sunny-scoops"), businesses.stream().map(b -> b.slug()).toList());

    OpeningStatus corner = businesses.get(0).openingStatus();
    assertEquals(OpeningStatus.State.OPEN, corner.state());
    assertEquals(TODAY.atTime(14, 0), corner.closesAt());

    OpeningStatus sunnyStatus = businesses.get(1).openingStatus();
    assertEquals(OpeningStatus.State.CLOSED, sunnyStatus.state());
    assertEquals(TODAY.atTime(12, 0), sunnyStatus.opensAt());
  }

  @Test
  void getBusinesses_FeaturesTheItemsOfEachBusinesssNextMenu() {
    Item lime = itemRepository.save(item(sunny, "Lime sorbet", "40.00"));
    Item mango = itemRepository.save(item(sunny, "Mango sorbet", "45.00"));
    Item coconut = itemRepository.save(item(sunny, "Coconut ice cream", "45.00"));

    // Ended at 11:00, so the featured menu is the one starting at noon, not tomorrow's
    Menu morning = menuRepository.save(menu(sunny, TODAY, "08:00", "11:00", MenuStatus.PUBLISHED));
    Menu noon = menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.PUBLISHED));
    Menu tomorrow =
        menuRepository.save(menu(sunny, TODAY.plusDays(1), "12:00", "20:00", MenuStatus.PUBLISHED));
    menuItemRepository.save(menuItem(morning, coconut, "45.00", 10));
    menuItemRepository.save(menuItem(noon, mango, "45.00", 10));
    menuItemRepository.save(menuItem(noon, lime, "40.00", 10));
    menuItemRepository.save(menuItem(tomorrow, coconut, "45.00", 10));

    List<BusinessSummaryDTO> businesses = businessService.getBusinesses();

    // Cold Corner has no menu; Sunny's items come sorted by name
    assertEquals(List.of(), businesses.get(0).featuredItems());
    assertEquals(List.of("Lime sorbet", "Mango sorbet"), businesses.get(1).featuredItems());
  }

  @Test
  void getBusiness_ListsOnlyPublishedMenusThatHaveNotEndedWithinAWeek() {
    Menu today = menuRepository.save(menu(sunny, TODAY, "12:00", "20:00", MenuStatus.PUBLISHED));
    Menu tomorrow =
        menuRepository.save(menu(sunny, TODAY.plusDays(1), "12:00", "20:00", MenuStatus.PUBLISHED));
    // Excluded: ended this morning, a draft, yesterday, and beyond the 7-day window
    menuRepository.save(menu(sunny, TODAY, "08:00", "11:00", MenuStatus.PUBLISHED));
    menuRepository.save(menu(sunny, TODAY, "13:00", "18:00", MenuStatus.DRAFT));
    menuRepository.save(menu(sunny, TODAY.minusDays(1), "12:00", "20:00", MenuStatus.PUBLISHED));
    menuRepository.save(menu(sunny, TODAY.plusDays(8), "12:00", "20:00", MenuStatus.PUBLISHED));

    BusinessDetailDTO detail = businessService.getBusiness("sunny-scoops");

    assertEquals(
        List.of(today.getId(), tomorrow.getId()),
        detail.upcomingMenus().stream().map(MenuSummaryDTO::id).toList());
    assertEquals("America/Mexico_City", detail.timeZone());
    assertEquals(1, detail.weeklyHours().size());
    assertEquals(DayOfWeek.SATURDAY, detail.weeklyHours().getFirst().dayOfWeek());
  }

  @Test
  void getBusiness_UnknownSlug_ThrowsWithItsErrorCode() {
    ResourceNotFoundException ex =
        assertThrows(ResourceNotFoundException.class, () -> businessService.getBusiness("nope"));

    assertEquals("BUSINESS_NOT_FOUND", ex.getCode());
  }
}
