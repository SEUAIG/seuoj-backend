package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 提交前的两道限制，题库/作业提交和比赛提交共用：
 * <ul>
 *   <li>同一个人两次提交至少隔 10 秒（只记在本进程内存里，后端重启后清零）；</li>
 *   <li>不许重复提交自己交过的代码：同一道题、同一语言、同一场比赛（或同一份作业、或都在两者之外），
 *       去掉所有空白字符后完全相同即算重复。评测系统故障、已删除、被管理者跳过的旧提交不算。</li>
 * </ul>
 */
@Component
public class SubmissionGuard {

    static final long COOLDOWN_MS = 10_000;

    private final SubmissionMapper submissionMapper;
    private final LongSupplier clock;
    private final long cooldownMs;
    private final ConcurrentHashMap<Long, Long> lastSubmitAt = new ConcurrentHashMap<>();

    /** 冷却时长可用 seuoj.submission.cooldown-ms 覆盖（集成测试里设 0） */
    @Autowired
    public SubmissionGuard(SubmissionMapper submissionMapper,
                           @Value("${seuoj.submission.cooldown-ms:" + COOLDOWN_MS + "}") long cooldownMs) {
        this(submissionMapper, System::currentTimeMillis, cooldownMs);
    }

    SubmissionGuard(SubmissionMapper submissionMapper, LongSupplier clock, long cooldownMs) {
        this.submissionMapper = submissionMapper;
        this.clock = clock;
        this.cooldownMs = cooldownMs;
    }

    /** 去掉所有空白字符后的 SHA-256（64 位十六进制） */
    public static String codeHash(String code) {
        StringBuilder sb = new StringBuilder(code.length());
        code.codePoints().filter(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c)).forEach(sb::appendCodePoint);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void assertNotDuplicate(Long userId, Long problemId, String language, String codeHash,
                                   Long contestId, Long assignmentId) {
        if (submissionMapper.countSameCode(userId, problemId, language, codeHash, contestId, assignmentId) > 0) {
            throw new BadRequestException("这份代码你已经提交过了（忽略空格和换行后完全相同）");
        }
    }

    /** 距上次提交不足冷却时间就拒绝；通过则把这次记为上次提交。并发请求只有一个能通过。 */
    public void acquireCooldown(Long userId) {
        long now = clock.getAsLong();
        long[] waitMs = {0};
        lastSubmitAt.compute(userId, (id, last) -> {
            if (last != null && now - last < cooldownMs) {
                waitMs[0] = cooldownMs - (now - last);
                return last;
            }
            return now;
        });
        if (waitMs[0] > 0) {
            throw new BadRequestException("提交太频繁了，请 " + ((waitMs[0] + 999) / 1000) + " 秒后再交");
        }
    }
}
