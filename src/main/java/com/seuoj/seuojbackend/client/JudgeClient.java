package com.seuoj.seuojbackend.client;

import com.fasterxml.jackson.databind.JsonNode;
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
     * 上传题目测试数据压缩包
     *
     * @param pid      题目pid
     * @param zipBytes zip 压缩包内容，解压后即为评测端 data 目录
     */
    void uploadProblemData(String pid, byte[] zipBytes);

    /**
     * 更新题目配置
     *
     * @param pid    题目pid
     * @param config 评测端配置 JSON，原样透传
     */
    void updateProblemConfig(String pid, JsonNode config);

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

    /**
     * 判题并发上限、当前负载和计时探针样本
     */
    JudgeConcurrencyStatus fetchConcurrency();

    /**
     * 不重启判题端修改并发上限
     *
     * @param limit 新上限
     * @return 修改后的状态（不含探针样本）
     */
    JudgeConcurrencyStatus updateConcurrency(int limit);
}
