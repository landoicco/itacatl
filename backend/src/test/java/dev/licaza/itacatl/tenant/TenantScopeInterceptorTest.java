package dev.licaza.itacatl.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

// A plain unit test: the thread starts with no scope, like a server thread
class TenantScopeInterceptorTest {

  private final TenantScopeInterceptor interceptor = new TenantScopeInterceptor(request -> 7L);
  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @Test
  void aCompletedRequest_LeavesTheThreadWithoutItsTenant() {
    interceptor.preHandle(request, response, null);
    assertEquals(7L, TenantContext.current());

    interceptor.afterCompletion(request, response, null, null);
    assertEquals(TenantContext.NONE, TenantContext.current());
  }

  @Test
  void anAsyncRequest_ReleasesItsThreadWithoutItsTenant() {
    interceptor.preHandle(request, response, null);
    interceptor.afterConcurrentHandlingStarted(request, response, null);
    assertEquals(TenantContext.NONE, TenantContext.current());

    // The async dispatch that completes the request opens and closes a scope of its own
    interceptor.preHandle(request, response, null);
    assertEquals(7L, TenantContext.current());
    interceptor.afterCompletion(request, response, null, null);
    assertEquals(TenantContext.NONE, TenantContext.current());
  }
}
