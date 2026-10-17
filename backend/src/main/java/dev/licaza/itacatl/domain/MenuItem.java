package dev.licaza.itacatl.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A catalog item offered in a menu, with that day's price and stock. */
@Entity
@Table(name = "menu_item")
@Getter
@Setter
@NoArgsConstructor
public class MenuItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Repeated from the parents on purpose: the composite foreign keys use it to keep a menu item
  // from mixing two businesses
  private Long tenantId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "menu_id")
  private Menu menu;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "item_id")
  private Item item;

  private BigDecimal price;

  // Total portions for this service; availability subtracts sold and held units (slice 4)
  private int quantity;

  @Version private Long version;
}
