package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.Post;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostMapper extends BaseMapper<Post> {

    /** 原子地改净分，避免并发投票互相覆盖 */
    @Update("UPDATE post SET score = score + #{delta} WHERE id = #{id}")
    int addScore(@Param("id") Long id, @Param("delta") int delta);

    /** 每道题公开可见的用户题解数（已发布、未隐藏）；key 为 problem_id、cnt */
    @Select({"<script>",
            "SELECT problem_id, COUNT(*) AS cnt FROM post",
            "WHERE kind = 'SOLUTION' AND status = 'PUBLISHED' AND is_del = 0 AND hidden_at IS NULL",
            "AND problem_id IN <foreach collection='problemIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "GROUP BY problem_id",
            "</script>"})
    List<Map<String, Object>> countPublicSolutions(@Param("problemIds") List<Long> problemIds);
}
