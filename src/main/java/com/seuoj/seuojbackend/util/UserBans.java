package com.seuoj.seuojbackend.util;

import com.seuoj.seuojbackend.entity.UserInfo;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 全站封禁的判定与提示文字。封禁到期后不需要清字段，按时间自然失效。 */
public final class UserBans {
    private static final DateTimeFormatter UNTIL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private UserBans() {}

    public static boolean isBanned(UserInfo user, LocalDateTime now) {
        if (user == null || user.getBannedAt() == null) return false;
        return user.getBannedUntil() == null || user.getBannedUntil().isAfter(now);
    }

    public static boolean isBanned(UserInfo user) {
        return isBanned(user, LocalDateTime.now());
    }

    public static String message(UserInfo user) {
        StringBuilder text = new StringBuilder("账号已被封禁");
        if (user.getBannedUntil() == null) text.append("（永久）");
        else text.append("，解封时间 ").append(user.getBannedUntil().format(UNTIL_FORMAT));
        if (user.getBanReason() != null && !user.getBanReason().isBlank()) text.append("。原因：").append(user.getBanReason());
        return text.toString();
    }
}
