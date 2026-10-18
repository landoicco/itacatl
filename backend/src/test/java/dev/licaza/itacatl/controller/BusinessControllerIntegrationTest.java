package dev.licaza.itacatl.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.licaza.itacatl.domain.OpeningStatus;
import dev.licaza.itacatl.dto.BusinessSummaryDTO;
import dev.licaza.itacatl.exception.ResourceNotFoundException;
import dev.licaza.itacatl.security.SecurityConfig;
import dev.licaza.itacatl.service.BusinessService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Checks the HTTP contract the frontend relies on: JSON shapes, date formats and error bodies.
// The real security rules are imported, so the test also proves these endpoints are public.
@WebMvcTest(BusinessController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class BusinessControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private BusinessService businessService;

  @Test
  void getBusinesses_ReturnsOpeningStatusAsLocalIsoDateTimes() throws Exception {
    when(businessService.getBusinesses())
        .thenReturn(
            List.of(
                new BusinessSummaryDTO(
                    "sunny-scoops",
                    "Sunny Scoops",
                    "Sorbets",
                    "America/Mexico_City",
                    OpeningStatus.closedUntil(LocalDateTime.of(2026, 10, 17, 12, 0)),
                    List.of("Lime sorbet"))));

    mockMvc
        .perform(get("/api/businesses"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].slug").value("sunny-scoops"))
        .andExpect(jsonPath("$[0].timeZone").value("America/Mexico_City"))
        .andExpect(jsonPath("$[0].openingStatus.state").value("CLOSED"))
        .andExpect(jsonPath("$[0].openingStatus.opensAt").value("2026-10-17T12:00:00"))
        .andExpect(jsonPath("$[0].featuredItems[0]").value("Lime sorbet"));
  }

  @Test
  void getBusiness_UnknownSlug_ReturnsProblemDetailWithCode() throws Exception {
    when(businessService.getBusiness("nope")).thenThrow(ResourceNotFoundException.business("nope"));

    mockMvc
        .perform(get("/api/businesses/nope"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("BUSINESS_NOT_FOUND"));
  }
}
