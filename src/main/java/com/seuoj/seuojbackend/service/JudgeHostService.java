package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.JudgeClient.HostSetting;
import com.seuoj.seuojbackend.client.dto.JudgeHostsStatus;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.JudgeHostMapper;
import com.seuoj.seuojbackend.mapper.SystemSettingMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 评测机：判题端里的每台评测机同一时间评一份提交，固定在一个 CPU 核上。
 * <p>
 * 评测机列表存在 system_setting（judge.hosts，形如 {@code 0:1,1:1,2:0}，冒号后 1 表示启用），修改时立即推给判题端；
 * 判题端重启后会回到 .env 的默认台数，所以后端每 30 秒核对一次，不一致就再推。没有设置时不干预判题端。
 * 重测送评上限（RejudgeDispatcher）始终等于判题端当前启用的评测机数。
 */
@Slf4j
@Service
public class JudgeHostService {
    static final String KEY = "judge.hosts";
    /** 最多台数。判题端容器限 32 核，台数越多其余服务越挤 */
    public static final int MAX_HOSTS = 16;
    public static final int MAX_HOST_ID = 999;
    public static final int JUDGINGS_LIMIT = 100;

    private final SystemSettingMapper settings;
    private final JudgeHostMapper judgings;
    private final JudgeClient judge;
    private final RejudgeDispatcher dispatcher;
    private final RejudgeService rejudge;
    private final UserRoleService roles;
    private volatile String lastSyncError;

    public JudgeHostService(SystemSettingMapper settings, JudgeHostMapper judgings, JudgeClient judge,
                            RejudgeDispatcher dispatcher, RejudgeService rejudge, UserRoleService roles) {
        this.settings = settings;
        this.judgings = judgings;
        this.judge = judge;
        this.dispatcher = dispatcher;
        this.rejudge = rejudge;
        this.roles = roles;
    }

    /**
     * @param judge      判题端实时状态（含计时探针样本），连不上时为 null
     * @param judgeError 连不上判题端、或推送失败的原因
     */
    public record Status(int maxHosts, boolean canEdit, int rejudgeQueue, int rejudgeMaxInFlight,
                         JudgeHostsStatus judge, String judgeError) {
    }

    /** 一份评测记录 */
    public record Judging(String submissionNo, Long contestId, String pid, String problemTitle, String username,
                          String status, String verdict, Integer score, LocalDateTime judgeStartedAt, Integer judgeMs) {
    }

    public Status status(Long actor) {
        requireAdmin(actor);
        return snapshot(actor, null);
    }

    public Status add(Long actor) {
        return modify(actor, hosts -> {
            if (hosts.size() >= MAX_HOSTS) throw new BadRequestException("最多 " + MAX_HOSTS + " 台评测机");
            int next = hosts.stream().mapToInt(HostSetting::id).max().orElse(-1) + 1;
            if (next > MAX_HOST_ID) throw new BadRequestException("评测机编号已用完");
            List<HostSetting> out = new ArrayList<>(hosts);
            out.add(new HostSetting(next, true));
            return out;
        }, "添加评测机");
    }

    public Status remove(Long actor, int id) {
        return modify(actor, hosts -> {
            requireHost(hosts, id);
            if (hosts.size() <= 1) throw new BadRequestException("至少保留一台评测机");
            return hosts.stream().filter(h -> h.id() != id).toList();
        }, "删除评测机 judge-" + id);
    }

    public Status setEnabled(Long actor, int id, boolean enabled) {
        return modify(actor, hosts -> {
            requireHost(hosts, id);
            return hosts.stream().map(h -> h.id() == id ? new HostSetting(id, enabled) : h).toList();
        }, (enabled ? "启用" : "暂停") + "评测机 judge-" + id);
    }

    public Status setAllEnabled(Long actor, boolean enabled) {
        return modify(actor, hosts -> hosts.stream().map(h -> new HostSetting(h.id(), enabled)).toList(),
                enabled ? "启动全部评测机" : "暂停全部评测机");
    }

    /** 这台评测机最近评过的提交（按最近一次评测算，重测后归到新评测机） */
    public List<Judging> judgings(Long actor, int id) {
        requireAdmin(actor);
        return judgings.recent(hostName(id), JUDGINGS_LIMIT);
    }

    /**
     * 重测这台评测机评过的提交。
     *
     * @param hours 只重测最近多少小时内评的，null 表示不限
     */
    public RejudgeService.Outcome rejudge(Long actor, int id, Integer hours) {
        requireSuperAdmin(actor);
        if (hours != null && hours < 1) throw new BadRequestException("时间范围不正确");
        LocalDateTime since = hours == null ? null : LocalDateTime.now().minusHours(hours);
        return rejudge.rejudgeJudgeHost(actor, hostName(id), since);
    }

