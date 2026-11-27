package dev.licaza.itacatl.security;

import static dev.licaza.itacatl.MarketplaceTestData.business;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.licaza.itacatl.AppRoleTestcontainersConfiguration;
import dev.licaza.itacatl.BrowserSession;
import dev.licaza.itacatl.ServerThreadMockMvcConfig;
import dev.licaza.itacatl.domain.AppUser;
import dev.licaza.itacatl.domain.Business;
import dev.licaza.itacatl.domain.Membership;
import dev.licaza.itacatl.domain.MembershipRole;
import dev.licaza.itacatl.repo.AppUserRepository;
import dev.licaza.itacatl.repo.BusinessRepository;
import dev.licaza.itacatl.repo.MembershipRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// The cookies as the aws profile sets them. MockMvc's requests are plain HTTP, like CloudFront's
// to the instance, so the cookies are Secure only because the setting says so
@SpringBootTest(properties = "itacatl.session.cookie.secure=true")
@AutoConfigureMockMvc
@Import({AppRoleTestcontainersConfiguration.class, ServerThreadMockMvcConfig.class})
@ActiveProfiles("test")
class SecureCookiesIntegrationTest {

  private static final String PASSWORD = "correct-horse-battery";

  @Autowired private MockMvc mockMvc;
  @Autowired private AppUserRepository userRepository;
  @Autowired private BusinessRepository businessRepository;
  @Autowired private MembershipRepository membershipRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void csrfCookie_OverPlainHttp_IsSecure() throws Exception {
    Cookie xsrf =
        mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");

    assertNotNull(xsrf);
    assertTrue(xsrf.getSecure(), "the CSRF cookie must only travel over HTTPS");
  }

  @Test
  void sessionCookie_OverPlainHttp_IsSecure() throws Exception {
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

    Cookie session =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .with(BrowserSession.anonymous(mockMvc).cookies())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email": "ana@example.com", "password": "%s"}
                        """
                            .formatted(PASSWORD)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie("SESSION");

    assertNotNull(session);
    assertTrue(session.getSecure(), "the session cookie must only travel over HTTPS");
  }
}
