package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seuoj.seuojbackend.entity.ResourcePermission;
import com.seuoj.seuojbackend.vo.permission.ResourcePermissionVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ResourcePermissionMapper extends BaseMapper<ResourcePermission> {

    List<ResourcePermissionVO> selectPermissionsWithUserInfo(
            @Param("resourceType") String resourceType,
            @Param("resourceId") Long resourceId);

    boolean hasPermission(@Param("resourceType") String resourceType,
                          @Param("resourceId") Long resourceId,
                          @Param("userId") Long userId,
                          @Param("permission") String permission);

    boolean hasAnyPermission(@Param("resourceType") String resourceType,
                             @Param("resourceId") Long resourceId,
                             @Param("userId") Long userId);

    boolean hasProblemSetAccessViaAssignment(@Param("problemSetId") Long problemSetId,
                                             @Param("userId") Long userId);

    boolean hasContestAccessViaClass(@Param("contestId") Long contestId,
                                     @Param("userId") Long userId);
}
