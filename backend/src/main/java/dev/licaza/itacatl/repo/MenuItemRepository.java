package dev.licaza.itacatl.repo;

import dev.licaza.itacatl.domain.MenuItem;
import java.util.Collection;
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

  // The same for several menus in one query, e.g. one per business on the home page
  @Query(
      """
      SELECT mi FROM MenuItem mi JOIN FETCH mi.item
      WHERE mi.menu.id IN :menuIds
      ORDER BY mi.item.name
      """)
  List<MenuItem> findByMenuIdInWithItem(Collection<Long> menuIds);
}
