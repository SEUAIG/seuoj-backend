package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.UserRoleRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户角色关联表 Mapper 接口
 *
 * @author YourName
 * @since 2025-12-13
 */
@Mapper
public interface UserRoleRelMapper extends BaseMapper<UserRoleRel> {
    /**
     * 根据用户UUID获取用户角色信息列表
     */
    List<String> getRoleCodesByUserId(@Param("userId") Long userId);
}
