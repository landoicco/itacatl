package dev.licaza.itacatl.controller;

import dev.licaza.itacatl.dto.ManagedMenuDTO;
import dev.licaza.itacatl.dto.MenuRequestDTO;
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
}
