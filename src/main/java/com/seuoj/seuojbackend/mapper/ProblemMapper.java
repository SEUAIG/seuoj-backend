package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;
import com.seuoj.seuojbackend.vo.problem.ProblemListItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 题目表 Mapper 接口
 */
@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {
    /**
     * 原子增加题目总提交数
     */
    void atomicallyIncreaseTotalSubmissionCount(@Param("problemId") Long problemId);

    /**
     * 原子增加题目总通过数
     */
    void atomicallyIncreaseTotalAcceptCount(@Param("problemId") Long problemId);

    /**
     * 获取题目详情（不含标签，标签单独查询）
     */
    ProblemDetailVO getProblemDetail(@Param("pid") String pid);

    /**
     * 获取题目标签列表
     */
    List<String> getProblemTags(@Param("pid") String pid);

    /**
     * 分页查询题目列表（支持标题模糊搜索和标签筛选）
     */
    IPage<ProblemListItemVO> selectProblemPage(
            Page<ProblemListItemVO> page,
            @Param("title") String title,
            @Param("tagIds") List<Long> tagIds,
            @Param("tagIdsSize") int tagIdsSize);

    /**
     * 批量获取题目的标签
     */
    List<ProblemTagResult> getProblemTagsBatch(@Param("pids") List<String> pids);

    /**
     * 题目标签关联结果
     */
    class ProblemTagResult {
        private String pid;
        private String tagName;

        public String getPid() {
            return pid;
        }

        public void setPid(String pid) {
            this.pid = pid;
        }

        public String getTagName() {
            return tagName;
        }

        public void setTagName(String tagName) {
            this.tagName = tagName;
        }
    }
}