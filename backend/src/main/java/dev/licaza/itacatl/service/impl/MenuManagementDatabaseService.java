package dev.licaza.itacatl.service.impl;

import dev.licaza.itacatl.domain.Business;
import dev.licaza.itacatl.domain.Item;
import dev.licaza.itacatl.domain.Menu;
import dev.licaza.itacatl.domain.MenuItem;
import dev.licaza.itacatl.domain.MenuStatus;
import dev.licaza.itacatl.dto.AddMenuItemDTO;
import dev.licaza.itacatl.dto.ManagedMenuDTO;
import dev.licaza.itacatl.dto.ManagedMenuDTO.ManagedMenuItem;
import dev.licaza.itacatl.dto.MenuRequestDTO;
import dev.licaza.itacatl.dto.UpdateMenuItemDTO;
import dev.licaza.itacatl.exception.BusinessRuleException;
import dev.licaza.itacatl.exception.ResourceNotFoundException;
import dev.licaza.itacatl.repo.BusinessRepository;
import dev.licaza.itacatl.repo.ItemRepository;
import dev.licaza.itacatl.repo.MenuItemRepository;
import dev.licaza.itacatl.repo.MenuRepository;
import dev.licaza.itacatl.service.MenuManagementService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MenuManagementDatabaseService implements MenuManagementService {

  private static final Logger log = LoggerFactory.getLogger(MenuManagementDatabaseService.class);

  private final BusinessRepository businessRepository;
  private final MenuRepository menuRepository;
  private final MenuItemRepository menuItemRepository;
  private final ItemRepository itemRepository;
  private final Clock clock;

  public MenuManagementDatabaseService(
      BusinessRepository businessRepository,
      MenuRepository menuRepository,
      MenuItemRepository menuItemRepository,
      ItemRepository itemRepository,
      Clock clock) {
    this.businessRepository = businessRepository;
    this.menuRepository = menuRepository;
    this.menuItemRepository = menuItemRepository;
    this.itemRepository = itemRepository;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ManagedMenuDTO> getMenus(String slug, LocalDate from) {
    Business business = business(slug);
    LocalDate since = from != null ? from : today(business);
    List<Menu> menus =
        menuRepository.findByTenantIdAndServiceDateGreaterThanEqualOrderByServiceDateAscStartsAtAsc(
            business.getId(), since);

    // Every menu's items in one query instead of one per menu (N+1)
    Map<Long, List<MenuItem>> itemsByMenu =
        menus.isEmpty()
            ? Map.of()
            : menuItemRepository
                .findByMenuIdInWithItem(menus.stream().map(Menu::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(menuItem -> menuItem.getMenu().getId()));

    return menus.stream()
        .map(menu -> toDTO(menu, itemsByMenu.getOrDefault(menu.getId(), List.of())))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public ManagedMenuDTO getMenu(String slug, Long menuId) {
    Menu menu = menu(business(slug), menuId);
    return toDTO(menu, menuItemRepository.findByMenuIdWithItem(menu.getId()));
  }

  @Override
  public ManagedMenuDTO createDraft(String slug, MenuRequestDTO request) {
    Menu menu = newDraft(business(slug), request);
    log.info("✅ Draft menu {} created in {} for {}", menu.getId(), slug, menu.getServiceDate());
    return toDTO(menu, List.of());
  }

  @Override
  public ManagedMenuDTO duplicate(String slug, Long menuId, MenuRequestDTO request) {
    Business business = business(slug);
    Menu source = menu(business, menuId);
    Menu draft = newDraft(business, request);

    List<MenuItem> copies =
        menuItemRepository.findByMenuIdWithItem(source.getId()).stream()
            .filter(menuItem -> !menuItem.getItem().isArchived())
            .map(menuItem -> copy(menuItem, draft))
            .toList();
    menuItemRepository.saveAll(copies);
    log.info(
        "✅ Menu {} duplicated as draft {} in {} for {}",
        source.getId(),
        draft.getId(),
        slug,
        draft.getServiceDate());
    return withItems(draft);
  }

  @Override
  public ManagedMenuDTO reschedule(String slug, Long menuId, MenuRequestDTO request) {
    Business business = business(slug);
    Menu menu = draft(business, menuId);
    checkWhen(business, request);
    menu.setServiceDate(request.serviceDate());
    menu.setStartsAt(request.startsAt());
    menu.setEndsAt(request.endsAt());
    return withItems(menu);
  }

  @Override
  public ManagedMenuDTO publish(String slug, Long menuId) {
    Business business = business(slug);
    Menu menu = lockedMenu(business, menuId);
    // Checked first, so a retried request is a no-op even after the menu has ended
    if (menu.getStatus() == MenuStatus.PUBLISHED) {
      return withItems(menu);
    }
    checkNotEnded(business, menu);
    if (!menuItemRepository.existsByMenuIdAndItem_ArchivedFalse(menu.getId())) {
      throw BusinessRuleException.menuEmpty(menu.getId());
    }
    menu.setStatus(MenuStatus.PUBLISHED);
    log.info("✅ Menu {} published in {} for {}", menu.getId(), slug, menu.getServiceDate());
    return withItems(menu);
  }

  @Override
  public void deleteDraft(String slug, Long menuId) {
    Menu menu = draft(business(slug), menuId);
    // Its items go with it through the foreign key's ON DELETE CASCADE
    menuRepository.delete(menu);
    log.info("✅ Draft menu {} deleted in {}", menu.getId(), slug);
  }

  @Override
  public ManagedMenuDTO addItem(String slug, Long menuId, AddMenuItemDTO request) {
    Business business = business(slug);
    Menu menu = changeableMenu(business, menuId);
    Item item =
        itemRepository
            .findByIdAndTenantId(request.itemId(), business.getId())
            .orElseThrow(() -> ResourceNotFoundException.item(request.itemId()));
    if (item.isArchived()) {
      throw BusinessRuleException.itemArchived(item.getId());
    }

    MenuItem menuItem = new MenuItem();
    menuItem.setTenantId(business.getId());
    menuItem.setMenu(menu);
    menuItem.setItem(item);
    // The catalog's price is only a suggestion; the menu item's price is what is charged
    menuItem.setPrice(request.price() != null ? request.price() : item.getSuggestedPrice());
    menuItem.setQuantity(request.quantity());
    try {
      menuItemRepository.saveAndFlush(menuItem);
    } catch (DataIntegrityViolationException ex) {
      // The unique constraint (menu_id, item_id) decides, instead of a check-then-insert. Any
      // other violation is a bug, not a duplicate, and stays a 500
      if (violates(ex, "uq_menu_item")) {
        throw BusinessRuleException.itemAlreadyInMenu(item.getId());
      }
      throw ex;
    }
    return withItems(menu);
  }

  @Override
  public ManagedMenuDTO updateItem(
      String slug, Long menuId, Long itemId, UpdateMenuItemDTO request) {
    Menu menu = changeableMenu(business(slug), menuId);
    MenuItem menuItem = menuItem(menu, itemId);
    menuItem.setPrice(request.price());
    menuItem.setQuantity(request.quantity());
    return withItems(menu);
  }

  @Override
  public ManagedMenuDTO removeItem(String slug, Long menuId, Long itemId) {
    Menu menu = changeableMenu(business(slug), menuId);
    menuItemRepository.delete(menuItem(menu, itemId));
    return withItems(menu);
  }

  // A menu that has not ended, locked for the change. Ended menus are history
  private Menu changeableMenu(Business business, Long menuId) {
    Menu menu = lockedMenu(business, menuId);
    checkNotEnded(business, menu);
    return menu;
  }

  private Menu draft(Business business, Long menuId) {
    Menu menu = lockedMenu(business, menuId);
    if (menu.getStatus() != MenuStatus.DRAFT) {
      throw BusinessRuleException.menuNotDraft(menu.getId());
    }
    return menu;
  }

  private Menu newDraft(Business business, MenuRequestDTO request) {
    checkWhen(business, request);
    Menu menu = new Menu();
    menu.setTenantId(business.getId());
    menu.setServiceDate(request.serviceDate());
    menu.setStartsAt(request.startsAt());
    menu.setEndsAt(request.endsAt());
    menu.setStatus(MenuStatus.DRAFT);
    return menuRepository.save(menu);
  }

  // The source's price is kept rather than the catalog's: it is the price the owner last charged
  private static MenuItem copy(MenuItem source, Menu menu) {
    MenuItem copy = new MenuItem();
    copy.setTenantId(menu.getTenantId());
    copy.setMenu(menu);
    copy.setItem(source.getItem());
    copy.setPrice(source.getPrice());
    copy.setQuantity(source.getQuantity());
    return copy;
  }

  private MenuItem menuItem(Menu menu, Long itemId) {
    return menuItemRepository
        .findByMenuIdAndItemId(menu.getId(), itemId)
        .orElseThrow(() -> ResourceNotFoundException.menuItem(menu.getId(), itemId));
  }

  // The query flushes pending changes first, so it sees the item just added, changed or removed
  private ManagedMenuDTO withItems(Menu menu) {
    return toDTO(menu, menuItemRepository.findByMenuIdWithItem(menu.getId()));
  }

  // The window, checked here rather than only by the database's CHECK constraint, so the client
  // gets a code it can translate instead of a 500. A window that already closed today counts as
  // ended: customers would never see the menu
  private void checkWhen(Business business, MenuRequestDTO request) {
    if (!request.endsAt().isAfter(request.startsAt())) {
      throw BusinessRuleException.invalidTimeWindow();
    }
    if (hasEnded(business, request.serviceDate(), request.endsAt())) {
      throw BusinessRuleException.menuEnded(request.serviceDate(), request.endsAt());
    }
  }

  private void checkNotEnded(Business business, Menu menu) {
    if (hasEnded(business, menu.getServiceDate(), menu.getEndsAt())) {
      throw BusinessRuleException.menuEnded(menu.getServiceDate(), menu.getEndsAt());
    }
  }

  // In the business's time zone, like the public API's "not ended yet"
  private boolean hasEnded(Business business, LocalDate date, LocalTime endsAt) {
    LocalDateTime now = LocalDateTime.now(clock.withZone(business.getTimeZone()));
    return !LocalDateTime.of(date, endsAt).isAfter(now);
  }

  private LocalDate today(Business business) {
    return LocalDate.now(clock.withZone(business.getTimeZone()));
  }

  private static boolean violates(DataIntegrityViolationException ex, String constraint) {
    return ex.getCause() instanceof ConstraintViolationException violation
        && constraint.equalsIgnoreCase(violation.getConstraintName());
  }

  private Business business(String slug) {
    return businessRepository
        .findBySlug(slug)
        .orElseThrow(() -> ResourceNotFoundException.business(slug));
  }

  // Scoped to the business in the URL, like catalog items
  private Menu menu(Business business, Long menuId) {
    return menuRepository
        .findByIdAndTenantId(menuId, business.getId())
        .orElseThrow(() -> ResourceNotFoundException.menu(menuId));
  }

  // For every change. A publish and a removeItem on the same menu would otherwise both pass their
  // checks and leave an empty menu published, and a reschedule could write DRAFT back over a
  // concurrent publish. The second request waits, then sees the first one's result
  private Menu lockedMenu(Business business, Long menuId) {
    return menuRepository
        .findByIdAndTenantIdForUpdate(menuId, business.getId())
        .orElseThrow(() -> ResourceNotFoundException.menu(menuId));
  }

  private static ManagedMenuDTO toDTO(Menu menu, List<MenuItem> items) {
    return new ManagedMenuDTO(
        menu.getId(),
        menu.getServiceDate(),
        menu.getStartsAt(),
        menu.getEndsAt(),
        menu.getStatus(),
        items.stream()
            .map(
                menuItem ->
                    new ManagedMenuItem(
                        menuItem.getItem().getId(),
                        menuItem.getItem().getName(),
                        menuItem.getPrice(),
                        menuItem.getQuantity(),
                        menuItem.getItem().isArchived()))
            .toList());
  }
}
