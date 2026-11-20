package io.metersphere.agent.service;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/** Execute the actual production @Transactional boundaries against the test database. */
final class BusinessAssetTransactionProxy {
  private BusinessAssetTransactionProxy() {}

  @SuppressWarnings("unchecked")
  static <T> T wrap(T target, JdbcTemplate jdbc) {
    var factory = new ProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAdvice(
        new TransactionInterceptor(
            new DataSourceTransactionManager(jdbc.getDataSource()),
            new AnnotationTransactionAttributeSource()));
    return (T) factory.getProxy();
  }
}
