package dev.licaza.itacatl.repo;

import dev.licaza.itacatl.domain.BusinessHours;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessHoursRepository extends JpaRepository<BusinessHours, Long> {

  // One query for a whole page of businesses, instead of one per business (N+1)
  List<BusinessHours> findByTenantIdIn(Collection<Long> tenantIds);
}
