package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seuoj.seuojbackend.dto.tag.TagCreateDTO;
import com.seuoj.seuojbackend.dto.tag.TagUpdateDTO;
import com.seuoj.seuojbackend.entity.Tag;
import com.seuoj.seuojbackend.entity.TagGroup;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ConflictException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.TagGroupMapper;
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
    private final TagGroupMapper tagGroupMapper;

    public TagService(TagMapper tagMapper, TagGroupMapper tagGroupMapper) {
        this.tagMapper = tagMapper;
        this.tagGroupMapper = tagGroupMapper;
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

    public void createTag(TagCreateDTO dto) {
        TagGroup group = resolveCategoryGroup(dto.getCategoryName());
        String tagName = normalizeTagName(dto.getTagName());
        ensureTagNotExists(tagName, group.getId(), null);

        Tag tag = new Tag();
        tag.setTagName(tagName);
        tag.setGroupId(group.getId());
        tagMapper.insert(tag);
    }

    public void updateTag(TagUpdateDTO dto) {
        Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getId, dto.getTagId()));
        if (tag == null) {
            throw new NotFoundException("标签不存在");
        }

        String tagName = dto.getTagName() == null ? tag.getTagName() : normalizeTagName(dto.getTagName());
        Long groupId = tag.getGroupId();
        if (dto.getCategoryName() != null) {
            groupId = resolveCategoryGroup(dto.getCategoryName()).getId();
        }

        ensureTagNotExists(tagName, groupId, tag.getId());
        tag.setTagName(tagName);
        tag.setGroupId(groupId);
        tagMapper.updateById(tag);
    }

    private void ensureTagNotExists(String tagName, Long groupId, Long excludeTagId) {
        Tag existing = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getTagName, tagName)
                .ne(excludeTagId != null, Tag::getId, excludeTagId));
        if (existing != null) {
            throw new ConflictException("标签已存在");
        }
    }

    private TagGroup resolveCategoryGroup(String categoryName) {
        String normalized = normalizeCategory(categoryName);

        List<TagGroup> groupsByName = tagGroupMapper.selectList(new LambdaQueryWrapper<TagGroup>()
                .eq(TagGroup::getName, normalized));
        if (groupsByName.size() == 1) {
            return groupsByName.getFirst();
        }
        if (groupsByName.size() > 1) {
            throw new ConflictException("分类名称存在重复，无法确定唯一分组");
        }

        TagGroupType type = TagGroupType.fromCode(normalized).orElse(null);
        if (type == null) {
            throw new NotFoundException("分类不存在");
        }

        TagGroup existing = tagGroupMapper.selectOne(new LambdaQueryWrapper<TagGroup>()
                .eq(TagGroup::getType, type.getCode())
                .isNull(TagGroup::getName));
        if (existing != null) {
            return existing;
        }

        TagGroup group = new TagGroup();
        group.setType(type.getCode());
        group.setName(null);
        tagGroupMapper.insert(group);
        return group;
    }

    private String normalizeCategory(String categoryName) {
        if (categoryName == null) {
            throw new BadRequestException("category_name cannot be blank");
        }
        String normalized = categoryName.trim();
        if (normalized.isEmpty()) {
            throw new BadRequestException("category_name cannot be blank");
        }
        return normalized;
    }

    private String normalizeTagName(String tagName) {
        if (tagName == null) {
            throw new BadRequestException("tag_name cannot be blank");
        }
        String normalized = tagName.trim();
        if (normalized.isEmpty()) {
            throw new BadRequestException("tag_name cannot be blank");
        }
        return normalized;
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
            String normalized = code.trim().toLowerCase();
            for (TagGroupType type : values()) {
                if (type.code.equals(normalized)) {
                    return Optional.of(type);
                }
            }
            return Optional.empty();
        }
    }
}
