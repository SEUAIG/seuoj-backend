package com.seuoj.seuojbackend.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserCapabilityMapper {
    @Select("SELECT capability FROM user_capability WHERE user_id=#{userId}")
    List<String> findByUserId(Long userId);
    @Delete("DELETE FROM user_capability WHERE user_id=#{userId}")
    void deleteByUserId(Long userId);
    @Insert("INSERT INTO user_capability(user_id,capability) VALUES(#{userId},#{capability})")
    void grant(@Param("userId") Long userId, @Param("capability") String capability);
}
