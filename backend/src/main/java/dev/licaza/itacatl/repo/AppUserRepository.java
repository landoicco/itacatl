package dev.licaza.itacatl.repo;

import dev.licaza.itacatl.domain.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

  // Callers pass the email already lowercased, like it is stored
  Optional<AppUser> findByEmail(String email);
}
