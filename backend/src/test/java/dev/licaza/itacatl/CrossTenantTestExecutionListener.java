package dev.licaza.itacatl;

import dev.licaza.itacatl.tenant.TenantContext;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Runs every test in the cross-tenant scope, as the seed does: fixtures build several businesses in
 * one transaction and set each row's tenant explicitly. Registered for all tests in
 * META-INF/spring.factories. TenantIsolationIntegrationTest opens narrower scopes inside it.
 */
public class CrossTenantTestExecutionListener implements TestExecutionListener, Ordered {

  private static final String SCOPE = CrossTenantTestExecutionListener.class.getName() + ".scope";

  // Before TransactionalTestExecutionListener (4000), which opens the test's transaction and with
  // it the Hibernate session that reads the tenant
  @Override
  public int getOrder() {
    return 3900;
  }

  @Override
  public void beforeTestMethod(TestContext testContext) {
    testContext.setAttribute(SCOPE, TenantContext.open(TenantContext.CROSS_TENANT));
  }

  @Override
  public void afterTestMethod(TestContext testContext) {
    if (testContext.removeAttribute(SCOPE) instanceof TenantContext.Scope scope) {
      scope.close();
    }
  }
}
