package com.seuoj.seuojbackend.client;

import com.seuoj.seuojbackend.client.dto.JudgeProblemDataRequest;
import com.seuoj.seuojbackend.client.dto.JudgeProblemDataResponse;
import com.seuoj.seuojbackend.client.dto.JudgeProblemEditRequest;
import com.seuoj.seuojbackend.client.dto.JudgeSubmissionRequest;
import com.seuoj.seuojbackend.client.dto.ProblemContentDTO;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

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
     * 上传题目数据
     *
     * @param request 题目数据请求
     */
    void uploadProblemData(JudgeProblemDataRequest request);

    /**
     * 获取题目测试点元信息
     *
     * @param pid problem id
     * @return 测试点元信息
     */
    List<JudgeProblemDataResponse.TestcaseMeta> fetchProblemDataMeta(String pid);

    /**
     * 透传题目文件
     *
     * @param pid 题目编号
     * @param fileName 文件名
     * @param response 响应流
     */
    void proxyProblemFile(String pid, String fileName, HttpServletResponse response);
}
