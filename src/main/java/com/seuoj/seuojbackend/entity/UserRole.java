package com.seuoj.seuojbackend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户角色表实体类
 *
 * @author YourName
 * @since 2025-12-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_role")
public class UserRole implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 角色编码(STUDENT/TEACHER/ADMIN)
     * 和后端代码中的枚举类RoleType保持一致
     */
    //TODO: 是否是合理的设计？后续可以修改，记得改鉴权相关代码
    @TableField("role_code")
    private String roleCode;

    /**
     * 角色名称
     */
    @TableField("role_name")
    private String roleName;

    /**
     * 是否删除，0-未删除，1-已删除
     */
    @TableField("is_del")
    @TableLogic
    private Integer isDel;


}