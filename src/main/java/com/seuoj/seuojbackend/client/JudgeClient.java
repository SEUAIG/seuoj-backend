package com.seuoj.seuojbackend.client;

import com.seuoj.seuojbackend.client.dto.*;


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
     * 同步在线评测（自定义输入）
     *
     * @param request online judge payload
     * @return 同步评测结果
     */
    JudgeOnlineSubmissionResponseData submitOnline(JudgeOnlineSubmissionRequest request);

    /**
     * 获取评测端可用语言列表
     *
     * @return 语言列表响应
     */
    JudgeLanguagesResponseData fetchLanguages();

    /**
     * 更新题面数据
     *
     * @param request problem edit payload
     */
    void updateProblem(JudgeProblemEditRequest request);

    /**
     * 获取题目配置
     *
     * @param pid 题目pid
     * @return 题目配置dto
     */
    ProblemConfigDTO fetchProblemConfig(String pid);

    /**
     * 删除题目
     */
    void deleteProblem(String pid);

    /**
     * 获取题目测试点元数据（已废弃）
     */
    @Deprecated
    JudgeProblemDataResponse fetchProblemDataMeta(String pid);

    /**
     * 获取题目文件树
     *
     * @param pid problem id
     * @return 文件树（List of FileNode maps）
     */
    Object fetchProblemTree(String pid);

    /**
     * 代理获取题目文件（字节流）
     *
     * @param pid      problem id
     * @param fileName 文件路径
     * @return 文件字节数组
     */
    byte[] fetchProblemFile(String pid, String fileName);
}
