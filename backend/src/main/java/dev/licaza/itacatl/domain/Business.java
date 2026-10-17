package dev.licaza.itacatl.domain;

import jakarta.persistence.*;
import java.time.ZoneId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A business on the marketplace, and the tenant that owns every other marketplace row. */
@Entity
@Table(name = "business")
@Getter
@Setter
@NoArgsConstructor
public class Business {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Public identifier used in URLs, so internal IDs are never exposed
  private String slug;

  private String name;

  private String description;

  // Stored as its ID (e.g. America/Mexico_City); schedules are local times in this zone
  private ZoneId timeZone;

  private boolean acceptsPickup;

  private boolean acceptsDineIn;
}
