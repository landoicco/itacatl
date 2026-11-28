package dev.licaza.itacatl;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// The endpoints the backend instance calls: the full health before going into service, liveness
// from the watchdog afterwards. Both must answer without signing in
@SpringBootTest
@AutoConfigureMockMvc
@Import(AppRoleTestcontainersConfiguration.class)
@ActiveProfiles("test")
class HealthProbesIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void health_WithoutSignIn_IsUp() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void liveness_WithoutSignIn_IsUp() throws Exception {
    mockMvc
        .perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }
}
