package com.seuoj.seuojbackend.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MailDispatcherWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(MailDispatcher.class)
            .withPropertyValues("spring.mail.host=smtp.qq.com", "spring.mail.username=bot@qq.com");

    @Test
    void withoutPrimaryOnlyUsesSpringMail() {
        runner.run(ctx -> assertEquals(List.of("smtp.qq.com"), ctx.getBean(MailDispatcher.class).channelNames()));
    }

    @Test
    void primaryComesFirst() {
        runner.withPropertyValues("app.mail.primary.host=smtp.resend.com",
                        "app.mail.primary.from=SEUOJ <noreply@seuoj.best>")
                .run(ctx -> assertEquals(List.of("smtp.resend.com", "smtp.qq.com"),
                        ctx.getBean(MailDispatcher.class).channelNames()));
    }

    @Test
    void primaryWithoutFromIsSkipped() {
        runner.withPropertyValues("app.mail.primary.host=smtp.resend.com")
                .run(ctx -> assertEquals(List.of("smtp.qq.com"), ctx.getBean(MailDispatcher.class).channelNames()));
    }
}
