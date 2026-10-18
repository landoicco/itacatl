package dev.licaza.itacatl.controller;

import static dev.licaza.itacatl.MarketplaceTestData.business;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.licaza.itacatl.TestcontainersConfiguration;
import dev.licaza.itacatl.domain.AppUser;
import dev.licaza.itacatl.domain.Business;
import dev.licaza.itacatl.domain.Membership;
import dev.licaza.itacatl.domain.MembershipRole;
import dev.licaza.itacatl.repo.AppUserRepository;
import dev.licaza.itacatl.repo.BusinessRepository;
import dev.licaza.itacatl.repo.MembershipRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// The sign-in flow over HTTP with the real filter chain, CSRF tokens and Spring Session storing
// sessions in PostgreSQL, the way the React app will use it
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

  private static final String PASSWORD = "correct-horse-battery";

  @Autowired private MockMvc mockMvc;
  @Autowired private AppUserRepository userRepository;
  @Autowired private BusinessRepository businessRepository;
  @Autowired private MembershipRepository membershipRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  // The context, and its database, is shared by every test in this class
  @BeforeEach
  void createOwner() {
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    businessRepository.deleteAll();

    Business sunny = businessRepository.save(business("sunny-scoops", "Sunny Scoops"));
    AppUser ana = new AppUser();
    ana.setEmail("ana@example.com");
    ana.setName("Ana");
    ana.setPasswordHash(passwordEncoder.encode(PASSWORD));
    ana = userRepository.save(ana);
    membershipRepository.save(new Membership(ana.getId(), sunny, MembershipRole.OWNER));
  }

  @Test
  void signIn_LikeTheBrowserDoes_StartsASessionThatReachesMe() throws Exception {
    // 1. The app asks who is signed in: nobody, but the response carries a CSRF token cookie
    MvcResult anonymous =
        mockMvc
            .perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
            .andReturn();
    Cookie xsrf = anonymous.getResponse().getCookie("XSRF-TOKEN");
    assertNotNull(xsrf);

    // 2. It signs in, copying the cookie into the header. The email's case and spaces do not matter
    MvcResult signedIn =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .cookie(xsrf)
                    .header("X-XSRF-TOKEN", xsrf.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(" Ana@Example.com ", PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("ana@example.com"))
            .andExpect(jsonPath("$.businesses[0].slug").value("sunny-scoops"))
            .andExpect(jsonPath("$.businesses[0].role").value("OWNER"))
            .andReturn();
    Cookie session = signedIn.getResponse().getCookie("SESSION");
    assertNotNull(session);
    assertTrue(session.isHttpOnly());
    assertEquals("Lax", ((MockCookie) session).getSameSite());

    // 3. The session cookie alone identifies the user on the next requests
    mockMvc
        .perform(get("/api/auth/me").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ana"));
  }

  @Test
  void signIn_WithAWrongPassword_ReturnsInvalidCredentials() throws Exception {
    mockMvc
        .perform(signIn("ana@example.com", "wrong-password"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void signIn_WithAnUnknownEmail_ReturnsTheSameErrorAsAWrongPassword() throws Exception {
    mockMvc
        .perform(signIn("nobody@example.com", PASSWORD))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void signIn_WithoutACsrfToken_IsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("ana@example.com", PASSWORD)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
  }

  @Test
  void signOut_EndsTheSession() throws Exception {
    Cookie session =
        mockMvc
            .perform(signIn("ana@example.com", PASSWORD))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie("SESSION");

    Cookie xsrf = xsrfCookie();
    mockMvc
        .perform(
            post("/api/auth/logout").cookie(session, xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void marketplaceReads_StayPublic() throws Exception {
    mockMvc.perform(get("/api/businesses")).andExpect(status().isOk());
  }

  private MockHttpServletRequestBuilder signIn(String email, String password) throws Exception {
    Cookie xsrf = xsrfCookie();
    return post("/api/auth/login")
        .cookie(xsrf)
        .header("X-XSRF-TOKEN", xsrf.getValue())
        .contentType(MediaType.APPLICATION_JSON)
        .content(credentials(email, password));
  }

  // Any response carries the CSRF token cookie, like the browser gets it. spring-security-test's
  // csrf() is not used on purpose: it swaps the filter's cookie repository for a session-based one
  // for the rest of the shared context, and XOR-encodes the token, which the SPA setup rejects
  private Cookie xsrfCookie() throws Exception {
    Cookie xsrf =
        mockMvc.perform(get("/api/businesses")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    assertNotNull(xsrf);
    return xsrf;
  }

  private static String credentials(String email, String password) {
    return """
        {"email": "%s", "password": "%s"}
        """
        .formatted(email, password);
  }
}
