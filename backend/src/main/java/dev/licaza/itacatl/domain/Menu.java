package dev.licaza.itacatl.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/** What a business sells in one service: a date and a local time window. */
@Entity
@Table(name = "menu")
@Getter
@Setter
@NoArgsConstructor
public class Menu {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Hibernate adds "tenant_id = ?" to every query and fills it on insert (see TenantContext)
  @TenantId private Long tenantId;

  private LocalDate serviceDate;

  private LocalTime startsAt;

  private LocalTime endsAt;

  @Enumerated(EnumType.STRING)
  private MenuStatus status;
}
