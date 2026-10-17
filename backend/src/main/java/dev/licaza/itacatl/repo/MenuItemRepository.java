package dev.licaza.itacatl.repo;

import dev.licaza.itacatl.domain.MenuItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

  // Fetches the catalog item in the same query, since every menu item is shown with its name
  @Query(
      """
      SELECT mi FROM MenuItem mi JOIN FETCH mi.item
      WHERE mi.menu.id = :menuId
      ORDER BY mi.item.name
      """)
  List<MenuItem> findByMenuIdWithItem(Long menuId);
}
