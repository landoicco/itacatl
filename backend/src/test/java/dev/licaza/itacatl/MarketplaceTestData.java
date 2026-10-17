package dev.licaza.itacatl;

import dev.licaza.itacatl.domain.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** Builders for marketplace entities, so tests only spell out what they are about. */
public final class MarketplaceTestData {

  private MarketplaceTestData() {}

  public static Business business(String slug, String name) {
    Business business = new Business();
    business.setSlug(slug);
    business.setName(name);
    business.setDescription(name + " description");
    business.setTimeZone(FixedClockConfig.MEXICO_CITY);
    business.setAcceptsPickup(true);
    return business;
  }

  public static Item item(Business business, String name, String price) {
    Item item = new Item();
    item.setTenantId(business.getId());
    item.setName(name);
    item.setSuggestedPrice(new BigDecimal(price));
    return item;
  }

  public static Menu menu(
      Business business, LocalDate date, String startsAt, String endsAt, MenuStatus status) {
    Menu menu = new Menu();
    menu.setTenantId(business.getId());
    menu.setServiceDate(date);
    menu.setStartsAt(LocalTime.parse(startsAt));
    menu.setEndsAt(LocalTime.parse(endsAt));
    menu.setStatus(status);
    return menu;
  }

  public static MenuItem menuItem(Menu menu, Item item, String price, int quantity) {
    MenuItem menuItem = new MenuItem();
    menuItem.setTenantId(menu.getTenantId());
    menuItem.setMenu(menu);
    menuItem.setItem(item);
    menuItem.setPrice(new BigDecimal(price));
    menuItem.setQuantity(quantity);
    return menuItem;
  }
}
