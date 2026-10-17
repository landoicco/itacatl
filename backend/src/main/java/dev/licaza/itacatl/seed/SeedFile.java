package dev.licaza.itacatl.seed;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * The shape of a seed file (src/main/resources/seed/*.json). Menus use a day offset instead of a
 * date, so the sample data always has menus for today and the next days, whenever it is loaded.
 */
public record SeedFile(List<SeedBusiness> businesses) {

  public record SeedBusiness(
      String slug,
      String name,
      String description,
      String timeZone,
      boolean acceptsPickup,
      boolean acceptsDineIn,
      List<SeedHours> hours,
      List<SeedItem> items,
      List<SeedMenu> menus) {}

  public record SeedHours(List<DayOfWeek> days, LocalTime opens, LocalTime closes) {}

  /**
   * @param key identifies the item inside the file, so menus can reference it in any language
   * @param alcoholic optional, false when omitted. A wrapper type on purpose: Jackson 3 rejects a
   *     missing value for a primitive, which keeps required numbers like quantity strict
   */
  public record SeedItem(
      String key, String name, String description, BigDecimal price, Boolean alcoholic) {}

  /**
   * A menu for the business's first opening window on today + {@code dayOffset}. Skipped if the
   * business is closed that day.
   */
  public record SeedMenu(int dayOffset, List<SeedMenuItem> items) {}

  /**
   * @param price the day's price; the item's suggested price when omitted
   */
  public record SeedMenuItem(String item, int quantity, BigDecimal price) {}
}
