package dev.licaza.itacatl.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * The session cookie (SESSION) that Spring Session writes. Its flags are security settings, so they
 * are declared here instead of inherited from the servlet container, whose defaults differ between
 * Tomcat and the test environment.
 */
@Configuration
public class SessionConfig {

  @Bean
  public CookieSerializer cookieSerializer(
      @Value("${itacatl.session.cookie.secure:false}") boolean secure) {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    // JavaScript cannot read it, so an XSS bug cannot steal the session
    serializer.setUseHttpOnlyCookie(true);
    // Not sent on requests started by other sites, except top-level navigation. A second layer;
    // the CSRF token stays the main defense
    serializer.setSameSite("Lax");
    // HTTPS only in AWS (the aws profile turns it on); locally the app runs on http://localhost
    serializer.setUseSecureCookie(secure);
    return serializer;
  }
}
