package dev.licaza.itacatl.service.impl;

import dev.licaza.itacatl.domain.*;
import dev.licaza.itacatl.dto.*;
import dev.licaza.itacatl.exception.ResourceNotFoundException;
import dev.licaza.itacatl.repo.*;
import dev.licaza.itacatl.service.BusinessService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BusinessDatabaseService implements BusinessService {

  // How far ahead a business profile lists published menus
  static final int UPCOMING_MENU_DAYS = 7;

  private final BusinessRepository businessRepository;
  private final BusinessHoursRepository hoursRepository;
  private final ScheduleExceptionRepository exceptionRepository;
  private final MenuRepository menuRepository;
  private final Clock clock;

  public BusinessDatabaseService(
      BusinessRepository businessRepository,
      BusinessHoursRepository hoursRepository,
      ScheduleExceptionRepository exceptionRepository,
      MenuRepository menuRepository,
      Clock clock) {
    this.businessRepository = businessRepository;
    this.hoursRepository = hoursRepository;
    this.exceptionRepository = exceptionRepository;
    this.menuRepository = menuRepository;
    this.clock = clock;
  }

  @Override
  public List<BusinessSummaryDTO> getBusinesses() {
    List<Business> businesses = businessRepository.findAllByOrderByNameAsc();
    Map<Long, WeeklySchedule> schedules = loadSchedules(businesses);

    return businesses.stream()
        .map(
            business ->
                new BusinessSummaryDTO(
                    business.getSlug(),
                    business.getName(),
                    business.getDescription(),
                    business.getTimeZone().getId(),
                    schedules.get(business.getId()).statusAt(localNow(business))))
        .toList();
  }

  @Override
  public BusinessDetailDTO getBusiness(String slug) {
    Business business =
        businessRepository
            .findBySlug(slug)
            .orElseThrow(() -> ResourceNotFoundException.business(slug));
    List<BusinessHours> hours = hoursRepository.findByTenantIdIn(List.of(business.getId()));
    WeeklySchedule schedule = loadSchedules(List.of(business), hours).get(business.getId());

    LocalDateTime now = localNow(business);
    LocalDate today = now.toLocalDate();
    List<Menu> menus =
        menuRepository.findUpcoming(
            business.getId(),
            MenuStatus.PUBLISHED,
            today,
            now.toLocalTime(),
            today.plusDays(UPCOMING_MENU_DAYS));

    return new BusinessDetailDTO(
        business.getSlug(),
        business.getName(),
        business.getDescription(),
        business.getTimeZone().getId(),
        business.isAcceptsPickup(),
        business.isAcceptsDineIn(),
        schedule.statusAt(now),
        hours.stream()
            .sorted(
                Comparator.comparing(BusinessHours::getDayOfWeek)
                    .thenComparing(BusinessHours::getOpensAt))
            .map(h -> new BusinessHoursDTO(h.getDayOfWeek(), h.getOpensAt(), h.getClosesAt()))
            .toList(),
        menus.stream()
            .map(
                menu ->
                    new MenuSummaryDTO(
                        menu.getId(), menu.getServiceDate(), menu.getStartsAt(), menu.getEndsAt()))
            .toList());
  }

  // Each business has its own time zone, so "now" is the business's local date and time
  private LocalDateTime localNow(Business business) {
    return LocalDateTime.now(clock.withZone(business.getTimeZone()));
  }

  private Map<Long, WeeklySchedule> loadSchedules(List<Business> businesses) {
    List<Long> ids = businesses.stream().map(Business::getId).toList();
    return loadSchedules(businesses, hoursRepository.findByTenantIdIn(ids));
  }

  // Loads every business's hours and exceptions in two queries, whatever the number of businesses
  private Map<Long, WeeklySchedule> loadSchedules(
      List<Business> businesses, List<BusinessHours> hours) {
    List<Long> ids = businesses.stream().map(Business::getId).toList();

    // A day of margin on each side covers every time zone's "today"
    LocalDate utcToday = LocalDate.now(clock);
    List<ScheduleException> exceptions =
        exceptionRepository.findByTenantIdInAndDateBetween(
            ids, utcToday.minusDays(1), utcToday.plusDays(WeeklySchedule.HORIZON_DAYS + 1));

    Map<Long, List<BusinessHours>> hoursByTenant =
        hours.stream().collect(Collectors.groupingBy(BusinessHours::getTenantId));
    Map<Long, List<ScheduleException>> exceptionsByTenant =
        exceptions.stream().collect(Collectors.groupingBy(ScheduleException::getTenantId));

    return ids.stream()
        .collect(
            Collectors.toMap(
                id -> id,
                id ->
                    new WeeklySchedule(
                        hoursByTenant.getOrDefault(id, List.of()),
                        exceptionsByTenant.getOrDefault(id, List.of()))));
  }
}
