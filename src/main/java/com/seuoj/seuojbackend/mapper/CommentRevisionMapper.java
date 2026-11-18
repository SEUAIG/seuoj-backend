package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.CommentRevision;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommentRevisionMapper extends BaseMapper<CommentRevision> {

    /** 某人从 since 起改了几次发言（限流用；只有作者能改，所以按发言作者算） */
    @Select("SELECT COUNT(*) FROM comment_revision r JOIN comment c ON c.id = r.comment_id "
            + "WHERE c.author_user_id = #{userId} AND r.created_at > #{since}")
    long countEditsSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
