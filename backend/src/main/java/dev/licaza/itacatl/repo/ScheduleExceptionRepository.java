package dev.licaza.itacatl.repo;

import dev.licaza.itacatl.domain.ScheduleException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleExceptionRepository extends JpaRepository<ScheduleException, Long> {

  List<ScheduleException> findByTenantIdInAndDateBetween(
      Collection<Long> tenantIds, LocalDate from, LocalDate to);
}