    static String hostName(int id) {
        return "judge-" + id;
    }

    private void requireAdmin(Long actor) {
        if (!roles.isAdmin(actor)) throw new ForbiddenException("只有管理员可以查看评测机");
    }

    private void requireSuperAdmin(Long actor) {
        if (!roles.isSuperAdmin(actor)) throw new ForbiddenException("只有超级管理员可以操作评测机");
    }

    private static void requireHost(List<HostSetting> hosts, int id) {
        if (hosts.stream().noneMatch(h -> h.id() == id)) throw new NotFoundException("没有评测机 judge-" + id);
    }

    private Status modify(Long actor, UnaryOperator<List<HostSetting>> change, String what) {
        requireSuperAdmin(actor);
        List<HostSetting> base = configured();
        if (base == null) {
            try {
                base = fromJudge(judge.fetchHosts());
            } catch (RuntimeException e) {
                throw new BadRequestException(e.getMessage() + "，暂时无法修改评测机");
            }
        }
        List<HostSetting> next = change.apply(base);
        settings.put(KEY, format(next), actor);
        log.info("用户 {} {}，评测机现为 {}", actor, what, format(next));
        String error = null;
        try {
            followJudge(judge.updateHosts(next));
        } catch (RuntimeException e) {
            error = e.getMessage() + "。设置已保存，判题端可用后会自动同步";
        }
        return snapshot(actor, error);
    }

    private Status snapshot(Long actor, String pushError) {
        JudgeHostsStatus current = null;
        String error = pushError;
        try {
            current = judge.fetchHosts();
        } catch (RuntimeException e) {
            if (error == null) error = e.getMessage();
        }
        return new Status(MAX_HOSTS, roles.isSuperAdmin(actor), dispatcher.pending(), dispatcher.maxInFlight(),
                current, error);
    }

    /** 判题端当前的列表（不含已删除、正在收尾的） */
    static List<HostSetting> fromJudge(JudgeHostsStatus status) {
        List<HostSetting> out = new ArrayList<>();
        if (status.getHosts() != null) {
            for (JudgeHostsStatus.Host h : status.getHosts()) {
                if (h.getId() == null || Boolean.TRUE.equals(h.getRemoving())) continue;
                out.add(new HostSetting(h.getId(), !Boolean.FALSE.equals(h.getEnabled())));
            }
        }
        return out;
    }

    static String format(List<HostSetting> hosts) {
        return String.join(",", hosts.stream().map(h -> h.id() + ":" + (h.enabled() ? 1 : 0)).toList());
    }

    /** 保存的列表；没有或格式不对时为 null */
    List<HostSetting> configured() {
        String raw = settings.find(KEY);
        if (raw == null || raw.isBlank()) return null;
        List<HostSetting> out = new ArrayList<>();
        try {
            for (String part : raw.split(",")) {
                String[] kv = part.trim().split(":");
                if (kv.length != 2) return null;
                int id = Integer.parseInt(kv[0].trim());
                if (id < 0 || id > MAX_HOST_ID || out.stream().anyMatch(h -> h.id() == id)) return null;
                out.add(new HostSetting(id, "1".equals(kv[1].trim())));
            }
        } catch (NumberFormatException e) {
            return null;
        }
        out.sort(java.util.Comparator.comparingInt(HostSetting::id));
        return out.isEmpty() || out.size() > MAX_HOSTS ? null : out;
    }

    private void followJudge(JudgeHostsStatus status) {
        long enabled = fromJudge(status).stream().filter(HostSetting::enabled).count();
        dispatcher.updateMaxInFlight((int) Math.max(1, enabled));
    }

    /** 让判题端回到保存的评测机列表，并让重测送评上限跟上 */
    @Scheduled(initialDelayString = "${seuoj.judge-hosts.initial-delay-millis:5000}",
            fixedDelayString = "${seuoj.judge-hosts.sync-millis:30000}")
    public void sync() {
        try {
            List<HostSetting> wanted = configured();
            JudgeHostsStatus current = judge.fetchHosts();
            if (wanted != null && !wanted.equals(fromJudge(current))) {
                log.info("判题端评测机为 {}，与设置 {} 不一致，重新下发", format(fromJudge(current)), format(wanted));
                current = judge.updateHosts(wanted);
            }
            followJudge(current);
            lastSyncError = null;
        } catch (RuntimeException e) {
            String message = e.getMessage();
            if (!Objects.equals(message, lastSyncError)) log.warn("同步评测机失败：{}", message);
            lastSyncError = message;
        }
    }
}
