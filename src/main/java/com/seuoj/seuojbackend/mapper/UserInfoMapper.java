package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.vo.common.UserPageItemVO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {

    @org.apache.ibatis.annotations.Select("SELECT id FROM user_role WHERE role_code='STUDENT' AND is_del=0 FOR UPDATE")
    Long lockAccountCreation();

    IPage<UserPageItemVO> selectUserPageBasic(Page<?> page,
                                              @Param("username") String username,
                                              @Param("email") String email,
                                              @Param("roleCodes") List<String> roleCodes,
                                              @Param("banned") Boolean banned,
                                              @Param("now") LocalDateTime now);
}