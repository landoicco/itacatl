package dev.licaza.itacatl.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.licaza.itacatl.AppRoleTestcontainersConfiguration;
import dev.licaza.itacatl.BrowserSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Its own low limit, so it does not share buckets with the other tests (application-test lifts it)
@SpringBootTest(properties = "itacatl.sign-in.attempts-per-email=2")
@AutoConfigureMockMvc
@Import(AppRoleTestcontainersConfiguration.class)
@ActiveProfiles("test")
class SignInRateLimitIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void signIn_OverTheLimit_ReturnsTooManyAttemptsWithRetryAfter() throws Exception {
    mockMvc.perform(signIn()).andExpect(status().isUnauthorized());
    mockMvc.perform(signIn()).andExpect(status().isUnauthorized());

    mockMvc
        .perform(signIn())
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
        .andExpect(header().exists("Retry-After"));
  }

  private MockHttpServletRequestBuilder signIn() throws Exception {
    return post("/api/auth/login")
        .with(BrowserSession.anonymous(mockMvc).cookies())
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"email": "nobody@example.com", "password": "wrong-password"}
            """);
  }
}
