package dev.licaza.itacatl.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// The shape of request errors, independent of any feature: a throwaway controller with a validated
// body, wired to the real GlobalExceptionHandler
class GlobalExceptionHandlerTest {

  record ItemForm(@NotBlank String name, @PositiveOrZero BigDecimal price) {}

  @RestController
  static class FormController {
    @PostMapping("/form")
    void submit(@Valid @RequestBody ItemForm form) {}
  }

  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(new FormController())
          .setControllerAdvice(new GlobalExceptionHandler())
          .build();

  @Test
  void invalidFields_AreListedWithTheirConstraint() throws Exception {
    mockMvc
        .perform(
            post("/form")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \" \", \"price\": -1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(jsonPath("$.errors[?(@.field == 'name')].code").value("NotBlank"))
        .andExpect(jsonPath("$.errors[?(@.field == 'price')].code").value("PositiveOrZero"));
  }

  @Test
  void aBodyThatIsNotJson_IsMalformed() throws Exception {
    mockMvc
        .perform(post("/form").contentType(MediaType.APPLICATION_JSON).content("{name:"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }
}
