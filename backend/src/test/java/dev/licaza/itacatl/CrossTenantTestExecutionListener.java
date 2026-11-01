package dev.licaza.itacatl;

import dev.licaza.itacatl.tenant.TenantContext;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Starts every Spring test in the cross-tenant scope, so slice tests can build several businesses
 * in one transaction. Registered in META-INF/spring.factories.
 */
public class CrossTenantTestExecutionListener implements TestExecutionListener, Ordered {

  private static final String SCOPE = CrossTenantTestExecutionListener.class.getName() + ".scope";

  // Before TransactionalTestExecutionListener (4000), whose transaction reads the tenant
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
