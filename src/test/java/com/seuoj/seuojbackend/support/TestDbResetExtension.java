package com.seuoj.seuojbackend.support;

import javax.sql.DataSource;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringExtension;

public class TestDbResetExtension implements BeforeEachCallback, AfterEachCallback {

    private static final String RESET_SCRIPT = "sql/test-reset.sql";

    @Override
    public void beforeEach(ExtensionContext context) {
        resetDatabase(context);
    }

    @Override
    public void afterEach(ExtensionContext context) {
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
