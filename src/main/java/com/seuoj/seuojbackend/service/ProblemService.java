package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.client.JudgeClient;
import com.seuoj.seuojbackend.client.dto.ProblemContentDTO;
import com.seuoj.seuojbackend.dto.problem.ProblemPageDTO;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.vo.problem.ProblemDetailVO;
import com.seuoj.seuojbackend.vo.problem.ProblemListItemVO;
import com.seuoj.seuojbackend.vo.problem.ProblemPageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProblemService {

    private final ProblemMapper problemMapper;
    private final JudgeClient judgeClient;

    public ProblemService(ProblemMapper problemMapper, JudgeClient judgeClient) {
        this.problemMapper = problemMapper;
        this.judgeClient = judgeClient;
    }

    /**
     * 分页查询题目列表
     *
     * @param dto 分页查询参数
     * @return 分页结果
     */
    public ProblemPageVO getProblemPage(ProblemPageDTO dto) {
        List<Long> tagIds = dto.getTagIds();
        if (tagIds != null && !tagIds.isEmpty()) {
            tagIds = tagIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        }
        int tagIdsSize = (tagIds == null) ? 0 : tagIds.size();

        SearchSpec searchSpec = SearchSpec.from(dto.getTitle());

        Page<ProblemListItemVO> page = new Page<>(dto.getCurrent(), dto.getSize());
        IPage<ProblemListItemVO> resultPage = problemMapper.selectProblemPage(
                page,
                searchSpec.fulltextQuery,
                searchSpec.titleLike,
                searchSpec.useFulltext,
                searchSpec.singleTokens,
                searchSpec.useLikeSingle,
                searchSpec.useLikeRaw,
                tagIds,
                tagIdsSize
        );

        // 批量获取标签
        List<ProblemListItemVO> records = resultPage.getRecords();
        if (records != null && !records.isEmpty()) {
            List<String> pids = records.stream()
                    .map(ProblemListItemVO::getPid)
                    .collect(Collectors.toList());

            // 批量查询标签
            List<ProblemMapper.ProblemTagResult> tagResults = problemMapper.getProblemTagsBatch(pids);

            // 按 pid 分组
            Map<String, List<String>> pidTagsMap = new HashMap<>();
            for (ProblemMapper.ProblemTagResult tagResult : tagResults) {
                pidTagsMap.computeIfAbsent(tagResult.getPid(), k -> new ArrayList<>())
                        .add(tagResult.getTagName());
            }

            // 设置标签
            for (ProblemListItemVO item : records) {
                item.setTags(pidTagsMap.getOrDefault(item.getPid(), Collections.emptyList()));
            }
        }

        // 构建返回结果
        ProblemPageVO vo = new ProblemPageVO();
        vo.setCurrent(dto.getCurrent());
        vo.setSize(dto.getSize());
        vo.setTotal(resultPage.getTotal());
        vo.setRecords(records != null ? records : Collections.emptyList());

        return vo;
    }

    private static String buildFulltextQuery(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(token).append('*');
        }
        return builder.toString();
    }

    private static final class SearchSpec {
        private final String fulltextQuery;
        private final String titleLike;
        private final List<String> singleTokens;
        private final boolean useFulltext;
        private final boolean useLikeSingle;
        private final boolean useLikeRaw;

        private SearchSpec(String fulltextQuery, String titleLike, List<String> singleTokens,
                           boolean useFulltext, boolean useLikeSingle, boolean useLikeRaw) {
            this.fulltextQuery = fulltextQuery;
            this.titleLike = titleLike;
            this.singleTokens = singleTokens;
            this.useFulltext = useFulltext;
            this.useLikeSingle = useLikeSingle;
            this.useLikeRaw = useLikeRaw;
        }

        private static SearchSpec from(String title) {
            List<String> tokens = tokenizeForFulltext(title);
            List<String> singleTokens = tokens.stream()
                    .filter(token -> token.length() == 1)
                    .collect(Collectors.toList());
            List<String> multiTokens = tokens.stream()
                    .filter(token -> token.length() >= 2)
                    .collect(Collectors.toList());

            String fulltextQuery = buildFulltextQuery(multiTokens);
            boolean useFulltext = !fulltextQuery.isEmpty();
            boolean useLikeSingle = !singleTokens.isEmpty();
            boolean useLikeRaw = !useFulltext && !useLikeSingle && title != null && !title.trim().isEmpty();

            return new SearchSpec(fulltextQuery, title, singleTokens, useFulltext, useLikeSingle, useLikeRaw);
        }
    }

    private static List<String> tokenizeForFulltext(String input) {
        if (input == null) {
            return Collections.emptyList();
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int mode = 0; // 0 none, 1 cjk, 2 alnum

        int index = 0;
        while (index < trimmed.length()) {
            int codePoint = trimmed.codePointAt(index);
            int charCount = Character.charCount(codePoint);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                if (mode == 2) {
                    addAlnumToken(tokens, buffer);
                    buffer.setLength(0);
                }
                mode = 1;
                buffer.appendCodePoint(codePoint);
            } else if (Character.isLetterOrDigit(codePoint)) {
                if (mode == 1) {
                    addCjkBigrams(tokens, buffer);
                    buffer.setLength(0);
                }
                mode = 2;
                buffer.appendCodePoint(codePoint);
            } else {
                if (mode == 1) {
                    addCjkBigrams(tokens, buffer);
                } else if (mode == 2) {
                    addAlnumToken(tokens, buffer);
                }
                buffer.setLength(0);
                mode = 0;
            }
            index += charCount;
        }

        if (mode == 1) {
            addCjkBigrams(tokens, buffer);
        } else if (mode == 2) {
            addAlnumToken(tokens, buffer);
        }

        return tokens.stream().distinct().collect(Collectors.toList());
    }

    private static void addAlnumToken(List<String> tokens, StringBuilder buffer) {
        if (!buffer.isEmpty()) {
            tokens.add(buffer.toString());
        }
    }

    private static void addCjkBigrams(List<String> tokens, StringBuilder buffer) {
        if (buffer.isEmpty()) {
            return;
        }
        int[] codePoints = buffer.codePoints().toArray();
        if (codePoints.length == 1) {
            tokens.add(new String(codePoints, 0, 1));
            return;
        }
        for (int i = 0; i < codePoints.length - 1; i++) {
            tokens.add(new String(codePoints, i, 2));
        }
    }

    /**
     * 根据 pid 获取题目详情
     *
     * @param pid 题目编号
     * @return 题目详情 VO
     */
    public ProblemDetailVO getProblemDetail(String pid) {
        ProblemDetailVO problemDetail = problemMapper.getProblemDetail(pid);
        if (problemDetail == null) {
            log.warn("获取题目详情时发现题目 {} 不存在", pid);
            throw new NotFoundException("题目不存在");
        }

        List<String> tags = problemMapper.getProblemTags(pid);
        problemDetail.setTags(tags != null ? tags : Collections.emptyList());

        ProblemContentDTO problemContentDTO = judgeClient.fetchProblemContent(pid);
        if (problemContentDTO == null) {
            log.warn("题目 {} 的内容在判题服务中缺失", pid);
            throw new NotFoundException("题目不存在");
        }

        problemDetail.setContent(problemContentDTO);
        return problemDetail;
    }
}
