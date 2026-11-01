package dev.licaza.itacatl.tenant;

import static dev.licaza.itacatl.FixedClockConfig.TODAY;
import static dev.licaza.itacatl.MarketplaceTestData.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.licaza.itacatl.AppRoleTestcontainersConfiguration;
import dev.licaza.itacatl.domain.*;
import dev.licaza.itacatl.repo.*;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL's policies (V4), tested with native SQL, which Hibernate's tenant filter never sees.
 * The SQL runs inside a JPA transaction, as it would in a service, so its connection comes from
 * TenantConnectionProvider with the scope's tenant set.
 */
@SpringBootTest
@Import(AppRoleTestcontainersConfiguration.class)
@ActiveProfiles("test")
class RowLevelSecurityIntegrationTest {

  private static final String INSERT_ITEM =
      "INSERT INTO item (tenant_id, name, suggested_price) VALUES (?, 'Lime sorbet', 40)";

  @Autowired private BusinessRepository businessRepository;
  @Autowired private ItemRepository itemRepository;
  @Autowired private MenuRepository menuRepository;
  @Autowired private MenuItemRepository menuItemRepository;
  @Autowired private AppUserRepository userRepository;
  @Autowired private MembershipRepository membershipRepository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  private Business sunny;
  private Business corner;
  private Item cornersItem;

  // The context, and its database, is shared with other full-application tests
  @BeforeEach
  void createTwoBusinesses() {
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    businessRepository.deleteAll();

    sunny = businessRepository.save(business("sunny-scoops", "Sunny Scoops"));
    corner = businessRepository.save(business("cold-corner", "Cold Corner"));
    inScopeOf(sunny, () -> itemRepository.save(item(sunny, "Mango sorbet", "45.00")));
    cornersItem =
        inScopeOf(corner, () -> itemRepository.save(item(corner, "Lime sorbet", "40.00")));
    Menu published =
        inScopeOf(
            corner,
            () -> menuRepository.save(menu(corner, TODAY, "12:00", "20:00", MenuStatus.PUBLISHED)));
    Menu draft =
        inScopeOf(
            corner,
            () ->
                menuRepository.save(
                    menu(corner, TODAY.plusDays(1), "12:00", "20:00", MenuStatus.DRAFT)));
    inScopeOf(corner, () -> menuItemRepository.save(menuItem(published, cornersItem, "40.00", 10)));
    inScopeOf(corner, () -> menuItemRepository.save(menuItem(draft, cornersItem, "40.00", 5)));
  }

  @Test
  void businessScope_SeesOnlyItsRows() {
    assertEquals(1, count(sunny.getId(), "item"));
    assertEquals(0, count(sunny.getId(), "menu"));
    // Its own drafts included
    assertEquals(2, count(corner.getId(), "menu"));
  }

  @Test
  void businessScope_CannotWriteAnotherBusinessesRows() {
    assertThrows(
        DataAccessException.class,
        () -> inScope(sunny.getId(), sql -> sql.update(INSERT_ITEM, corner.getId())));

    // Rows it cannot see cannot be updated or deleted either
    int updated =
        inScope(
            sunny.getId(),
            sql -> sql.update("UPDATE item SET name = 'Taken' WHERE id = ?", cornersItem.getId()));
    assertEquals(0, updated);
  }

  @Test
  void businessScope_CannotMoveARowToAnotherBusiness() {
    assertThrows(
        DataAccessException.class,
        () ->
            inScope(
                corner.getId(),
                sql ->
                    sql.update(
                        "UPDATE item SET tenant_id = ? WHERE id = ?",
                        sunny.getId(),
                        cornersItem.getId())));
  }

  @Test
  void crossTenant_ReadsEveryBusinessButNoDrafts() {
    assertEquals(2, count(TenantContext.CROSS_TENANT, "item"));
    assertEquals(1, count(TenantContext.CROSS_TENANT, "menu"));
    // The draft's menu item is hidden with its menu
    assertEquals(1, count(TenantContext.CROSS_TENANT, "menu_item"));
  }

  @Test
  void crossTenant_CannotWrite() {
    assertThrows(
        DataAccessException.class,
        () -> inScope(TenantContext.CROSS_TENANT, sql -> sql.update(INSERT_ITEM, sunny.getId())));
    int deleted =
        inScope(
            TenantContext.CROSS_TENANT,
            sql -> sql.update("DELETE FROM item WHERE id = ?", cornersItem.getId()));
    assertEquals(0, deleted);
  }

  @Test
  void withoutATenant_NothingIsVisible() {
    assertEquals(0, count(TenantContext.NONE, "item"));
    // A connection taken from the pool directly, outside any Hibernate session, has no tenant
    // either
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM item", Integer.class));
    // Tables without policies stay readable
    assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM business", Integer.class));
  }

  @Test
  void applicationRole_CannotBypassThePolicies() {
    assertEquals("itacatl_app", jdbc.queryForObject("SELECT current_user", String.class));
    // Policies do not apply to superusers, to roles with BYPASSRLS, or to a table's owner
    assertFalse(
        jdbc.queryForObject(
            "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user",
            Boolean.class));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_tables WHERE tableowner = current_user", Integer.class));
    // Nor can it change the schema, for instance to disable them
    assertThrows(
        DataAccessException.class,
        () -> jdbc.execute("ALTER TABLE item DISABLE ROW LEVEL SECURITY"));
  }

  private int count(long tenantId, String table) {
    return inScope(
        tenantId, sql -> sql.queryForObject("SELECT count(*) FROM " + table, Integer.class));
  }

  // A transaction opened inside the scope, so its connection carries that tenant
  private <T> T inScope(long tenantId, Function<JdbcTemplate, T> work) {
    try (TenantContext.Scope scope = TenantContext.open(tenantId)) {
      return new TransactionTemplate(transactionManager).execute(status -> work.apply(jdbc));
    }
  }
}
