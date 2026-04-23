package com.seuoj.seuojbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.seuoj.seuojbackend.entity.Announcement;
import com.seuoj.seuojbackend.vo.announcement.AnnouncementVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AnnouncementMapper extends BaseMapper<Announcement> {

    IPage<AnnouncementVO> selectAnnouncementPage(IPage<?> page,
                                                  @Param("targetType") String targetType,
                                                  @Param("targetId") Long targetId);
}
