package dev.licaza.itacatl.controller;

import static dev.licaza.itacatl.MarketplaceTestData.business;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.licaza.itacatl.BrowserSession;
import dev.licaza.itacatl.TestcontainersConfiguration;
import dev.licaza.itacatl.domain.AppUser;
import dev.licaza.itacatl.domain.Business;
import dev.licaza.itacatl.domain.Membership;
import dev.licaza.itacatl.domain.MembershipRole;
import dev.licaza.itacatl.repo.AppUserRepository;
import dev.licaza.itacatl.repo.BusinessRepository;
import dev.licaza.itacatl.repo.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// Who may read and change a catalog, over HTTP with real sign-in, method security and validation
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CatalogControllerIntegrationTest {

  private static final String PASSWORD = "correct-horse-battery";
  private static final String ITEMS = "/api/manage/businesses/sunny-scoops/items";
  private static final String LIME =
      """
      {"name": "Lime sorbet", "description": "Tart.", "suggestedPrice": 40, "alcoholic": false}
      """;

  @Autowired private MockMvc mockMvc;
  @Autowired private AppUserRepository userRepository;
  @Autowired private BusinessRepository businessRepository;
  @Autowired private MembershipRepository membershipRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  // The context, and its database, is shared with other full-application tests
  @BeforeEach
  void createMembers() {
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    businessRepository.deleteAll();

    Business sunny = businessRepository.save(business("sunny-scoops", "Sunny Scoops"));
    Business corner = businessRepository.save(business("cold-corner", "Cold Corner"));
    member("ana@example.com", sunny, MembershipRole.OWNER);
    member("luis@example.com", sunny, MembershipRole.STAFF);
    member("rosa@example.com", corner, MembershipRole.OWNER);
  }

  @Test
  void owner_CreatesAndListsItems() throws Exception {
    BrowserSession ana = BrowserSession.signIn(mockMvc, "ana@example.com", PASSWORD);

    mockMvc
        .perform(
            post(ITEMS).with(ana.cookies()).contentType(MediaType.APPLICATION_JSON).content(LIME))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Lime sorbet"))
        .andExpect(jsonPath("$.archived").value(false));

    mockMvc
        .perform(get(ITEMS).with(ana.cookies()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Lime sorbet"));
  }

  @Test
  void staff_CanReadButNotChangeTheCatalog() throws Exception {
    BrowserSession luis = BrowserSession.signIn(mockMvc, "luis@example.com", PASSWORD);

    mockMvc.perform(get(ITEMS).with(luis.cookies())).andExpect(status().isOk());
    mockMvc
        .perform(
            post(ITEMS).with(luis.cookies()).contentType(MediaType.APPLICATION_JSON).content(LIME))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void ownerOfAnotherBusiness_IsDenied() throws Exception {
    BrowserSession rosa = BrowserSession.signIn(mockMvc, "rosa@example.com", PASSWORD);

    mockMvc
        .perform(get(ITEMS).with(rosa.cookies()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void anonymousVisitor_MustSignIn() throws Exception {
    mockMvc
        .perform(get(ITEMS))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
  }

  @Test
  void invalidItem_ListsItsFieldErrors() throws Exception {
    BrowserSession ana = BrowserSession.signIn(mockMvc, "ana@example.com", PASSWORD);

    mockMvc
        .perform(
            post(ITEMS)
                .with(ana.cookies())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "", "suggestedPrice": -5, "alcoholic": false}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[?(@.field == 'name')].code").value("NotBlank"))
        .andExpect(
            jsonPath("$.errors[?(@.field == 'suggestedPrice')].code").value("PositiveOrZero"));
  }

  private void member(String email, Business business, MembershipRole role) {
    AppUser user = new AppUser();
    user.setEmail(email);
    user.setName(email);
    user.setPasswordHash(passwordEncoder.encode(PASSWORD));
    user = userRepository.save(user);
    membershipRepository.save(new Membership(user.getId(), business, role));
  }
}
