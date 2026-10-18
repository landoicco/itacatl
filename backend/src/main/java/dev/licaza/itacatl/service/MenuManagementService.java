package dev.licaza.itacatl.service;

import dev.licaza.itacatl.dto.ManagedMenuDTO;
import dev.licaza.itacatl.dto.MenuRequestDTO;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * A business's menus as its members manage them, drafts included. The public side is MenuService,
 * which only shows published menus. Menus of another business are not found, whatever their ID.
 */
public interface MenuManagementService {

  /**
   * Menus served on {@code from} or later, by date and start time.
   *
   * @param from a date in the business's time zone; today there when null
   */
  @PreAuthorize("@businessAccess.canView(authentication, #slug)")
  List<ManagedMenuDTO> getMenus(String slug, LocalDate from);

  /**
   * @throws dev.licaza.itacatl.exception.ResourceNotFoundException if the business has no such menu
   */
  @PreAuthorize("@businessAccess.canView(authentication, #slug)")
  ManagedMenuDTO getMenu(String slug, Long menuId);

  /**
   * A new, empty draft. Customers do not see it until it is published.
   *
   * @throws dev.licaza.itacatl.exception.BusinessRuleException if the window ends before it starts
   *     or the date has passed
   */
  @PreAuthorize("@businessAccess.canManage(authentication, #slug)")
  ManagedMenuDTO createDraft(String slug, MenuRequestDTO request);
}
