package dev.licaza.itacatl.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * The HTTP security rules: which endpoints are public, how sessions and CSRF work, and how security
 * errors reach the client. See docs/security.md.
 */
@Configuration
@EnableWebSecurity
// URL rules decide who is signed in; @PreAuthorize on services decides what they may do in each
// business (BusinessAccess)
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      CsrfTokenRepository csrfTokenRepository,
      SecurityContextRepository securityContextRepository,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
      throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth
                    // The marketplace is public: customers browse without an account
                    .requestMatchers(HttpMethod.GET, "/api/businesses/**", "/api/menus/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/login")
                    .permitAll()
                    .requestMatchers("/actuator/health/**", "/error")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    // Closed by default, so a new endpoint is never public by accident
                    .anyRequest()
                    .denyAll())
        // Cookie-based tokens the React app reads and sends back in a header. The repository is
        // shared with the login endpoint, which rotates the token after sign-in
        .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
        // The SecurityContext lives in the HTTP session, which Spring Session stores in PostgreSQL
        .securityContext(context -> context.securityContextRepository(securityContextRepository))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler(
                        new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
        // Security errors happen in filters, before Spring MVC. Handing them to the MVC exception
        // resolver lets GlobalExceptionHandler render them as Problem Details, like any other error
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, ex) ->
                            resolve(
                                exceptionResolver, request, response, ex, HttpStatus.UNAUTHORIZED))
                    .accessDeniedHandler(
                        (request, response, ex) ->
                            resolve(
                                exceptionResolver, request, response, ex, HttpStatus.FORBIDDEN)))
        // No login page, redirects or browser popups: the React app has its own sign-in screen
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable);
    return http.build();
  }

  // Readable by JavaScript on purpose: the app copies it into the X-XSRF-TOKEN header, which a
  // page on another site cannot do
  @Bean
  public CsrfTokenRepository csrfTokenRepository() {
    return CookieCsrfTokenRepository.withHttpOnlyFalse();
  }

  // Spring Security's default, declared as a bean so the login endpoint saves the SecurityContext
  // in the same place the filters read it from
  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new DelegatingSecurityContextRepository(
        new RequestAttributeSecurityContextRepository(),
        new HttpSessionSecurityContextRepository());
  }

  // What a filter-based login does after authenticating, repeated by the login endpoint: a new
  // session ID, so a session ID planted before sign-in is useless (session fixation), and a new
  // CSRF token
  @Bean
  public SessionAuthenticationStrategy sessionAuthenticationStrategy(
      CsrfTokenRepository csrfTokenRepository) {
    return new CompositeSessionAuthenticationStrategy(
        List.of(
            new ChangeSessionIdAuthenticationStrategy(),
            new CsrfAuthenticationStrategy(csrfTokenRepository)));
  }

  private static void resolve(
      HandlerExceptionResolver resolver,
      HttpServletRequest request,
      HttpServletResponse response,
      Exception ex,
      HttpStatus fallback)
      throws IOException {
    // Null when no Problem Detail can be written, e.g. a client that only accepts HTML
    if (resolver.resolveException(request, response, null, ex) == null) {
      response.sendError(fallback.value());
    }
  }
}
