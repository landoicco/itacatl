package dev.licaza.itacatl.controller;

import dev.licaza.itacatl.dto.MenuDetailDTO;
import dev.licaza.itacatl.service.MenuService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/menus")
public class MenuController {

  private final MenuService menuService;

  public MenuController(MenuService menuService) {
    this.menuService = menuService;
  }

  @GetMapping("/{id}")
  public ResponseEntity<MenuDetailDTO> getMenu(@PathVariable Long id) {
    return ResponseEntity.ok(menuService.getPublishedMenu(id));
  }
}
