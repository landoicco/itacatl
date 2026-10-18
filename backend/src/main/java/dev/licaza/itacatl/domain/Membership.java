package dev.licaza.itacatl.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A user's role in one business. One user can belong to several businesses. */
@Entity
@Table(name = "membership")
@Getter
@Setter
@NoArgsConstructor
public class Membership {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long userId;

  // The tenant_id column: the business is the tenant this membership grants access to
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "tenant_id")
  private Business business;

  @Enumerated(EnumType.STRING)
  private MembershipRole role;

  public Membership(Long userId, Business business, MembershipRole role) {
    this.userId = userId;
    this.business = business;
    this.role = role;
  }
}
