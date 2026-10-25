package dev.licaza.itacatl.repo;

import dev.licaza.itacatl.domain.Membership;
import dev.licaza.itacatl.domain.MembershipRole;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

  // Fetches the businesses in the same query, since the caller always shows their names
  @Query(
      """
      SELECT m FROM Membership m JOIN FETCH m.business b
      WHERE m.userId = :userId
      ORDER BY b.name
      """)
  List<Membership> findWithBusinessByUserId(Long userId);

  // One indexed lookup per permission check (uq_membership covers user_id first)
  boolean existsByUserIdAndBusiness_SlugAndRoleIn(
      Long userId, String slug, Collection<MembershipRole> roles);
}
