package com.seuoj.seuojbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seuoj.seuojbackend.dto.problemset.ProblemSetCreateDTO;
import com.seuoj.seuojbackend.dto.problemset.ProblemSetProblemEditDTO;
import com.seuoj.seuojbackend.dto.problemset.ProblemSetUpdateDTO;
import com.seuoj.seuojbackend.entity.ClassProblemSetRel;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.entity.ProblemSet;
import com.seuoj.seuojbackend.entity.ProblemSetInvitedMemberRel;
import com.seuoj.seuojbackend.entity.ProblemSetProblemRel;
import com.seuoj.seuojbackend.exception.AuthorizationException;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.mapper.ClassProblemSetRelMapper;
import com.seuoj.seuojbackend.mapper.ProblemAccessMapper;
import com.seuoj.seuojbackend.mapper.ProblemMapper;
import com.seuoj.seuojbackend.mapper.ProblemSetInvitedMemberRelMapper;
import com.seuoj.seuojbackend.mapper.ProblemSetMapper;
import com.seuoj.seuojbackend.mapper.ProblemSetProblemRelMapper;
import com.seuoj.seuojbackend.vo.problemset.ProblemSetCreateVO;
import com.seuoj.seuojbackend.vo.problemset.ProblemSetDetailVO;
import com.seuoj.seuojbackend.vo.problemset.ProblemSetItemVO;
import com.seuoj.seuojbackend.vo.problemset.ProblemSetPageVO;
import com.seuoj.seuojbackend.vo.problemset.ProblemSetProblemItemVO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 题单业务服务
 */
@Slf4j
@Service
public class ProblemSetService {

    private final ProblemSetMapper problemSetMapper;
    private final ProblemSetProblemRelMapper problemSetProblemRelMapper;
    private final ClassProblemSetRelMapper classProblemSetRelMapper;
    private final ProblemSetInvitedMemberRelMapper problemSetInvitedMemberRelMapper;
    private final ProblemMapper problemMapper;
    private final ProblemAccessMapper problemAccessMapper;
    private final UserRoleService userRoleService;

    public ProblemSetService(ProblemSetMapper problemSetMapper,
                             ProblemSetProblemRelMapper problemSetProblemRelMapper,
                             ClassProblemSetRelMapper classProblemSetRelMapper,
                             ProblemSetInvitedMemberRelMapper problemSetInvitedMemberRelMapper,
                             ProblemMapper problemMapper,
                             ProblemAccessMapper problemAccessMapper,
                             UserRoleService userRoleService) {
        this.problemSetMapper = problemSetMapper;
        this.problemSetProblemRelMapper = problemSetProblemRelMapper;
        this.classProblemSetRelMapper = classProblemSetRelMapper;
        this.problemSetInvitedMemberRelMapper = problemSetInvitedMemberRelMapper;
        this.problemMapper = problemMapper;
        this.problemAccessMapper = problemAccessMapper;
        this.userRoleService = userRoleService;
    }

    /**
     * 创建题单
     */
    @Transactional(rollbackFor = Exception.class)
    public ProblemSetCreateVO createProblemSet(ProblemSetCreateDTO dto) {
        Long userId = AuthContexts.requiredUserId();
        String title = normalizeRequiredText(dto.getTitle(), "title 不能为空");

        ProblemSet problemSet = new ProblemSet();
        problemSet.setPublicId(UUID.randomUUID().toString());
        problemSet.setTitle(title);
        problemSet.setDescription(dto.getDescription());
        problemSet.setIsPublic(Boolean.TRUE.equals(dto.getIsPublic()));
        problemSet.setOwnerUserId(userId);
        problemSetMapper.insert(problemSet);

        ProblemSetCreateVO vo = new ProblemSetCreateVO();
        vo.setProblemSetPublicId(problemSet.getPublicId());
        return vo;
    }

    /**
     * 题单分页
     */
    public ProblemSetPageVO getProblemSetPage(Integer current, Integer size) {
        if (current == null || current < 1) {
            throw new BadRequestException("页码必须大于等于 1");
        }
        if (size == null || size < 1 || size > 100) {
            throw new BadRequestException("每页条数必须在 1 到 100 之间");
        }

        Long userId = AuthContexts.userIdOrNull();
        boolean isAdmin = userId != null && userRoleService.isAdmin(userId);

        Page<ProblemSetItemVO> page = new Page<>(current, size);
        IPage<ProblemSetItemVO> pageResult = problemSetMapper.selectProblemSetPage(page, userId, isAdmin);

        ProblemSetPageVO vo = new ProblemSetPageVO();
        vo.setCurrent(pageResult.getCurrent());
        vo.setSize(pageResult.getSize());
        vo.setTotal(pageResult.getTotal());
        vo.setRecords(pageResult.getRecords() == null ? Collections.emptyList() : pageResult.getRecords());
        return vo;
    }

