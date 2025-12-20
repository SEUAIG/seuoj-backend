package com.seuoj.seuojbackend.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // 生成提交记录业务 id
        this.strictInsertFill(metaObject, "submissionNo", String.class, UUID.randomUUID().toString());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 交给数据库的 ON UPDATE CURRENT_TIMESTAMP 自动维护
    }
}
