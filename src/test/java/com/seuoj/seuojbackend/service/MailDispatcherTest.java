package com.seuoj.seuojbackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MailDispatcherTest {

    private static final long COOLDOWN = 120_000;
    private static final long ALERT_INTERVAL = 1_800_000;

    private JavaMailSender primary;
    private JavaMailSender backup;
    private AtomicLong now;
    private MailDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        primary = mock(JavaMailSender.class);
        backup = mock(JavaMailSender.class);
        now = new AtomicLong(1_000_000);
        dispatcher = new MailDispatcher(
                List.of(new MailDispatcher.Channel("smtp.resend.com", primary, "SEUOJ <noreply@seuoj.best>"),
                        new MailDispatcher.Channel("smtp.qq.com", backup, "bot@qq.com")),
                List.of("admin@example.com"), COOLDOWN, ALERT_INTERVAL, Runnable::run, now::get);
    }

    private List<SimpleMailMessage> sent(JavaMailSender sender, int count) {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, times(count)).send(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void usesPrimaryWhenHealthy() {
        dispatcher.send("u@seu.edu.cn", "s", "t");

        SimpleMailMessage msg = sent(primary, 1).get(0);
        assertEquals("SEUOJ <noreply@seuoj.best>", msg.getFrom());
        assertArrayEquals(new String[]{"u@seu.edu.cn"}, msg.getTo());
        verify(backup, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void failsOverToBackupAndAlertsOnce() {
        doThrow(new MailSendException("550 daily quota exceeded")).when(primary).send(any(SimpleMailMessage.class));

        dispatcher.send("u@seu.edu.cn", "s", "t");

        List<SimpleMailMessage> backupMails = sent(backup, 2);
        assertEquals("bot@qq.com", backupMails.get(0).getFrom());
        assertArrayEquals(new String[]{"u@seu.edu.cn"}, backupMails.get(0).getTo());
        assertArrayEquals(new String[]{"admin@example.com"}, backupMails.get(1).getTo());
        assertTrue(backupMails.get(1).getText().contains("daily quota exceeded"));

        // 冷却期内直接走备用，不再碰主通道，也不重复告警
        reset(backup);
        now.addAndGet(10_000);
        dispatcher.send("v@seu.edu.cn", "s", "t");
        verify(primary, times(1)).send(any(SimpleMailMessage.class));
        assertEquals(1, sent(backup, 1).size());
    }

    @Test
    void retriesPrimaryAfterCooldownAndReportsRecovery() {
        doThrow(new MailSendException("connect timed out")).when(primary).send(any(SimpleMailMessage.class));
        dispatcher.send("u@seu.edu.cn", "s", "t");

        doNothing().when(primary).send(any(SimpleMailMessage.class));
        reset(backup);
        now.addAndGet(COOLDOWN + 1);
        dispatcher.send("v@seu.edu.cn", "s", "t");

        // 主通道：失败的 u、恢复后的 v、经主通道发出的恢复告警
        List<SimpleMailMessage> primaryMails = sent(primary, 3);
        assertArrayEquals(new String[]{"v@seu.edu.cn"}, primaryMails.get(1).getTo());
        assertTrue(primaryMails.get(2).getSubject().contains("已恢复"));
        verify(backup, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void throwsWhenAllChannelsFail() {
        doThrow(new MailSendException("primary down")).when(primary).send(any(SimpleMailMessage.class));
        doThrow(new MailSendException("backup down")).when(backup).send(any(SimpleMailMessage.class));

        MailSendException e = assertThrows(MailSendException.class,
                () -> dispatcher.send("u@seu.edu.cn", "s", "t"));
        assertEquals("backup down", e.getMessage());
    }

    @Test
    void badAddressDoesNotFailOver() {
        doThrow(new MailParseException("bad address")).when(primary).send(any(SimpleMailMessage.class));

        assertThrows(MailParseException.class, () -> dispatcher.send("not an address", "s", "t"));
        verify(backup, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void worksWithOnlyOneChannel() {
        MailDispatcher single = new MailDispatcher(
                List.of(new MailDispatcher.Channel("smtp.qq.com", backup, "bot@qq.com")),
                List.of(), COOLDOWN, ALERT_INTERVAL, Runnable::run, now::get);

        single.send("u@seu.edu.cn", "s", "t");

        assertEquals("bot@qq.com", sent(backup, 1).get(0).getFrom());
    }
}