    /**
     * 题单详情
     */
    public ProblemSetDetailVO getProblemSetDetail(String problemSetPublicId) {
        ProblemSet problemSet = getProblemSetByPublicId(problemSetPublicId);
        assertCanViewProblemSet(problemSet);

        List<ProblemSetProblemItemVO> problemList = problemSetMapper.selectProblemSetProblems(problemSet.getId());

        ProblemSetDetailVO vo = new ProblemSetDetailVO();
        vo.setProblemSetId(problemSet.getPublicId());
        vo.setTitle(problemSet.getTitle());
        vo.setDescription(problemSet.getDescription());
        vo.setIsPublic(problemSet.getIsPublic());
        vo.setProblemList(problemList == null ? Collections.emptyList() : problemList);
        return vo;
    }

    /**
     * 更新题单基础信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateProblemSet(String problemSetPublicId, ProblemSetUpdateDTO dto) {
        ProblemSet problemSet = getProblemSetByPublicId(problemSetPublicId);
        assertCanManageProblemSet(problemSet);

        ProblemSet update = new ProblemSet();
        update.setId(problemSet.getId());

        boolean changed = false;
        if (dto.getTitle() != null) {
            update.setTitle(normalizeRequiredText(dto.getTitle(), "title 不能为空"));
            changed = true;
        }
        if (dto.getDescription() != null) {
            update.setDescription(dto.getDescription());
            changed = true;
        }
        if (dto.getIsPublic() != null) {
            update.setIsPublic(dto.getIsPublic());
            changed = true;
        }

        if (changed) {
            problemSetMapper.updateById(update);
        }
    }

    /**
     * 删除题单
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteProblemSet(String problemSetPublicId) {
        ProblemSet problemSet = getProblemSetByPublicId(problemSetPublicId);
        assertCanManageProblemSet(problemSet);

        problemSetMapper.deleteById(problemSet.getId());

        problemSetProblemRelMapper.update(null, new LambdaUpdateWrapper<ProblemSetProblemRel>()
                .set(ProblemSetProblemRel::getIsDel, 1)
                .eq(ProblemSetProblemRel::getProblemSetId, problemSet.getId()));

        classProblemSetRelMapper.update(null, new LambdaUpdateWrapper<ClassProblemSetRel>()
                .set(ClassProblemSetRel::getIsDel, 1)
                .eq(ClassProblemSetRel::getProblemSetId, problemSet.getId()));

        problemSetInvitedMemberRelMapper.update(null, new LambdaUpdateWrapper<ProblemSetInvitedMemberRel>()
                .set(ProblemSetInvitedMemberRel::getIsDel, 1)
                .eq(ProblemSetInvitedMemberRel::getProblemSetId, problemSet.getId()));
    }

    /**
     * 全量覆盖题单题目列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceProblemSetProblems(String problemSetPublicId, ProblemSetProblemEditDTO dto) {
        ProblemSet problemSet = getProblemSetByPublicId(problemSetPublicId);
        assertCanManageProblemSet(problemSet);

        List<ProblemPlanItem> planItems = buildProblemPlan(dto.getProblemList());
        Map<String, Problem> problemByPid = loadProblemMapByPid(planItems);

        Map<Long, Integer> desiredOrderByProblemId = new HashMap<>();
        for (ProblemPlanItem item : planItems) {
            Problem problem = problemByPid.get(item.pid());
            desiredOrderByProblemId.put(problem.getId(), item.sortOrder());
        }

        List<ProblemSetProblemRel> existingRels = problemSetProblemRelMapper.selectByProblemSetId(problemSet.getId());
        Map<Long, ProblemSetProblemRel> activeByProblemId = new HashMap<>();
        Map<Long, ProblemSetProblemRel> deletedByProblemId = new HashMap<>();
        for (ProblemSetProblemRel rel : existingRels) {
            if (rel == null || rel.getProblemId() == null) {
                continue;
            }
            Long problemId = rel.getProblemId();
            if (Integer.valueOf(0).equals(rel.getIsDel())) {
                activeByProblemId.putIfAbsent(problemId, rel);
            } else if (!activeByProblemId.containsKey(problemId)) {
                deletedByProblemId.putIfAbsent(problemId, rel);
            }
        }

        Set<Long> desiredProblemIds = desiredOrderByProblemId.keySet();
        Set<Long> toSoftDelete = new LinkedHashSet<>();
        for (Long activeProblemId : activeByProblemId.keySet()) {
            if (!desiredProblemIds.contains(activeProblemId)) {
                toSoftDelete.add(activeProblemId);
            }
        }
        Set<Long> toKeep = new LinkedHashSet<>();
        Set<Long> toRestore = new LinkedHashSet<>();
        Set<Long> toInsert = new LinkedHashSet<>();
        for (Long desiredProblemId : desiredProblemIds) {
            if (activeByProblemId.containsKey(desiredProblemId)) {
                toKeep.add(desiredProblemId);
            } else if (deletedByProblemId.containsKey(desiredProblemId)) {
                toRestore.add(desiredProblemId);
            } else {
                toInsert.add(desiredProblemId);
            }
        }

        if (!toSoftDelete.isEmpty()) {
            List<Long> softDeleteIds = toSoftDelete.stream()
                    .map(activeByProblemId::get)
                    .filter(rel -> rel != null && rel.getId() != null)
                    .map(ProblemSetProblemRel::getId)
                    .toList();
            if (!softDeleteIds.isEmpty()) {
                problemSetProblemRelMapper.markDeletedByIds(problemSet.getId(), softDeleteIds);
            }
        }

        List<ProblemSetProblemRel> keepNeedReorder = new ArrayList<>();
        for (Long problemId : toKeep) {
            ProblemSetProblemRel rel = activeByProblemId.get(problemId);
            Integer desiredOrder = desiredOrderByProblemId.get(problemId);
            if (rel == null || rel.getId() == null || desiredOrder == null) {
                continue;
            }
            if (!desiredOrder.equals(rel.getSortOrder())) {
                keepNeedReorder.add(new ProblemSetProblemRel()
                        .setId(rel.getId())
                        .setSortOrder(desiredOrder));
            }
        }

        if (!keepNeedReorder.isEmpty()) {
            int temporarySortBase = calculateTemporarySortBase(existingRels, desiredOrderByProblemId.values(), keepNeedReorder.size());
            List<ProblemSetProblemRel> temporaryUpdates = new ArrayList<>(keepNeedReorder.size());
            int offset = 0;
            for (ProblemSetProblemRel rel : keepNeedReorder) {
                temporaryUpdates.add(new ProblemSetProblemRel()
                        .setId(rel.getId())
                        .setSortOrder(temporarySortBase + offset));
                offset++;
            }
            problemSetProblemRelMapper.updateSortOrdersByIds(problemSet.getId(), temporaryUpdates);
        }

        if (!toRestore.isEmpty()) {
            List<ProblemSetProblemRel> restoreUpdates = new ArrayList<>(toRestore.size());
            for (Long problemId : toRestore) {
                ProblemSetProblemRel rel = deletedByProblemId.get(problemId);
                Integer desiredOrder = desiredOrderByProblemId.get(problemId);
                if (rel == null || rel.getId() == null || desiredOrder == null) {
                    continue;
                }
                restoreUpdates.add(new ProblemSetProblemRel()
                        .setId(rel.getId())
                        .setSortOrder(desiredOrder));
            }
            if (!restoreUpdates.isEmpty()) {
                problemSetProblemRelMapper.restoreByIdsWithSortOrders(problemSet.getId(), restoreUpdates);
            }
        }

        if (!toInsert.isEmpty()) {
            List<ProblemSetProblemRel> insertRels = new ArrayList<>(toInsert.size());
            for (Long problemId : toInsert) {
                Integer desiredOrder = desiredOrderByProblemId.get(problemId);
                if (desiredOrder == null) {
                    continue;
                }
                insertRels.add(new ProblemSetProblemRel()
                        .setProblemSetId(problemSet.getId())
                        .setProblemId(problemId)
                        .setSortOrder(desiredOrder));
            }
            if (!insertRels.isEmpty()) {
                problemSetProblemRelMapper.insertBatch(insertRels);
            }
        }

        if (!keepNeedReorder.isEmpty()) {
            problemSetProblemRelMapper.updateSortOrdersByIds(problemSet.getId(), keepNeedReorder);
        }
    }

    private Map<String, Problem> loadProblemMapByPid(List<ProblemPlanItem> planItems) {
        if (planItems.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> pids = planItems.stream().map(ProblemPlanItem::pid).collect(Collectors.toList());
        List<Problem> problems = problemMapper.selectList(new LambdaQueryWrapper<Problem>()
                .in(Problem::getPid, pids));

        Map<String, Problem> problemByPid = new HashMap<>();
        for (Problem problem : problems) {
            problemByPid.put(problem.getPid(), problem);
        }

        List<String> missingPids = pids.stream()
                .filter(pid -> !problemByPid.containsKey(pid))
                .distinct()
                .toList();
        if (!missingPids.isEmpty()) {
            throw new BadRequestException("以下题目不存在: " + String.join(", ", missingPids));
        }
        return problemByPid;
    }

    private int calculateTemporarySortBase(List<ProblemSetProblemRel> existingRels,
                                           java.util.Collection<Integer> desiredOrders,
                                           int updateCount) {
        int maxOrder = 0;
        for (ProblemSetProblemRel rel : existingRels) {
            if (rel != null && rel.getSortOrder() != null) {
                maxOrder = Math.max(maxOrder, rel.getSortOrder());
            }
        }
        for (Integer desiredOrder : desiredOrders) {
            if (desiredOrder != null) {
                maxOrder = Math.max(maxOrder, desiredOrder);
            }
        }

        long candidate = (long) maxOrder + 1_000_000L;
        int tail = Math.max(8, updateCount + 2);
        long maxAllowed = (long) Integer.MAX_VALUE - tail;
        if (candidate > maxAllowed) {
            candidate = maxAllowed;
        }
        if (candidate <= 0) {
            candidate = 1_000_000_000L;
        }
        return (int) candidate;
    }

    private ProblemSet getProblemSetByPublicId(String problemSetPublicId) {
        if (!StringUtils.hasText(problemSetPublicId)) {
            throw new BadRequestException("problem_set_public_id 不能为空");
        }

        ProblemSet problemSet = problemSetMapper.selectOne(new LambdaQueryWrapper<ProblemSet>()
                .eq(ProblemSet::getPublicId, problemSetPublicId));
        if (problemSet == null) {
            throw new NotFoundException("题单不存在");
        }
        return problemSet;
    }

    /**
     * 校验题单查看权限
     */
    private void assertCanViewProblemSet(ProblemSet problemSet) {
        if (Boolean.TRUE.equals(problemSet.getIsPublic())) {
            return;
        }

        Long userId = AuthContexts.userIdOrNull();
        if (userId == null) {
            throw new AuthorizationException("用户未登录");
        }
        if (userRoleService.isAdmin(userId)) {
            return;
        }

        ProblemAccessMapper.ProblemSetAccessRow accessRow =
                problemAccessMapper.selectProblemSetAccess(problemSet.getPublicId(), userId);
        if (accessRow == null) {
            throw new NotFoundException("题单不存在");
        }
        if (accessRow.isOwner() || accessRow.isClassMember() || accessRow.isSharedUser()) {
            return;
        }
        throw new ForbiddenException("无权访问该题单");
    }

