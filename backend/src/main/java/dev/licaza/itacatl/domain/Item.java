package dev.licaza.itacatl.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Something a business knows how to make, kept in its catalog and offered in menus. */
@Entity
@Table(name = "item")
@Getter
@Setter
@NoArgsConstructor
public class Item {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long tenantId;

  private String name;

  private String description;

  // Only prefilled when the item is added to a menu; MenuItem.price is what is charged
  private BigDecimal suggestedPrice;

  private boolean alcoholic;

  // Archived instead of deleted, so past menus and orders keep their meaning
  private boolean archived;
}
