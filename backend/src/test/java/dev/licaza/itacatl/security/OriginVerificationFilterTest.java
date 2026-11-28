package dev.licaza.itacatl.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OriginVerificationFilterTest {

  private final OriginVerificationFilter filter = new OriginVerificationFilter("s3cret-value");

  @Test
  void aRequestWithTheSecret_GoesThrough() throws Exception {
    MockHttpServletRequest request = request("/api/businesses");
    request.addHeader(OriginVerificationFilter.HEADER, "s3cret-value");

    MockFilterChain chain = filter(request).chain();

    assertNotNull(chain.getRequest(), "the request must reach the app");
  }

  @Test
  void aRequestWithoutTheSecret_IsRejected() throws Exception {
    Result result = filter(request("/api/businesses"));

    assertEquals(403, result.response().getStatus());
    assertNull(result.chain().getRequest(), "the request must not reach the app");
  }

  @Test
  void aRequestWithAWrongSecret_IsRejected() throws Exception {
    MockHttpServletRequest request = request("/api/auth/login");
    request.addHeader(OriginVerificationFilter.HEADER, "s3cret-valuf");

    assertEquals(403, filter(request).response().getStatus());
  }

  @Test
  void theInstancesHealthChecks_NeedNoSecret() throws Exception {
    for (String path :
        new String[] {
          "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"
        }) {
      assertNotNull(filter(request(path)).chain().getRequest(), path + " must stay reachable");
    }
  }

  @Test
  void pathsThatOnlyLookLikeHealthChecks_NeedTheSecret() throws Exception {
    for (String path :
        new String[] {
          "/actuator/healthx", "/actuator/health/db", "/actuator/health/..;/api/businesses"
        }) {
      assertEquals(403, filter(request(path)).response().getStatus(), path + " must be rejected");
    }
  }

  @Test
  void aBlankSecret_IsRefused() {
    assertThrows(IllegalArgumentException.class, () -> new OriginVerificationFilter(" "));
  }

  private static MockHttpServletRequest request(String path) {
    return new MockHttpServletRequest("GET", path);
  }

  private Result filter(MockHttpServletRequest request) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, response, chain);
    return new Result(response, chain);
  }

  private record Result(MockHttpServletResponse response, MockFilterChain chain) {}
}
