package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户基础表 Mapper 接口
 *
 * @author YourName
 * @since 2025-12-13
 */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {

}