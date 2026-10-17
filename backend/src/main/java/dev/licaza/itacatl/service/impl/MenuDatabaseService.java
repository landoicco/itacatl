package dev.licaza.itacatl.service.impl;

import dev.licaza.itacatl.domain.Business;
import dev.licaza.itacatl.domain.Menu;
import dev.licaza.itacatl.domain.MenuStatus;
import dev.licaza.itacatl.dto.MenuDetailDTO;
import dev.licaza.itacatl.dto.MenuItemDTO;
import dev.licaza.itacatl.exception.ResourceNotFoundException;
import dev.licaza.itacatl.repo.BusinessRepository;
import dev.licaza.itacatl.repo.MenuItemRepository;
import dev.licaza.itacatl.repo.MenuRepository;
import dev.licaza.itacatl.service.MenuService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MenuDatabaseService implements MenuService {

  private final MenuRepository menuRepository;
  private final MenuItemRepository menuItemRepository;
  private final BusinessRepository businessRepository;

  public MenuDatabaseService(
      MenuRepository menuRepository,
      MenuItemRepository menuItemRepository,
      BusinessRepository businessRepository) {
    this.menuRepository = menuRepository;
    this.menuItemRepository = menuItemRepository;
    this.businessRepository = businessRepository;
  }

  @Override
  public MenuDetailDTO getPublishedMenu(Long id) {
    Menu menu =
        menuRepository
            .findByIdAndStatus(id, MenuStatus.PUBLISHED)
            .orElseThrow(() -> ResourceNotFoundException.menu(id));
    // The foreign key guarantees the business exists
    Business business = businessRepository.findById(menu.getTenantId()).orElseThrow();

    List<MenuItemDTO> items =
        menuItemRepository.findByMenuIdWithItem(id).stream()
            .map(
                menuItem ->
                    new MenuItemDTO(
                        menuItem.getId(),
                        menuItem.getItem().getName(),
                        menuItem.getItem().getDescription(),
                        menuItem.getPrice(),
                        menuItem.getQuantity(),
                        menuItem.getItem().isAlcoholic()))
            .toList();

    return new MenuDetailDTO(
        menu.getId(),
        business.getSlug(),
        business.getName(),
        menu.getServiceDate(),
        menu.getStartsAt(),
        menu.getEndsAt(),
        items);
  }
}
