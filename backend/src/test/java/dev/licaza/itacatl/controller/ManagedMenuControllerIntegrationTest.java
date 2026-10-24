package dev.licaza.itacatl.controller;

import static dev.licaza.itacatl.MarketplaceTestData.business;
import static dev.licaza.itacatl.MarketplaceTestData.item;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.licaza.itacatl.BrowserSession;
import dev.licaza.itacatl.FixedClockConfig;
import dev.licaza.itacatl.TestcontainersConfiguration;
import dev.licaza.itacatl.domain.AppUser;
import dev.licaza.itacatl.domain.Business;
import dev.licaza.itacatl.domain.Item;
import dev.licaza.itacatl.domain.Membership;
import dev.licaza.itacatl.domain.MembershipRole;
import dev.licaza.itacatl.repo.AppUserRepository;
import dev.licaza.itacatl.repo.BusinessRepository;
import dev.licaza.itacatl.repo.ItemRepository;
import dev.licaza.itacatl.repo.MembershipRepository;
import java.time.Clock;
import java.time.LocalDate;
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
import tools.jackson.databind.json.JsonMapper;

// Building a menu over HTTP with real sign-in: the owner's flow, who else may do it, and how rule
// violations reach the client
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ManagedMenuControllerIntegrationTest {

  private static final String PASSWORD = "correct-horse-battery";
  private static final String MENUS = "/api/manage/businesses/sunny-scoops/menus";

  private final JsonMapper json = JsonMapper.builder().build();

  @Autowired private MockMvc mockMvc;
  @Autowired private AppUserRepository userRepository;
  @Autowired private BusinessRepository businessRepository;
  @Autowired private MembershipRepository membershipRepository;
  @Autowired private ItemRepository itemRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  // The application's real clock: drafts must be planned for a day that has not passed
  @Autowired private Clock clock;

  private Item lime;
  private Item archived;
  private LocalDate tomorrow;

  // The context, and its database, is shared with other full-application tests
  @BeforeEach
  void createMembersAndCatalog() {
    membershipRepository.deleteAll();
    userRepository.deleteAll();
    businessRepository.deleteAll();

    Business sunny = businessRepository.save(business("sunny-scoops", "Sunny Scoops"));
    Business corner = businessRepository.save(business("cold-corner", "Cold Corner"));
    member("ana@example.com", sunny, MembershipRole.OWNER);
    member("luis@example.com", sunny, MembershipRole.STAFF);
    member("rosa@example.com", corner, MembershipRole.OWNER);

    lime = itemRepository.save(item(sunny, "Lime sorbet", "40.00"));
    Item old = item(sunny, "Old flavor", "30.00");
    old.setArchived(true);
    archived = itemRepository.save(old);
    tomorrow = LocalDate.now(clock.withZone(FixedClockConfig.MEXICO_CITY)).plusDays(1);
  }

  @Test
  void owner_BuildsAMenu() throws Exception {
    BrowserSession ana = BrowserSession.signIn(mockMvc, "ana@example.com", PASSWORD);
    long menuId = createDraft(ana);

    // Added at the catalog's suggested price
    mockMvc
        .perform(
            post(MENUS + "/" + menuId + "/items")
                .with(ana.cookies())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\": %d, \"quantity\": 30}".formatted(lime.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.items[0].name").value("Lime sorbet"))
        .andExpect(jsonPath("$.items[0].price").value(40.0));

    mockMvc
        .perform(
            put(MENUS + "/" + menuId + "/items/" + lime.getId())
                .with(ana.cookies())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\": 35, \"quantity\": 12}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].quantity").value(12));

    mockMvc
        .perform(delete(MENUS + "/" + menuId + "/items/" + lime.getId()).with(ana.cookies()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void owner_PublishesAMenu_ThenPlansTheNextOneFromIt() throws Exception {
    BrowserSession ana = BrowserSession.signIn(mockMvc, "ana@example.com", PASSWORD);
    long menuId = createDraft(ana);
    mockMvc
        .perform(
            post(MENUS + "/" + menuId + "/items")
                .with(ana.cookies())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\": %d, \"quantity\": 30}".formatted(lime.getId())))
        .andExpect(status().isOk());

    mockMvc
        .perform(post(MENUS + "/" + menuId + "/publish").with(ana.cookies()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"));

    String body =
        mockMvc
            .perform(
                post(MENUS + "/" + menuId + "/duplicate")
                    .with(ana.cookies())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(window(tomorrow.plusDays(1), "12:00", "20:00")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.items[0].name").value("Lime sorbet"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long copyId = json.readTree(body).get("id").asLong();

    mockMvc
        .perform(
            put(MENUS + "/" + copyId)
                .with(ana.cookies())
                .contentType(MediaType.APPLICATION_JSON)
                .content(window(tomorrow.plusDays(2), "09:00", "14:00")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.serviceDate").value(tomorrow.plusDays(2).toString()))
        .andExpect(jsonPath("$.startsAt").value("09:00:00"));

    mockMvc
        .perform(delete(MENUS + "/" + copyId).with(ana.cookies()))
        .andExpect(status().isNoContent());
    mockMvc.perform(get(MENUS + "/" + copyId).with(ana.cookies())).andExpect(status().isNotFound());
  }

  @Test
  void staff_SeeDraftsButCannotChangeThem() throws Exception {
    long menuId = createDraft(BrowserSession.signIn(mockMvc, "ana@example.com", PASSWORD));
    BrowserSession luis = BrowserSession.signIn(mockMvc, "luis@example.com", PASSWORD);

    mockMvc
        .perform(get(MENUS).with(luis.cookies()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(menuId));
    mockMvc
        .perform(
            post(MENUS + "/" + menuId + "/items")
                .with(luis.cookies())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\": %d, \"quantity\": 30}".formatted(lime.getId())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    mockMvc
        .perform(post(MENUS + "/" + menuId + "/publish").with(luis.cookies()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete(MENUS + "/" + menuId).with(luis.cookies()))
        .andExpect(status().isForbidden());
  }

  @Test
  void ownerOfAnotherBusiness_IsDenied() throws Exception {
    BrowserSession rosa = BrowserSession.signIn(mockMvc, "rosa@example.com", PASSWORD);

    mockMvc.perform(get(MENUS).with(rosa.cookies())).andExpect(status().isForbidden());
  }

  @Test
  void ruleViolations_AnswerUnprocessableWithACode() throws Exception {
    BrowserSession ana = BrowserSession.signIn(mockMvc, "ana@example.com", PASSWORD);
    long menuId = createDraft(ana);

    mockMvc
        .perform(
            post(MENUS + "/" + menuId + "/items")
                .with(ana.cookies())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\": %d, \"quantity\": 5}".formatted(archived.getId())))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("ITEM_ARCHIVED"));

    mockMvc
        .perform(
            post(MENUS)
                .with(ana.cookies())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"serviceDate": "%s", "startsAt": "20:00", "endsAt": "12:00"}
                    """
                        .formatted(tomorrow)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("INVALID_TIME_WINDOW"));

    mockMvc
        .perform(post(MENUS + "/" + menuId + "/publish").with(ana.cookies()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("MENU_EMPTY"));
  }

  private long createDraft(BrowserSession owner) throws Exception {
    String body =
        mockMvc
            .perform(
                post(MENUS)
                    .with(owner.cookies())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(window(tomorrow, "12:00", "20:00")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private static String window(LocalDate date, String startsAt, String endsAt) {
    return """
        {"serviceDate": "%s", "startsAt": "%s", "endsAt": "%s"}
        """
        .formatted(date, startsAt, endsAt);
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
