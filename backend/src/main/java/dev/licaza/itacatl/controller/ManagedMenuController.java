package dev.licaza.itacatl.controller;

import dev.licaza.itacatl.dto.AddMenuItemDTO;
import dev.licaza.itacatl.dto.ManagedMenuDTO;
import dev.licaza.itacatl.dto.MenuRequestDTO;
import dev.licaza.itacatl.dto.UpdateMenuItemDTO;
import dev.licaza.itacatl.service.MenuManagementService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The owner's menus, drafts included. Part of the management API under /api/manage; the public
 * MenuController only serves published menus.
 */
@RestController
@RequestMapping("/api/manage/businesses/{slug}/menus")
public class ManagedMenuController {

  private final MenuManagementService menuService;

  public ManagedMenuController(MenuManagementService menuService) {
    this.menuService = menuService;
  }

  // ?from=2026-10-01 to look back; upcoming menus by default
  @GetMapping
  public ResponseEntity<List<ManagedMenuDTO>> getMenus(
      @PathVariable String slug,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from) {
    return ResponseEntity.ok(menuService.getMenus(slug, from));
  }

  @GetMapping("/{menuId}")
  public ResponseEntity<ManagedMenuDTO> getMenu(
      @PathVariable String slug, @PathVariable Long menuId) {
    return ResponseEntity.ok(menuService.getMenu(slug, menuId));
  }

  @PostMapping
  public ResponseEntity<ManagedMenuDTO> createDraft(
      @PathVariable String slug, @Valid @RequestBody MenuRequestDTO request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(menuService.createDraft(slug, request));
  }

  // Menu items are addressed by their catalog item: a menu offers each item once
  @PostMapping("/{menuId}/items")
  public ResponseEntity<ManagedMenuDTO> addItem(
      @PathVariable String slug,
      @PathVariable Long menuId,
      @Valid @RequestBody AddMenuItemDTO request) {
    return ResponseEntity.ok(menuService.addItem(slug, menuId, request));
  }

  @PutMapping("/{menuId}/items/{itemId}")
  public ResponseEntity<ManagedMenuDTO> updateItem(
      @PathVariable String slug,
      @PathVariable Long menuId,
      @PathVariable Long itemId,
      @Valid @RequestBody UpdateMenuItemDTO request) {
    return ResponseEntity.ok(menuService.updateItem(slug, menuId, itemId, request));
  }

  @DeleteMapping("/{menuId}/items/{itemId}")
  public ResponseEntity<ManagedMenuDTO> removeItem(
      @PathVariable String slug, @PathVariable Long menuId, @PathVariable Long itemId) {
    return ResponseEntity.ok(menuService.removeItem(slug, menuId, itemId));
  }
}
