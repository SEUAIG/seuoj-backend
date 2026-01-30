package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.mapper.TagMapper;
import com.seuoj.seuojbackend.vo.tag.TagGroupTagRow;
import com.seuoj.seuojbackend.vo.tag.TagItemVO;
import com.seuoj.seuojbackend.vo.tag.TagListVO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TagService {

    private static final Set<String> SUPPORTED_TYPES = Set.of("algorithm", "source", "time", "special");

    private final TagMapper tagMapper;

    public TagService(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    /**
     * 获取全部题目标签
     *
     * @return 标签列表
     */
    public TagListVO listTags() {
        List<TagGroupTagRow> rows = tagMapper.listTagWithGroup();

        Map<String, LinkedHashMap<Long, TagListVO.TagGroup>> grouped = new LinkedHashMap<>();
        grouped.put("algorithm", new LinkedHashMap<>());
        grouped.put("source", new LinkedHashMap<>());
        grouped.put("time", new LinkedHashMap<>());
        grouped.put("special", new LinkedHashMap<>());

        for (TagGroupTagRow row : rows) {
            if (row == null || row.getGroupId() == null || !SUPPORTED_TYPES.contains(row.getGroupType())) {
                if (row != null && row.getGroupType() != null) {
                    log.warn("获取标签列表时发现未知分组类型, groupId={}, type={}", row.getGroupId(), row.getGroupType());
                }
                continue;
            }
            Map<Long, TagListVO.TagGroup> groupMap = grouped.get(row.getGroupType());
            TagListVO.TagGroup groupVO = groupMap.get(row.getGroupId());
            if (groupVO == null) {
                groupVO = new TagListVO.TagGroup();
                groupVO.setGroupName(row.getGroupName());
                groupVO.setTags(new ArrayList<>());
                groupMap.put(row.getGroupId(), groupVO);
            }
            if (row.getTagId() != null) {
                TagItemVO item = new TagItemVO();
                item.setTagId(row.getTagId());
                item.setTagName(row.getTagName());
                groupVO.getTags().add(item);
            }
        }

        TagListVO vo = new TagListVO();
        vo.setAlgorithm(new ArrayList<>(grouped.get("algorithm").values()));
        vo.setSource(new ArrayList<>(grouped.get("source").values()));
        vo.setTime(new ArrayList<>(grouped.get("time").values()));
        vo.setSpecial(new ArrayList<>(grouped.get("special").values()));
        return vo;
    }
}
