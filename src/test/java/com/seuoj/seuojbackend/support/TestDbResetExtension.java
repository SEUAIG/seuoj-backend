package com.seuoj.seuojbackend.support;

import javax.sql.DataSource;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringExtension;

public class TestDbResetExtension implements BeforeAllCallback, AfterAllCallback {

    private static final String RESET_SCRIPT = "sql/test-reset.sql";

    @Override
    public void beforeAll(ExtensionContext context) {
        resetDatabase(context);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        resetDatabase(context);
    }

    private void resetDatabase(ExtensionContext context) {
        DataSource dataSource = SpringExtension.getApplicationContext(context).getBean(DataSource.class);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource(RESET_SCRIPT)
        );
        populator.execute(dataSource);
    }
}
