package com.seuoj.seuojbackend.mapper;

import org.apache.ibatis.annotations.*;

/** 全站运行时设置（system_setting 键值表） */
@Mapper
public interface SystemSettingMapper {
    @Select("SELECT setting_value FROM system_setting WHERE setting_key=#{key}")
    String find(@Param("key") String key);

    @Insert("INSERT INTO system_setting(setting_key,setting_value,updated_by) VALUES(#{key},#{value},#{by}) "
            + "ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value), updated_by=VALUES(updated_by)")
    void put(@Param("key") String key, @Param("value") String value, @Param("by") Long by);
}
