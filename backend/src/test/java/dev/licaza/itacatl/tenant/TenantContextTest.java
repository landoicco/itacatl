package dev.licaza.itacatl.tenant;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TenantContextTest {

  // A plain unit test: no Spring, so CrossTenantTestExecutionListener opens no scope here
  @Test
  void closingAScope_RestoresThePreviousTenant() {
    try (TenantContext.Scope outer = TenantContext.open(7L)) {
      try (TenantContext.Scope inner = TenantContext.open(8L)) {
        assertEquals(8L, TenantContext.current());
      }
      assertEquals(7L, TenantContext.current());
    }
    assertEquals(TenantContext.NONE, TenantContext.current());
  }

  @Test
  void aScope_IsOnlyVisibleToItsOwnThread() throws InterruptedException {
    long[] seen = new long[1];
    // Another request's thread does not inherit this one's business
    try (TenantContext.Scope scope = TenantContext.open(7L)) {
      Thread thread = new Thread(() -> seen[0] = TenantContext.current());
      thread.start();
      thread.join();
    }

    assertEquals(TenantContext.NONE, seen[0]);
  }
}
