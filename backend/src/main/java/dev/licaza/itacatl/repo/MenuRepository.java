package dev.licaza.itacatl.repo;

import dev.licaza.itacatl.domain.Menu;
import dev.licaza.itacatl.domain.MenuStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MenuRepository extends JpaRepository<Menu, Long> {

  // Menus that have not ended yet: later dates, or today with a window still running or ahead.
  // "today" and "now" are the business's local date and time, computed by the caller.
  @Query(
      """
      SELECT m FROM Menu m
      WHERE m.tenantId = :tenantId
        AND m.status = :status
        AND m.serviceDate <= :until
        AND (m.serviceDate > :today OR (m.serviceDate = :today AND m.endsAt > :now))
      ORDER BY m.serviceDate, m.startsAt
      """)
  List<Menu> findUpcoming(
      Long tenantId, MenuStatus status, LocalDate today, LocalTime now, LocalDate until);

  Optional<Menu> findByIdAndStatus(Long id, MenuStatus status);

  // Management: always within one business, drafts included
  Optional<Menu> findByIdAndTenantId(Long id, Long tenantId);

  // SELECT ... FOR UPDATE: the menu is the root of its items, so every change to either takes this
  // row lock first and changes to one menu run one after another
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT m FROM Menu m WHERE m.id = :id AND m.tenantId = :tenantId")
  Optional<Menu> findByIdAndTenantIdForUpdate(Long id, Long tenantId);

  List<Menu> findByTenantIdAndServiceDateGreaterThanEqualOrderByServiceDateAscStartsAtAsc(
      Long tenantId, LocalDate from);

  // Candidates for many businesses at once; each business's own "not ended yet" filter runs in
  // its local time in the service, since one SQL "now" cannot fit every time zone
  List<Menu> findByTenantIdInAndStatusAndServiceDateBetweenOrderByServiceDateAscStartsAtAsc(
      Collection<Long> tenantIds, MenuStatus status, LocalDate from, LocalDate to);
}
