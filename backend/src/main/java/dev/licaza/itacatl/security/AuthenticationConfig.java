package dev.licaza.itacatl.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * How credentials are checked. Separate from SecurityConfig so code that only needs the password
 * encoder (the seeder, its tests) does not pull in the web security setup.
 */
@Configuration
public class AuthenticationConfig {

  // bcrypt today. Hashes carry their algorithm as a prefix ({bcrypt}...), so a stronger one can
  // become the default later while old hashes keep working
  @Bean
  public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  // The login endpoint calls this directly, since there is no formLogin filter to do it.
  // DaoAuthenticationProvider loads the account, checks the password and the enabled flag, and
  // spends the same bcrypt time on unknown emails, so response times do not reveal which exist.
  @Bean
  public AuthenticationManager authenticationManager(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }
}
