package com.seuoj.seuojbackend.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // 填充创建时间和更新时间
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());

        // 生成提交记录业务id
        this.strictInsertFill(metaObject, "submissionNo", String.class, UUID.randomUUID().toString());

        // 兼容其他命名方式
        this.strictInsertFill(metaObject, "created_at", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updated_at", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 填充更新时间
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        
        // 兼容其他命名方式
        this.strictUpdateFill(metaObject, "updated_at", LocalDateTime.class, LocalDateTime.now());
    }
}