package dev.licaza.itacatl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Talks to the API like the browser does: a CSRF token from the cookie copied into a header and,
 * once signed in, the session cookie. Tests of protected endpoints go through the real sign-in
 * instead of faking a user, so they also cover the filters and the session store.
 *
 * <p>spring-security-test's csrf() is not used: it does not work with the SPA CSRF setup (see the
 * gotchas in docs/security.md).
 */
public final class BrowserSession {

  private final Cookie xsrf;
  private final Cookie session;

  private BrowserSession(Cookie xsrf, Cookie session) {
    this.xsrf = xsrf;
    this.session = session;
  }

  /** A visitor who has not signed in, with a CSRF token like any page load gives. */
  public static BrowserSession anonymous(MockMvc mockMvc) throws Exception {
    Cookie xsrf =
        mockMvc.perform(get("/api/businesses")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    assertNotNull(xsrf);
    return new BrowserSession(xsrf, null);
  }

  public static BrowserSession signIn(MockMvc mockMvc, String email, String password)
      throws Exception {
    BrowserSession visitor = anonymous(mockMvc);
    Cookie session =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .with(visitor.cookies())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email": "%s", "password": "%s"}
                        """
                            .formatted(email, password)))
            .andReturn()
            .getResponse()
            .getCookie("SESSION");
    assertNotNull(session, "sign-in failed for " + email);
    // The token is replaced on sign-in, so take a fresh one for the requests that follow
    return new BrowserSession(anonymous(mockMvc).xsrf, session);
  }

  /** Adds the cookies and the CSRF header to a request. */
  public RequestPostProcessor cookies() {
    return request -> {
      List<Cookie> cookies = new ArrayList<>(List.of(xsrf));
      if (session != null) {
        cookies.add(session);
      }
      request.setCookies(cookies.toArray(Cookie[]::new));
      request.addHeader("X-XSRF-TOKEN", xsrf.getValue());
      return request;
    };
  }
}
