package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.mapper.TagMapper;
import com.seuoj.seuojbackend.vo.tag.TagGroupTagRow;
import com.seuoj.seuojbackend.vo.tag.TagItemVO;
import com.seuoj.seuojbackend.vo.tag.TagListVO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TagService {

    private static final List<TagGroupType> SUPPORTED_TYPES = List.of(
            TagGroupType.ALGORITHM,
            TagGroupType.SOURCE,
            TagGroupType.TIME,
            TagGroupType.SPECIAL
    );

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
        for (TagGroupType type : SUPPORTED_TYPES) {
            grouped.put(type.getCode(), new LinkedHashMap<>());
        }

        for (TagGroupTagRow row : rows) {
            if (row == null || row.getGroupId() == null) {
                if (row != null && row.getGroupType() != null) {
                    log.warn("获取标签列表时发现未知分组类型, groupId={}, type={}", row.getGroupId(), row.getGroupType());
                }
                continue;
            }
            TagGroupType type = TagGroupType.fromCode(row.getGroupType()).orElse(null);
            if (type == null) {
                if (row.getGroupType() != null) {
                    log.warn("获取标签列表时发现未知分组类型, groupId={}, type={}", row.getGroupId(), row.getGroupType());
                }
                continue;
            }
            Map<Long, TagListVO.TagGroup> groupMap = grouped.get(type.getCode());
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
        vo.setAlgorithm(new ArrayList<>(grouped.get(TagGroupType.ALGORITHM.getCode()).values()));
        vo.setSource(new ArrayList<>(grouped.get(TagGroupType.SOURCE.getCode()).values()));
        vo.setTime(new ArrayList<>(grouped.get(TagGroupType.TIME.getCode()).values()));
        vo.setSpecial(new ArrayList<>(grouped.get(TagGroupType.SPECIAL.getCode()).values()));
        return vo;
    }

    @Getter
    private enum TagGroupType {
        ALGORITHM("algorithm"),
        SOURCE("source"),
        TIME("time"),
        SPECIAL("special");

        private final String code;

        TagGroupType(String code) {
            this.code = code;
        }

        public static Optional<TagGroupType> fromCode(String code) {
            if (code == null || code.isEmpty()) {
                return Optional.empty();
            }
            for (TagGroupType type : values()) {
                if (type.code.equals(code)) {
                    return Optional.of(type);
                }
            }
            return Optional.empty();
        }
    }
}
