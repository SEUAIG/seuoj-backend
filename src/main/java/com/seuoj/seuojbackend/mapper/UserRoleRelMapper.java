package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.UserRoleRel;
import com.seuoj.seuojbackend.vo.common.UserRoleRowVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserRoleRelMapper extends BaseMapper<UserRoleRel> {

    List<String> getRoleCodesByUserId(@Param("userId") Long userId);

    List<UserRoleRowVO> selectRoleCodesByUserIds(@Param("userIds") List<Long> userIds,
                                                 @Param("visibleRoleCodes") List<String> visibleRoleCodes);
}