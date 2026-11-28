package dev.licaza.itacatl.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Turns on OriginVerificationFilter when itacatl.origin.verify is true: in AWS, whose secret comes
 * from SSM Parameter Store. Without the secret the app does not start. Locally and in tests there
 * is no CloudFront, so the filter is not registered.
 */
@Configuration
@ConditionalOnProperty(name = "itacatl.origin.verify", havingValue = "true")
public class OriginVerificationConfig {

  // First of all filters, before Spring Security: a request that skipped CloudFront gets nothing,
  // not even a CSRF cookie or a session lookup
  @Bean
  public FilterRegistrationBean<OriginVerificationFilter> originVerificationFilter(
      @Value("${itacatl.origin.secret}") String secret) {
    FilterRegistrationBean<OriginVerificationFilter> registration =
        new FilterRegistrationBean<>(new OriginVerificationFilter(secret));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
