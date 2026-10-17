package dev.licaza.itacatl.service;

import dev.licaza.itacatl.dto.MenuDetailDTO;

/** Public, read-only view of published menus. */
public interface MenuService {

  /**
   * A published menu with its items. Drafts are reported as not found, so their existence is not
   * revealed to customers.
   *
   * @throws dev.licaza.itacatl.exception.ResourceNotFoundException if no published menu has this ID
   */
  MenuDetailDTO getPublishedMenu(Long id);
}
