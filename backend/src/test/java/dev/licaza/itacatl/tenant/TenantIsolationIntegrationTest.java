package dev.licaza.itacatl.tenant;

import static dev.licaza.itacatl.FixedClockConfig.TODAY;
import static dev.licaza.itacatl.MarketplaceTestData.*;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.licaza.itacatl.BrowserSession;
import dev.licaza.itacatl.TestcontainersConfiguration;
import dev.licaza.itacatl.domain.*;
import dev.licaza.itacatl.repo.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Hibernate's tenant filter: inside a business's scope, the repositories cannot see or create
 * another business's rows, even through queries that do not mention the tenant. Not
 * {@code @Transactional}, so every repository call opens its own session in the scope current at
 * that moment, as a request does.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TenantIsolationIntegrationTest {

  private static final String PASSWORD = "correct-horse-battery";

  @Autowired private MockMvc mockMvc;
  @Autowired private BusinessRepository businessRepository;
  @Autowired private ItemRepository itemRepository;
  @Autowired private MenuRepository menuRepository;
  @Autowired private MenuItemRepository menuItemRepository;
  @Autowired private AppUserRepository userRepository;
  @Autowired private MembershipRepository membershipRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private Business sunny;
  private Item sunnysItem;
  private Item cornersItem;
  private Menu cornersMenu;

  // The context, and its database, is shared with other full-application tests. Built in the
  // cross-tenant scope that every test starts in (CrossTenantTestExecutionListener)
  @BeforeEach
  void createTwoBusinesses() {
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    businessRepository.deleteAll();

    sunny = businessRepository.save(business("sunny-scoops", "Sunny Scoops"));
    Business corner = businessRepository.save(business("cold-corner", "Cold Corner"));
    sunnysItem = itemRepository.save(item(sunny, "Mango sorbet", "45.00"));
    cornersItem = itemRepository.save(item(corner, "Lime sorbet", "40.00"));
    cornersMenu = menuRepository.save(menu(corner, TODAY, "12:00", "20:00", MenuStatus.PUBLISHED));
    menuItemRepository.save(menuItem(cornersMenu, cornersItem, "40.00", 10));

    AppUser ana = new AppUser();
    ana.setEmail("ana@example.com");
    ana.setName("Ana");
    ana.setPasswordHash(passwordEncoder.encode(PASSWORD));
    ana = userRepository.save(ana);
    membershipRepository.save(new Membership(ana.getId(), sunny, MembershipRole.OWNER));
  }

  @Test
  void inABusinessScope_QueriesSeeOnlyThatBusiness() {
    try (TenantContext.Scope scope = TenantContext.open(sunny.getId())) {
      assertEquals(List.of(sunnysItem.getId()), ids(itemRepository.findAll()));
      assertTrue(menuRepository.findAll().isEmpty());
      // A query written without the tenant still gets "tenant_id = ?" from Hibernate
      assertTrue(menuItemRepository.findByMenuIdWithItem(cornersMenu.getId()).isEmpty());
    }
  }

  @Test
  void inABusinessScope_AnotherBusinessesIdIsNotFound() {
    try (TenantContext.Scope scope = TenantContext.open(sunny.getId())) {
      assertTrue(itemRepository.findById(cornersItem.getId()).isEmpty());
      assertTrue(menuRepository.findById(cornersMenu.getId()).isEmpty());
    }
  }

  @Test
  void inABusinessScope_NewRowsGetThatBusiness() {
    try (TenantContext.Scope scope = TenantContext.open(sunny.getId())) {
      Item lime = new Item();
      lime.setName("Lime sorbet");
      lime.setSuggestedPrice(new BigDecimal("40.00"));

      assertEquals(sunny.getId(), itemRepository.save(lime).getTenantId());
    }
  }

  @Test
  void inABusinessScope_RowsOfAnotherBusinessAreRejected() {
    Item forCorner = item(sunny, "Lime sorbet", "40.00");
    forCorner.setTenantId(cornersItem.getTenantId());

    try (TenantContext.Scope scope = TenantContext.open(sunny.getId())) {
      assertThrows(DataAccessException.class, () -> itemRepository.save(forCorner));
    }
  }

  @Test
  void withoutAScope_NoBusinessesRowsAreVisible() {
    try (TenantContext.Scope scope = TenantContext.open(TenantContext.NONE)) {
      assertTrue(itemRepository.findAll().isEmpty());
      // Businesses belong to no tenant: the marketplace's directory is always readable
      assertEquals(2, businessRepository.count());
    }
  }

  @Test
  void crossTenant_SeesEveryBusiness() {
    try (TenantContext.Scope scope = TenantContext.open(TenantContext.CROSS_TENANT)) {
      assertEquals(2, itemRepository.count());
    }
  }

  // The requests below start in NONE, like a server thread, so only the interceptor's scope can
  // make the data visible
  @Test
  void publicRequest_ReadsAcrossBusinesses() throws Exception {
    try (TenantContext.Scope scope = TenantContext.open(TenantContext.NONE)) {
      mockMvc
          .perform(get("/api/menus/{id}", cornersMenu.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items", hasSize(1)));

      assertEquals(TenantContext.NONE, TenantContext.current());
    }
  }

  @Test
  void managementRequest_ReadsOnlyTheBusinessInTheUrl() throws Exception {
    try (TenantContext.Scope scope = TenantContext.open(TenantContext.NONE)) {
      BrowserSession ana = BrowserSession.signIn(mockMvc, "ana@example.com", PASSWORD);

      mockMvc
          .perform(get("/api/manage/businesses/sunny-scoops/items").with(ana.cookies()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(1)))
          .andExpect(jsonPath("$[0].id").value(sunnysItem.getId()));

      assertEquals(TenantContext.NONE, TenantContext.current());
    }
  }

  private static List<Long> ids(List<Item> items) {
    return items.stream().map(Item::getId).toList();
  }
}
