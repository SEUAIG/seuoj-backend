package com.seuoj.seuojbackend.client;

import com.seuoj.seuojbackend.client.dto.JudgeProblemDataResponse;
import com.seuoj.seuojbackend.client.dto.JudgeProblemEditRequest;
import com.seuoj.seuojbackend.client.dto.JudgeSubmissionRequest;
import com.seuoj.seuojbackend.client.dto.ProblemContentDTO;


/**
 * 评测服务请求抽象类
 * 目前预留，可以有更多实现方式
 */
public interface JudgeClient {

    /**
     * 从评测端获取题目详细信息
     *
     * @param pid problem id
     * @return problem content
     */
    ProblemContentDTO fetchProblemContent(String pid);

    /**
     * 向评测端提交代码
     *
     * @param request submission payload
     */
    void submit(JudgeSubmissionRequest request);

    /**
     * 更新题面数据
     *
     * @param request problem edit payload
     */
    void updateProblem(JudgeProblemEditRequest request);


    /**
     * 获取题目测试点元信息
     *
     * @param pid problem id
     * @return 测试点元信息
     */
    JudgeProblemDataResponse fetchProblemDataMeta(String pid);
}
