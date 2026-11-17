package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.Comment;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    /** 原子地改净分，避免并发投票互相覆盖 */
    @Update("UPDATE comment SET score = score + #{delta} WHERE id = #{id}")
    int addScore(@Param("id") Long id, @Param("delta") int delta);

    @Update("UPDATE comment SET reply_count = reply_count + #{delta} WHERE id = #{id}")
    int addReplyCount(@Param("id") Long id, @Param("delta") int delta);

    /** 每个目标下可见的发言数（未删除、未隐藏，含回复）；key 为 target_id、cnt */
    @Select({"<script>",
            "SELECT target_id, COUNT(*) AS cnt FROM comment",
            "WHERE target_type = #{targetType} AND is_del = 0 AND deleted_at IS NULL AND hidden_at IS NULL",
            "AND target_id IN <foreach collection='targetIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "GROUP BY target_id",
            "</script>"})
    List<Map<String, Object>> countVisibleByTargets(@Param("targetType") String targetType,
                                                    @Param("targetIds") List<Long> targetIds);
}
