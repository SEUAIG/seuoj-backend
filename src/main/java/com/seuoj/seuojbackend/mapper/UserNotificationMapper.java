package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.UserNotification;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserNotificationMapper extends BaseMapper<UserNotification> {

    /**
     * 给比赛里报名的每个人写一条通知，跳过 skip1、skip2（操作者本人、提问人）。
     * 某人对同一对象（ref）还有一条 sameRef1 / sameRef2 类型的通知没弹，就不再重复写。
     */
    @Insert("INSERT INTO user_notification (user_id, type, contest_id, ref_id, created_at) "
            + "SELECT DISTINCT r.user_id, #{type}, #{contestId}, #{refId}, #{now} FROM contest_register_rel r "
            + "WHERE r.contest_id = #{contestId} AND r.is_del = 0 AND r.user_id IS NOT NULL "
            + "AND r.user_id <> #{skip1} AND r.user_id <> #{skip2} "
            + "AND NOT EXISTS (SELECT 1 FROM user_notification n WHERE n.user_id = r.user_id AND n.popped_at IS NULL "
            + "AND n.ref_id = #{refId} AND n.type IN (#{sameRef1}, #{sameRef2}))")
    int fanOutToRegistrants(@Param("contestId") Long contestId, @Param("type") String type, @Param("refId") Long refId,
                            @Param("now") LocalDateTime now, @Param("skip1") Long skip1, @Param("skip2") Long skip2,
                            @Param("sameRef1") String sameRef1, @Param("sameRef2") String sameRef2);

    /**
     * 给能管这场比赛的人各写一条，跳过操作者本人。能管 = 这场比赛上单独授了 WRITE（resource_permission），
     * 或者全站管理员（ADMIN / SUPER_ADMIN 角色、CONTEST_MANAGE 能力），与 PermissionService 的判定一致。
     */
    @Insert("INSERT INTO user_notification (user_id, type, contest_id, ref_id, created_at) "
            + "SELECT m.user_id, #{type}, #{contestId}, #{refId}, #{now} FROM ("
            + "SELECT p.user_id FROM resource_permission p WHERE p.resource_type = 'CONTEST' AND p.resource_id = #{contestId} "
            + "AND p.permission = 'WRITE' AND p.is_del = 0 "
            + "UNION SELECT urr.user_id FROM user_role_rel urr JOIN user_role r ON r.id = urr.role_id "
            + "WHERE urr.is_del = 0 AND r.is_del = 0 AND r.role_code IN ('ADMIN', 'SUPER_ADMIN') "
            + "UNION SELECT uc.user_id FROM user_capability uc WHERE uc.capability = 'CONTEST_MANAGE'"
            + ") m JOIN user_info u ON u.id = m.user_id AND u.is_del = 0 WHERE m.user_id <> #{skip}")
    int fanOutToContestManagers(@Param("contestId") Long contestId, @Param("type") String type,
                                @Param("refId") Long refId, @Param("now") LocalDateTime now, @Param("skip") Long skip);

    /** 把一条通知标成弹过；返回 1 表示这次是它第一次被弹（别的标签页没抢先） */
    @Update("UPDATE user_notification SET popped_at = #{now} WHERE id = #{id} AND user_id = #{userId} AND popped_at IS NULL")
    int claim(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);
}
