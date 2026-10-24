package dev.licaza.itacatl.repo;

import dev.licaza.itacatl.domain.Item;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

  List<Item> findByTenantIdOrderByNameAsc(Long tenantId);

  // Always looked up together with the tenant: an ID from another business is simply not found
  Optional<Item> findByIdAndTenantId(Long id, Long tenantId);
}
