package dev.licaza.itacatl.seed;

import dev.licaza.itacatl.domain.*;
import dev.licaza.itacatl.repo.*;
import dev.licaza.itacatl.seed.SeedFile.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads sample businesses, their accounts, catalogs and menus from a seed file. Which file is a
 * deployment setting, like the UI's default locale: English for the repository, Spanish for a
 * Mexican deployment. See docs/domain-model.md.
 */
@Component
public class MarketplaceSeeder {

  private static final Logger log = LoggerFactory.getLogger(MarketplaceSeeder.class);

  // Its own mapper, independent of how the web layer serializes JSON
  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  private final BusinessRepository businessRepository;
  private final BusinessHoursRepository hoursRepository;
  private final ItemRepository itemRepository;
  private final MenuRepository menuRepository;
  private final MenuItemRepository menuItemRepository;
  private final AppUserRepository userRepository;
  private final MembershipRepository membershipRepository;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public MarketplaceSeeder(
      BusinessRepository businessRepository,
      BusinessHoursRepository hoursRepository,
      ItemRepository itemRepository,
      MenuRepository menuRepository,
      MenuItemRepository menuItemRepository,
      AppUserRepository userRepository,
      MembershipRepository membershipRepository,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    this.businessRepository = businessRepository;
    this.hoursRepository = hoursRepository;
    this.itemRepository = itemRepository;
    this.menuRepository = menuRepository;
    this.menuItemRepository = menuItemRepository;
    this.userRepository = userRepository;
    this.membershipRepository = membershipRepository;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  /**
   * Loads the file into an empty marketplace; does nothing if businesses already exist.
   *
   * @param userPassword the password of every seeded account, from configuration rather than the
   *     file, so the repository holds no credentials
   */
  @Transactional
  public void seed(Resource file, String userPassword) {
    if (businessRepository.count() > 0) {
      log.info("⏭️ Marketplace already has businesses, skipping the seed");
      return;
    }
    if (userPassword == null || userPassword.isBlank()) {
      throw new IllegalStateException(
          "Set itacatl.seed.user-password to seed the demo accounts of " + file.getFilename());
    }

    SeedFile seed = read(file);
    Map<String, AppUser> users = seedUsers(seed.users(), userPassword);
    int menus = 0;
    for (SeedBusiness business : seed.businesses()) {
      menus += seedBusiness(business, users);
    }
    log.info(
        "📦 Seeded {} businesses with {} menus and {} accounts from {}",
        seed.businesses().size(),
        menus,
        users.size(),
        file.getFilename());
  }

  // Keyed by email, so businesses can reference their members
  private Map<String, AppUser> seedUsers(List<SeedUser> seeds, String password) {
    return seeds.stream()
        .map(
            seed -> {
              AppUser user = new AppUser();
              user.setEmail(AppUser.normalizeEmail(seed.email()));
              user.setName(seed.name());
              // Encoded per account, so each hash gets its own salt
              user.setPasswordHash(passwordEncoder.encode(password));
              return userRepository.save(user);
            })
        .collect(Collectors.toMap(AppUser::getEmail, user -> user));
  }

  private SeedFile read(Resource file) {
    try (InputStream input = file.getInputStream()) {
      return jsonMapper.readValue(input, SeedFile.class);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read seed file " + file.getDescription(), e);
    }
  }

  // Returns the number of menus created
  private int seedBusiness(SeedBusiness seed, Map<String, AppUser> users) {
    Business business = new Business();
    business.setSlug(seed.slug());
    business.setName(seed.name());
    business.setDescription(seed.description());
    business.setTimeZone(ZoneId.of(seed.timeZone()));
    business.setAcceptsPickup(seed.acceptsPickup());
    business.setAcceptsDineIn(seed.acceptsDineIn());
    business = businessRepository.save(business);
    Long tenantId = business.getId();

    for (SeedMember member : seed.members()) {
      AppUser user = users.get(AppUser.normalizeEmail(member.email()));
      if (user == null) {
        // Fail fast, like unknown items: a typo should break the build, not drop an owner
        throw new IllegalStateException(
            "Seed business " + seed.slug() + " has unknown member " + member.email());
      }
      membershipRepository.save(new Membership(user.getId(), business, member.role()));
    }

    List<BusinessHours> hours =
        hoursRepository.saveAll(
            seed.hours().stream()
                .flatMap(
                    h ->
                        h.days().stream()
                            .map(day -> new BusinessHours(tenantId, day, h.opens(), h.closes())))
                .toList());

    Map<String, Item> items =
        seed.items().stream()
            .collect(
                Collectors.toMap(SeedItem::key, s -> itemRepository.save(toItem(tenantId, s))));

    // Menus follow the business's own schedule, in its own time zone
    WeeklySchedule schedule = new WeeklySchedule(hours, List.of());
    LocalDate today = LocalDate.now(clock.withZone(business.getTimeZone()));

    int created = 0;
    for (SeedMenu seedMenu : seed.menus()) {
      LocalDate date = today.plusDays(seedMenu.dayOffset());
      List<TimeWindow> windows = schedule.windowsOn(date);
      if (windows.isEmpty()) {
        log.info("⏭️ {} is closed on {}, skipping its seed menu", seed.slug(), date);
        continue;
      }

      Menu menu = new Menu();
      menu.setTenantId(tenantId);
      menu.setServiceDate(date);
      menu.setStartsAt(windows.getFirst().opensAt());
      menu.setEndsAt(windows.getFirst().closesAt());
      menu.setStatus(MenuStatus.PUBLISHED);
      Menu savedMenu = menuRepository.save(menu);

      menuItemRepository.saveAll(
          seedMenu.items().stream()
              .map(s -> toMenuItem(savedMenu, items, seed.slug(), s))
              .toList());
      created++;
    }
    return created;
  }

  private static Item toItem(Long tenantId, SeedItem seed) {
    Item item = new Item();
    item.setTenantId(tenantId);
    item.setName(seed.name());
    item.setDescription(seed.description());
    item.setSuggestedPrice(seed.price());
    item.setAlcoholic(Boolean.TRUE.equals(seed.alcoholic()));
    return item;
  }

  private static MenuItem toMenuItem(
      Menu menu, Map<String, Item> items, String slug, SeedMenuItem seed) {
    Item item = items.get(seed.item());
    if (item == null) {
      // Fail fast: a typo in a seed file should break the build, not silently drop an item
      throw new IllegalStateException("Seed menu of " + slug + " uses unknown item " + seed.item());
    }
    MenuItem menuItem = new MenuItem();
    menuItem.setTenantId(menu.getTenantId());
    menuItem.setMenu(menu);
    menuItem.setItem(item);
    menuItem.setPrice(seed.price() != null ? seed.price() : item.getSuggestedPrice());
    menuItem.setQuantity(seed.quantity());
    return menuItem;
  }
}
