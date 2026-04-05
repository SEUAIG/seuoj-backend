package com.seuoj.seuojbackend.support;

import javax.sql.DataSource;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * 测试数据库重置扩展：每个用例执行前后都运行重置脚本。
 */
public class TestDbResetExtension implements BeforeEachCallback, AfterEachCallback {

    private static final String RESET_SCRIPT = "sql/test-reset.sql";

    /**
     * 用例执行前重置数据库到基线状态。
     */
    @Override
    public void beforeEach(ExtensionContext context) {
        resetDatabase(context);
    }

    /**
     * 用例执行后再次重置数据库，避免脏数据外溢。
     */
    @Override
    public void afterEach(ExtensionContext context) {
        resetDatabase(context);
    }

    /**
     * 执行 SQL 重置脚本。
     */
    private void resetDatabase(ExtensionContext context) {
        DataSource dataSource = SpringExtension.getApplicationContext(context).getBean(DataSource.class);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource(RESET_SCRIPT)
        );
        populator.execute(dataSource);
    }
}
