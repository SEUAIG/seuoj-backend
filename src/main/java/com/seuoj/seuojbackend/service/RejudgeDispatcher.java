package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.JudgeSubmissionRequest;
import com.seuoj.seuojbackend.common.SubmissionStatus;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import com.seuoj.seuojbackend.storage.CodeStorage;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 重测送评队列。判题端同时只评少数几份，整题重测一次可能有上千条：
 * 这里逐条送出，判题端手上已有 maxInFlight 份在评时就等，新提交（提交时直接送评）可以随时插进来。
 * maxInFlight 跟随判题端当前并发（见 JudgeConcurrencyService）。
 * <p>
 * 队列只在内存里；重启后把最近 24 小时仍为 Pending 的提交重新放回队列。
 */
@Slf4j
@Component
public class RejudgeDispatcher {
    /** 超过这个时间仍为 Running 的视为丢失的回调，不再占用名额 */
    static final int STALE_MINUTES = 10;

    private final SubmissionMapper submissions;
    private final ProblemMapper problems;
    private final CodeStorage codeStorage;
    private final JudgeClient judgeClient;
    /** 初始值来自配置，之后由 JudgeConcurrencyService 按判题端实际并发更新 */
    private volatile int maxInFlight;
    private final long pollMillis;

    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Set<String> queued = ConcurrentHashMap.newKeySet();
    private volatile Thread worker;
    private volatile boolean running = true;

    public RejudgeDispatcher(SubmissionMapper submissions, ProblemMapper problems, CodeStorage codeStorage,
                             JudgeClient judgeClient,
                             @Value("${seuoj.rejudge.max-in-flight:2}") int maxInFlight,
                             @Value("${seuoj.rejudge.poll-millis:1000}") long pollMillis) {
        this.submissions = submissions;
        this.problems = problems;
        this.codeStorage = codeStorage;
        this.judgeClient = judgeClient;
        this.maxInFlight = Math.max(1, maxInFlight);
        this.pollMillis = Math.max(10, pollMillis);
    }

    /** 事务提交后调用：提交此时已是 Pending */
    public void enqueue(Collection<String> submissionNos) {
        for (String no : submissionNos) if (queued.add(no)) queue.add(no);
        ensureWorker();
    }

    public int pending() {
        return queue.size();
    }

    /** 判题端并发变化时调用：重测最多占满判题端的名额，不再多送 */
    public void updateMaxInFlight(int limit) {
        int next = Math.max(1, limit);
        if (next != maxInFlight) {
            log.info("重测送评上限 {} -> {}", maxInFlight, next);
            maxInFlight = next;
        }
    }

    public int maxInFlight() {
        return maxInFlight;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        List<Submission> left = submissions.selectList(new LambdaQueryWrapper<Submission>()
                .select(Submission::getId, Submission::getSubmissionNo)
                .eq(Submission::getStatus, SubmissionStatus.PENDING.getStatus())
                .ge(Submission::getUpdatedAt, LocalDateTime.now().minusHours(24))
                .orderByAsc(Submission::getId));
        if (!left.isEmpty()) {
            log.info("重测队列恢复 {} 条 Pending 提交", left.size());
            enqueue(left.stream().map(Submission::getSubmissionNo).toList());
        }
    }

    private synchronized void ensureWorker() {
        if (worker != null && worker.isAlive()) return;
        worker = new Thread(this::loop, "rejudge-dispatcher");
        worker.setDaemon(true);
        worker.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        Thread t = worker;
        if (t != null) t.interrupt();
    }

    private void loop() {
        while (running) {
            String no;
            try {
                no = queue.take();
            } catch (InterruptedException e) {
                return;
            }
            try {
                waitForCapacity();
                dispatch(no);
            } catch (InterruptedException e) {
                return;
            } catch (RuntimeException e) {
                log.error("重测送评失败 submissionNo={}", no, e);
            } finally {
                queued.remove(no);
            }
        }
    }

    private void waitForCapacity() throws InterruptedException {
        while (running) {
            Long busy = submissions.selectCount(new LambdaQueryWrapper<Submission>()
                    .eq(Submission::getStatus, SubmissionStatus.RUNNING.getStatus())
                    .ge(Submission::getUpdatedAt, LocalDateTime.now().minusMinutes(STALE_MINUTES)));
            if (busy == null || busy < maxInFlight) return;
            Thread.sleep(pollMillis);
        }
    }

    /** 只送仍为 Pending 的；送出后改 Running，送不出改 Failed（与普通提交一致） */
    void dispatch(String no) {
        Submission s = submissions.selectOne(new LambdaQueryWrapper<Submission>().eq(Submission::getSubmissionNo, no));
        if (s == null || !SubmissionStatus.PENDING.getStatus().equals(s.getStatus())) return;
        Problem problem = problems.selectById(s.getProblemId());
        String code;
        try {
            code = codeStorage.getCode(no);
        } catch (RuntimeException e) {
            code = null;
        }
        if (problem == null || code == null) {
            log.warn("重测找不到题目或代码，标记为 Failed submissionNo={}", no);
            markFrom(no, SubmissionStatus.FAILED);
            return;
        }
        try {
            judgeClient.submit(new JudgeSubmissionRequest(no, problem.getPid(), code, s.getLanguage()));
            markFrom(no, SubmissionStatus.RUNNING);
        } catch (RuntimeException e) {
            log.error("重测送评失败 submissionNo={}", no, e);
            markFrom(no, SubmissionStatus.FAILED);
        }
    }

    private void markFrom(String no, SubmissionStatus to) {
        submissions.update(null, new LambdaUpdateWrapper<Submission>()
                .set(Submission::getStatus, to.getStatus())
                .eq(Submission::getSubmissionNo, no)
                .eq(Submission::getStatus, SubmissionStatus.PENDING.getStatus()));
    }
}
