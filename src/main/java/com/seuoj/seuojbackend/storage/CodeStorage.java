package com.seuoj.seuojbackend.storage;

/**
 * 用户代码存储接口，定义了代码存储的抽象方法
 */
public interface CodeStorage {

    /**
     * 代码保存
     *
     * @param code 用户代码文本
     * @param submissionNo 提交编号uuid
     */
    void save(String code, String submissionNo);

    /**
     * 代码查看
     *
     * @param submissionNo 提交编号
     * @return 用户代码文本，找不到会返回null
     */
    String getCode(String submissionNo);
}
