package dev.licaza.itacatl.repo;

import dev.licaza.itacatl.domain.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

  // Callers pass the email through AppUser.normalizeEmail, like it is stored
  Optional<AppUser> findByEmail(String email);
}
