package dev.licaza.itacatl.repo;

import dev.licaza.itacatl.domain.Business;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessRepository extends JpaRepository<Business, Long> {

  Optional<Business> findBySlug(String slug);

  List<Business> findAllByOrderByNameAsc();
}
