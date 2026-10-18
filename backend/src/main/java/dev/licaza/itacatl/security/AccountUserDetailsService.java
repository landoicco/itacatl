package dev.licaza.itacatl.security;

import dev.licaza.itacatl.domain.AppUser;
import dev.licaza.itacatl.repo.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells Spring Security how to find an account: the email is the username.
 * DaoAuthenticationProvider calls it during sign-in and then compares the password itself.
 */
@Service
public class AccountUserDetailsService implements UserDetailsService {

  private final AppUserRepository userRepository;

  public AccountUserDetailsService(AppUserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String email) {
    return userRepository
        .findByEmail(AppUser.normalizeEmail(email))
        .map(AccountPrincipal::from)
        // The provider turns this into BadCredentialsException, so callers cannot tell an unknown
        // email from a wrong password
        .orElseThrow(() -> new UsernameNotFoundException("No account with this email"));
  }
}
