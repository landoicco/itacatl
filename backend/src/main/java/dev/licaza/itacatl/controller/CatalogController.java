package dev.licaza.itacatl.controller;

import dev.licaza.itacatl.dto.CatalogItemDTO;
import dev.licaza.itacatl.dto.ItemRequestDTO;
import dev.licaza.itacatl.service.CatalogService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The owner's catalog. Under /api/manage, the management API: it shows what the public API hides
 * (archived items, later drafts), and every call checks the user's membership in the service.
 */
@RestController
@RequestMapping("/api/manage/businesses/{slug}/items")
public class CatalogController {

  private final CatalogService catalogService;

  public CatalogController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  @GetMapping
  public ResponseEntity<List<CatalogItemDTO>> getItems(@PathVariable String slug) {
    return ResponseEntity.ok(catalogService.getItems(slug));
  }

  @PostMapping
  public ResponseEntity<CatalogItemDTO> createItem(
      @PathVariable String slug, @Valid @RequestBody ItemRequestDTO request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.createItem(slug, request));
  }

  @PutMapping("/{itemId}")
  public ResponseEntity<CatalogItemDTO> updateItem(
      @PathVariable String slug,
      @PathVariable Long itemId,
      @Valid @RequestBody ItemRequestDTO request) {
    return ResponseEntity.ok(catalogService.updateItem(slug, itemId, request));
  }

  // Actions instead of a PATCH of the archived flag: archiving is a decision with its own rules
  // (past menus keep the item), not an edit of a field
  @PostMapping("/{itemId}/archive")
  public ResponseEntity<CatalogItemDTO> archiveItem(
      @PathVariable String slug, @PathVariable Long itemId) {
    return ResponseEntity.ok(catalogService.archiveItem(slug, itemId));
  }

  @PostMapping("/{itemId}/unarchive")
  public ResponseEntity<CatalogItemDTO> unarchiveItem(
      @PathVariable String slug, @PathVariable Long itemId) {
    return ResponseEntity.ok(catalogService.unarchiveItem(slug, itemId));
  }
}
