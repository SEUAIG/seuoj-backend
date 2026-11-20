package com.seuoj.seuojbackend.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * 统一发信入口：按顺序尝试各通道（主通道在前，spring.mail 配置的通道作备用）。
 * 某通道发信失败后进入冷却期，期间排到最后；改用备用通道或全部失败时发告警。
 * 只能发现 SMTP 当场返回的错误（连不上、认证失败、额度用完），发出后的异步退信看不到。
 */
@Slf4j
@Service
public class MailDispatcher {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final List<Channel> channels;
    private final List<String> alertRecipients;
    private final long cooldownMillis;
    private final long alertIntervalMillis;
    private final int failoverAlertThreshold;
    private final Executor alertExecutor;
    private final LongSupplier clock;
    private final Map<String, Long> lastAlertAt = new ConcurrentHashMap<>();
    /** 是否发过「主通道不可用」告警；只有发过才发「已恢复」。 */
    private volatile boolean failoverAlerted;

    @Autowired
    public MailDispatcher(ObjectProvider<JavaMailSender> defaultSender,
                          @Value("${spring.mail.username:}") String defaultFrom,
                          @Value("${app.mail.primary.host:}") String primaryHost,
                          @Value("${app.mail.primary.port:465}") int primaryPort,
                          @Value("${app.mail.primary.username:}") String primaryUsername,
                          @Value("${app.mail.primary.password:}") String primaryPassword,
                          @Value("${app.mail.primary.from:}") String primaryFrom,
                          @Value("${app.mail.timeout-ms:10000}") int timeoutMs,
                          @Value("${app.mail.failover-cooldown-seconds:120}") long cooldownSeconds,
                          @Value("${app.mail.alert-to:}") String alertTo,
                          @Value("${app.mail.alert-interval-seconds:1800}") long alertIntervalSeconds,
                          @Value("${app.mail.failover-alert-threshold:3}") int failoverAlertThreshold) {
        List<Channel> list = new ArrayList<>();
        if (!primaryHost.isBlank()) {
            if (primaryFrom.isBlank()) {
                log.error("[MAIL] 已配置主通道 {} 但缺少 app.mail.primary.from，主通道不启用", primaryHost);
            } else {
                list.add(new Channel(primaryHost,
                        buildSender(primaryHost, primaryPort, primaryUsername, primaryPassword, timeoutMs),
                        primaryFrom));
            }
        }
        JavaMailSender fallback = defaultSender.getIfAvailable();
        if (fallback != null) {
            String name = "spring.mail";
            if (fallback instanceof JavaMailSenderImpl impl) {
                applyTimeouts(impl.getJavaMailProperties(), timeoutMs);
                if (impl.getHost() != null) {
                    name = impl.getHost();
                }
            }
            list.add(new Channel(name, fallback, defaultFrom));
        }
        this.channels = List.copyOf(list);
        this.alertRecipients = parseRecipients(alertTo);
        this.cooldownMillis = cooldownSeconds * 1000;
        this.alertIntervalMillis = alertIntervalSeconds * 1000;
        this.failoverAlertThreshold = Math.max(1, failoverAlertThreshold);
        this.alertExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mail-alert");
            t.setDaemon(true);
            return t;
        });
        this.clock = System::currentTimeMillis;
        log.info("[MAIL] 发信通道顺序: {}；告警收件人 {} 个",
                channels.stream().map(Channel::name).toList(), alertRecipients.size());
    }

    MailDispatcher(List<Channel> channels, List<String> alertRecipients, long cooldownMillis,
                   long alertIntervalMillis, int failoverAlertThreshold, Executor alertExecutor,
                   LongSupplier clock) {
        this.channels = List.copyOf(channels);
        this.alertRecipients = List.copyOf(alertRecipients);
        this.cooldownMillis = cooldownMillis;
        this.alertIntervalMillis = alertIntervalMillis;
        this.failoverAlertThreshold = Math.max(1, failoverAlertThreshold);
        this.alertExecutor = alertExecutor;
        this.clock = clock;
    }

    List<String> channelNames() {
        return channels.stream().map(Channel::name).toList();
    }

    /**
     * 发一封纯文本邮件；所有通道都失败时抛出最后一个异常。
     */
    public void send(String to, String subject, String text) {
        if (channels.isEmpty()) {
            throw new MailSendException("未配置任何发信通道");
        }
        long now = clock.getAsLong();
        List<String> failures = new ArrayList<>();
        MailException lastError = null;
        for (Channel channel : orderedChannels(now)) {
            try {
                channel.sender().send(buildMessage(channel, to, subject, text));
                afterSuccess(channel, failures);
                return;
            } catch (MailParseException e) {
                // 收件地址或内容本身有问题，换通道也一样
                throw e;
            } catch (MailException e) {
                lastError = e;
                channel.markFailed(now + cooldownMillis);
                String reason = summarize(e);
                failures.add(channel.name() + "：" + reason);
                log.error("[MAIL] 通道 {} 发信失败（收件人 {}）：{}", channel.name(), to, reason);
            }
        }
        alert("all-failed", "【SEUOJ 告警】所有发信通道都失败",
                "所有发信通道都发信失败，用户收不到验证码。\n\n" + String.join("\n", failures));
        throw lastError;
    }

    /** 冷却中的通道排到最后，作为最后手段仍会尝试。 */
    private List<Channel> orderedChannels(long now) {
        List<Channel> ready = new ArrayList<>();
        List<Channel> cooling = new ArrayList<>();
        for (Channel channel : channels) {
            (channel.coolingDown(now) ? cooling : ready).add(channel);
        }
        ready.addAll(cooling);
        return ready;
    }

    private void afterSuccess(Channel used, List<String> failures) {
        Channel preferred = channels.get(0);
        used.recover();
        if (used == preferred) {
            if (failoverAlerted) {
                failoverAlerted = false;
                alert("recovered", "【SEUOJ 告警】主发信通道已恢复",
                        "主通道 " + used.name() + " 发信成功，已恢复使用。");
            }
            return;
        }
        // 偶发一次失败（比如 DNS 抖动）已由备用通道兜住，连续失败才告警
        int streak = preferred.consecutiveFailures();
        if (streak < failoverAlertThreshold) {
            return;
        }
        String detail = failures.isEmpty()
                ? "主通道 " + preferred.name() + " 处于失败后的冷却期。"
                : String.join("\n", failures);
        if (alert("failover", "【SEUOJ 告警】主发信通道不可用，已切到备用通道",
                "主通道 " + preferred.name() + " 已连续失败 " + streak + " 次，已改用 " + used.name()
                        + " 发信。\n\n" + detail
                        + "\n\n常见原因：额度用完、API key 失效、对方服务故障、DNS 解析失败。")) {
            failoverAlerted = true;
        }
    }

    /** 返回是否真的发出（未被限频）。 */
    private boolean alert(String kind, String subject, String body) {
        long now = clock.getAsLong();
        Long last = lastAlertAt.get(kind);
        if (last != null && now - last < alertIntervalMillis) {
            return false;
        }
        lastAlertAt.put(kind, now);
        log.error("[MAIL-ALERT] {}：{}", subject, body.replace('\n', ' '));
        if (alertRecipients.isEmpty()) {
            return true;
        }
        String text = body + "\n\n时间：" + TIME_FORMAT.format(Instant.ofEpochMilli(now))
                + "\n同类告警 " + (alertIntervalMillis / 60000) + " 分钟内只发一次。";
        alertExecutor.execute(() -> deliverAlert(subject, text));
        return true;
    }

    private void deliverAlert(String subject, String text) {
        for (Channel channel : orderedChannels(clock.getAsLong())) {
            try {
                for (String recipient : alertRecipients) {
                    channel.sender().send(buildMessage(channel, recipient, subject, text));
                }
                return;
            } catch (MailException e) {
                log.error("[MAIL-ALERT] 通过 {} 发告警邮件失败：{}", channel.name(), summarize(e));
            }
        }
    }

    private static SimpleMailMessage buildMessage(Channel channel, String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(channel.from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        return message;
    }

    private static JavaMailSenderImpl buildSender(String host, int port, String username, String password,
                                                  int timeoutMs) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        if (port == 465 || port == 2465) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        applyTimeouts(props, timeoutMs);
        return sender;
    }

    private static void applyTimeouts(Properties props, int timeoutMs) {
        String value = String.valueOf(timeoutMs);
        props.putIfAbsent("mail.smtp.connectiontimeout", value);
        props.putIfAbsent("mail.smtp.timeout", value);
        props.putIfAbsent("mail.smtp.writetimeout", value);
    }

    private static List<String> parseRecipients(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String summarize(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
        if (root != e) {
            msg += " / " + root.getClass().getSimpleName() + ": " + root.getMessage();
        }
        msg = msg.replaceAll("\\s+", " ");
        return msg.length() > 400 ? msg.substring(0, 400) + "…" : msg;
    }

    @PreDestroy
    void shutdown() {
        if (alertExecutor instanceof ExecutorService service) {
            service.shutdown();
        }
    }

    static final class Channel {
        private final String name;
        private final JavaMailSender sender;
        private final String from;
        private volatile long coolingUntil;
        private final AtomicInteger consecutiveFailures = new AtomicInteger();

        Channel(String name, JavaMailSender sender, String from) {
            this.name = name;
            this.sender = sender;
            this.from = from;
        }

        String name() { return name; }
        JavaMailSender sender() { return sender; }
        String from() { return from; }

        boolean coolingDown(long now) { return now < coolingUntil; }

        void markFailed(long until) {
            coolingUntil = until;
            consecutiveFailures.incrementAndGet();
        }

        int consecutiveFailures() { return consecutiveFailures.get(); }

        void recover() {
            coolingUntil = 0;
            consecutiveFailures.set(0);
        }
    }
}