    /**
     * 校验题单管理权限
     */
    private void assertCanManageProblemSet(ProblemSet problemSet) {
        Long userId = AuthContexts.requiredUserId();
        if (userRoleService.isAdmin(userId)) {
            return;
        }
        if (userId.equals(problemSet.getOwnerUserId())) {
            return;
        }

        ProblemAccessMapper.ProblemSetAccessRow accessRow =
                problemAccessMapper.selectProblemSetAccess(problemSet.getPublicId(), userId);
        if (accessRow != null && accessRow.isSharedUser()) {
            return;
        }

        throw new ForbiddenException("无权管理该题单");
    }

    /**
     * 构造题目覆盖计划并做参数校验
     */
    private List<ProblemPlanItem> buildProblemPlan(List<ProblemSetProblemEditDTO.ProblemItemDTO> input) {
        if (input == null) {
            return Collections.emptyList();
        }

        List<ProblemPlanItem> plan = new ArrayList<>(input.size());
        Set<String> pidSet = new LinkedHashSet<>();
        Set<Integer> orderSet = new LinkedHashSet<>();

        for (ProblemSetProblemEditDTO.ProblemItemDTO item : input) {
            if (item == null) {
                throw new BadRequestException("problem_list 中存在空对象");
            }

            String pid = normalizeRequiredText(item.getPid(), "problem_list.pid 不能为空");
            Integer sortOrder = item.getOrderId();

            if (!pidSet.add(pid)) {
                throw new BadRequestException("problem_list 中 pid 重复: " + pid);
            }
            if (!orderSet.add(sortOrder)) {
                throw new BadRequestException("problem_list 中 order_id 重复: " + sortOrder);
            }

            plan.add(new ProblemPlanItem(pid, sortOrder));
        }

        return plan;
    }


    /**
     * 规范化必填字符串
     */
    private String normalizeRequiredText(String raw, String message) {
        if (!StringUtils.hasText(raw)) {
            throw new BadRequestException(message);
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException(message);
        }
        return trimmed;
    }

    /**
     * 题目覆盖执行项
     */
    private record ProblemPlanItem(String pid, Integer sortOrder) {
    }
}











