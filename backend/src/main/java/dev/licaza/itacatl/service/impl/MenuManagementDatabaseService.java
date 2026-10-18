package dev.licaza.itacatl.service.impl;

import dev.licaza.itacatl.domain.Business;
import dev.licaza.itacatl.domain.Menu;
import dev.licaza.itacatl.domain.MenuItem;
import dev.licaza.itacatl.domain.MenuStatus;
import dev.licaza.itacatl.dto.ManagedMenuDTO;
import dev.licaza.itacatl.dto.ManagedMenuDTO.ManagedMenuItem;
import dev.licaza.itacatl.dto.MenuRequestDTO;
import dev.licaza.itacatl.exception.BusinessRuleException;
import dev.licaza.itacatl.exception.ResourceNotFoundException;
import dev.licaza.itacatl.repo.BusinessRepository;
import dev.licaza.itacatl.repo.MenuItemRepository;
import dev.licaza.itacatl.repo.MenuRepository;
import dev.licaza.itacatl.service.MenuManagementService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MenuManagementDatabaseService implements MenuManagementService {

  private static final Logger log = LoggerFactory.getLogger(MenuManagementDatabaseService.class);

  private final BusinessRepository businessRepository;
  private final MenuRepository menuRepository;
  private final MenuItemRepository menuItemRepository;
  private final Clock clock;

  public MenuManagementDatabaseService(
      BusinessRepository businessRepository,
      MenuRepository menuRepository,
      MenuItemRepository menuItemRepository,
      Clock clock) {
    this.businessRepository = businessRepository;
    this.menuRepository = menuRepository;
    this.menuItemRepository = menuItemRepository;
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
    Business business = business(slug);
    checkWhen(business, request);

    Menu menu = new Menu();
    menu.setTenantId(business.getId());
    menu.setServiceDate(request.serviceDate());
    menu.setStartsAt(request.startsAt());
    menu.setEndsAt(request.endsAt());
    menu.setStatus(MenuStatus.DRAFT);
    menu = menuRepository.save(menu);
    log.info("✅ Draft menu {} created in {} for {}", menu.getId(), slug, menu.getServiceDate());
    return toDTO(menu, List.of());
  }

  // The window and the date, checked here rather than only by the database's CHECK constraint, so
  // the client gets a code it can translate instead of a 500
  private void checkWhen(Business business, MenuRequestDTO request) {
    if (!request.endsAt().isAfter(request.startsAt())) {
      throw BusinessRuleException.invalidTimeWindow();
    }
    if (request.serviceDate().isBefore(today(business))) {
      throw BusinessRuleException.menuDateInPast(request.serviceDate());
    }
  }

  private LocalDate today(Business business) {
    return LocalDate.now(clock.withZone(business.getTimeZone()));
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
