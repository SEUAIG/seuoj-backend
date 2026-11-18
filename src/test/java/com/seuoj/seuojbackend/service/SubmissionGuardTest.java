package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.mapper.SubmissionMapper;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 提交冷却与重复代码拦截 */
class SubmissionGuardTest {
    private final SubmissionMapper mapper = mock(SubmissionMapper.class);
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final SubmissionGuard guard = new SubmissionGuard(mapper, now::get, SubmissionGuard.COOLDOWN_MS);

    @Test
    void secondSubmitWithinTenSecondsIsRejectedWithRemainingSeconds() {
        guard.acquireCooldown(1L);
        now.addAndGet(3_200);
        BadRequestException e = assertThrows(BadRequestException.class, () -> guard.acquireCooldown(1L));
        assertTrue(e.getMessage().contains("7 秒"), e.getMessage());
    }

    @Test
    void rejectedAttemptDoesNotRestartTheCooldown() {
        guard.acquireCooldown(1L);
        now.addAndGet(9_000);
        assertThrows(BadRequestException.class, () -> guard.acquireCooldown(1L));
        now.addAndGet(1_000);
        assertDoesNotThrow(() -> guard.acquireCooldown(1L));
    }

    @Test
    void cooldownIsPerUser() {
        guard.acquireCooldown(1L);
        assertDoesNotThrow(() -> guard.acquireCooldown(2L));
    }

    @Test
    void zeroCooldownNeverBlocks() {
        SubmissionGuard off = new SubmissionGuard(mapper, now::get, 0);
        off.acquireCooldown(1L);
        assertDoesNotThrow(() -> off.acquireCooldown(1L));
    }

    @Test
    void hashIgnoresAllWhitespace() {
        String a = "int main() {\n    return 0;\n}\n";
        String b = "int main(){\r\n\treturn 0;}";
        String c = "int main(){return 1;}";
        assertEquals(SubmissionGuard.codeHash(a), SubmissionGuard.codeHash(b));
        assertNotEquals(SubmissionGuard.codeHash(a), SubmissionGuard.codeHash(c));
        assertEquals(64, SubmissionGuard.codeHash(a).length());
    }

    @Test
    void duplicateIsRejectedAndScopePassedThrough() {
        when(mapper.countSameCode(1L, 5L, "C++17", "h", 13L, null)).thenReturn(1L);
        assertThrows(BadRequestException.class, () -> guard.assertNotDuplicate(1L, 5L, "C++17", "h", 13L, null));
        // 换一场比赛 / 不在比赛里 / 换语言：不算重复
        assertDoesNotThrow(() -> guard.assertNotDuplicate(1L, 5L, "C++17", "h", 14L, null));
        assertDoesNotThrow(() -> guard.assertNotDuplicate(1L, 5L, "C++17", "h", null, null));
        assertDoesNotThrow(() -> guard.assertNotDuplicate(1L, 5L, "Python3", "h", 13L, null));
        verify(mapper, times(4)).countSameCode(anyLong(), anyLong(), anyString(), anyString(), any(), any());
    }
}
