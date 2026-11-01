package dev.licaza.itacatl.tenant;

import static dev.licaza.itacatl.MarketplaceTestData.business;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.licaza.itacatl.domain.Business;
import dev.licaza.itacatl.repo.BusinessRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

// The URL rules that pick each request's tenant, without a server
class TenantConfigTest {

  private TenantConfig config;

  @BeforeEach
  void setUp() {
    Business sunny = business("sunny-scoops", "Sunny Scoops");
    sunny.setId(1L);
    BusinessRepository repository = mock(BusinessRepository.class);
    when(repository.findBySlug("sunny-scoops")).thenReturn(Optional.of(sunny));
    when(repository.findBySlug("unknown")).thenReturn(Optional.empty());

    @SuppressWarnings("unchecked")
    ObjectProvider<BusinessRepository> provider = mock(ObjectProvider.class);
    when(provider.getObject()).thenReturn(repository);
    config = new TenantConfig(provider);
  }

  @Test
  void marketplaceReads_AreCrossTenant() {
    assertEquals(TenantContext.CROSS_TENANT, tenantOf("GET", "/api/businesses"));
    assertEquals(TenantContext.CROSS_TENANT, tenantOf("GET", "/api/businesses/sunny-scoops"));
    assertEquals(TenantContext.CROSS_TENANT, tenantOf("HEAD", "/api/menus/7"));
  }

  @Test
  void marketplaceWrites_GetNoTenant() {
    assertEquals(TenantContext.NONE, tenantOf("POST", "/api/menus/7/reservations"));
  }

  @Test
  void managementRequests_GetTheBusinessInTheUrl() {
    assertEquals(1L, tenantOf("GET", "/api/manage/businesses/sunny-scoops/items"));
    assertEquals(1L, tenantOf("POST", "/api/manage/businesses/sunny-scoops/menus/7/publish"));
  }

  @Test
  void managementRequests_ForAnUnknownBusiness_GetNoTenant() {
    assertEquals(TenantContext.NONE, tenantOf("GET", "/api/manage/businesses/unknown/items"));
  }

  @Test
  void otherRequests_GetNoTenant() {
    assertEquals(TenantContext.NONE, tenantOf("GET", "/api/auth/me"));
  }

  private long tenantOf(String method, String path) {
    return config.tenantOf(new MockHttpServletRequest(method, path));
  }
}
