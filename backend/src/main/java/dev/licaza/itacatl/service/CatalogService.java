package dev.licaza.itacatl.service;

import dev.licaza.itacatl.dto.CatalogItemDTO;
import dev.licaza.itacatl.dto.ItemRequestDTO;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * A business's catalog: what it knows how to make. Permissions are part of the contract: members
 * read it, only owners change it (see BusinessAccess). Items of another business are not found,
 * whatever their ID.
 */
public interface CatalogService {

  /** Every item, archived ones included, sorted by name. */
  @PreAuthorize("@businessAccess.canView(authentication, #slug)")
  List<CatalogItemDTO> getItems(String slug);

  @PreAuthorize("@businessAccess.canManage(authentication, #slug)")
  CatalogItemDTO createItem(String slug, ItemRequestDTO request);

  /**
   * Replaces the item's fields. Published menus keep their own price, so they do not change.
   *
   * @throws dev.licaza.itacatl.exception.ResourceNotFoundException if the business has no such item
   */
  @PreAuthorize("@businessAccess.canManage(authentication, #slug)")
  CatalogItemDTO updateItem(String slug, Long itemId, ItemRequestDTO request);

  /** Hides the item from the catalog; past menus keep it. Items are never deleted. */
  @PreAuthorize("@businessAccess.canManage(authentication, #slug)")
  CatalogItemDTO archiveItem(String slug, Long itemId);

  @PreAuthorize("@businessAccess.canManage(authentication, #slug)")
  CatalogItemDTO unarchiveItem(String slug, Long itemId);
}
