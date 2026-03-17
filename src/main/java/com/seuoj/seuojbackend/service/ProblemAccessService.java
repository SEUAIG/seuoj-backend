package com.seuoj.seuojbackend.service;

import com.seuoj.seuojbackend.common.ProblemSourceType;
import com.seuoj.seuojbackend.entity.Problem;
import com.seuoj.seuojbackend.exception.ForbiddenException;
import com.seuoj.seuojbackend.exception.NotFoundException;
import com.seuoj.seuojbackend.interceptor.AuthContexts;
import com.seuoj.seuojbackend.interceptor.UserContext;
import com.seuoj.seuojbackend.mapper.ProblemAccessMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * 题目访问控制服务。
 *
 * <p>该服务负责根据访问场景统一判断当前用户是否可以查看题目详情。
 * 当前支持三种场景：
 * <ul>
 *     <li>DIRECT：直接从题库查看题目</li>
 *     <li>CONTEST：从比赛上下文查看题目</li>
 *     <li>PROBLEM_SET：从题单上下文查看题目</li>
 * </ul>
 *
 * <p>这里将“题目存在性查询”和“访问权限判断”拆开处理：
 * 题目、比赛、题单等基础数据先由 service / mapper 查询，
 * 本类只负责访问规则本身，避免权限条件散落在 controller 或底层 SQL 中。
 */
@Service
public class ProblemAccessService {

    private final ProblemAccessMapper problemAccessMapper;
    private final UserRoleService userRoleService;

    public ProblemAccessService(ProblemAccessMapper problemAccessMapper, UserRoleService userRoleService) {
        this.problemAccessMapper = problemAccessMapper;
        this.userRoleService = userRoleService;
    }

    /**
     * 根据访问场景校验当前用户是否可以查看题目。
     *
     * @param problem 题目基础信息，要求调用方已确认该题目本身未被软删除
     * @param sourceType 访问场景类型
     * @param ownerPublicId 上下文资源的 public_id。
     *                      当 sourceType 为 DIRECT 时为 null；
     *                      当 sourceType 为 CONTEST / PROBLEM_SET 时，
     *                      分别表示比赛或题单的 public_id
     * @throws NotFoundException 当资源不应暴露存在性时抛出
     * @throws ForbiddenException 当用户已登录，但不具备访问权限时抛出
     */
    public void assertCanViewProblem(Problem problem, ProblemSourceType sourceType, String ownerPublicId) {
        switch (sourceType) {
            case DIRECT -> assertCanViewDirect(problem);
            case CONTEST -> assertCanViewInContest(problem, ownerPublicId);
            case PROBLEM_SET -> assertCanViewInProblemSet(problem, ownerPublicId);
            default -> throw new IllegalStateException("Unexpected source type: " + sourceType);
        }
    }

    /**
     * 校验直接从题库访问题目详情的权限。
     *
     * <p>规则如下：
     * <ul>
     *     <li>游客和普通用户只能直接查看公开题</li>
     *     <li>管理员和超级管理员可以直接查看非公开题</li>
     * </ul>
     *
     * <p>普通用户直接访问非公开题时返回 404，
     * 以避免暴露私有题目的存在性。
     */
    private void assertCanViewDirect(Problem problem) {
        Long userId = AuthContexts.userIdOrNull();
        if (!isPublic(problem.getIsPublic()) && !isAdmin(userId)) {
            throw new NotFoundException("题目不存在");
        }
    }

    /**
     * 校验在比赛上下文中查看题目的权限。
     *
     * <p>校验流程：
     * <ol>
     *     <li>先确认题目确实属于该比赛</li>
     *     <li>比赛场景必须登录</li>
     *     <li>若当前用户是比赛管理者或系统管理员，则直接放行</li>
     *     <li>否则根据比赛所处阶段继续判断普通成员权限</li>
     * </ol>
     *
     * <p>当前比赛阶段规则：
     * <ul>
     *     <li>赛前：仅比赛管理者和系统管理员可访问</li>
     *     <li>赛中：比赛管理者、系统管理员、已报名选手可访问</li>
     *     <li>赛后：已报名用户、所属班级成员、公开比赛的其他登录用户可访问</li>
     * </ul>
     */
    private void assertCanViewInContest(Problem problem, String contestPublicId) {
        if (!problemAccessMapper.existsContestProblemRelation(contestPublicId, problem.getId())) {
            throw new NotFoundException("比赛题目不存在");
        }

        UserContext ctx = AuthContexts.requiredUserContext("请先登录");
        Long userId = ctx.getUserId();
        ProblemAccessMapper.ContestAccessRow contest = problemAccessMapper.selectContestAccess(contestPublicId, userId);
        if (contest == null) {
            throw new NotFoundException("比赛不存在");
        }

        if (canManageContest(contest, userId)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = contest.getStartTime();
        LocalDateTime endTime = contest.getEndTime();

        if (startTime != null && now.isBefore(startTime)) {
            throw new ForbiddenException("比赛开始前仅管理员可查看题目");
        }
        if (endTime == null || !now.isAfter(endTime)) {
            if (contest.isRegistered()) {
                return;
            }
            throw new ForbiddenException("比赛进行中仅管理员和参赛选手可查看题目");
        }

        if (contest.isRegistered() || contest.isClassMember() || isPublic(contest.getIsPublic())) {
            return;
        }
        throw new ForbiddenException("无权访问该比赛题目");
    }

    /**
     * 校验在题单上下文中查看题目的权限。
     *
     * <p>校验流程：
     * <ol>
     *     <li>先确认题目确实属于该题单</li>
     *     <li>题单场景必须登录</li>
     *     <li>公开题单允许所有已登录用户查看</li>
     *     <li>私有题单仅允许系统管理员、题单关联用户、所属班级成员、分享用户查看</li>
     * </ol>
     *
     * <p>其中“分享用户”通过 {@code problem_set_invited_member_rel} 关系表判断，
     * “题单关联用户”通过 {@code user_problem_set_rel} 关系表判断。
     */
    private void assertCanViewInProblemSet(Problem problem, String problemSetPublicId) {
        if (!problemAccessMapper.existsProblemSetRelation(problemSetPublicId, problem.getId())) {
            throw new NotFoundException("题单题目不存在");
        }

        UserContext ctx = AuthContexts.requiredUserContext("请先登录");
        Long userId = ctx.getUserId();
        ProblemAccessMapper.ProblemSetAccessRow problemSet =
                problemAccessMapper.selectProblemSetAccess(problemSetPublicId, userId);
        if (problemSet == null) {
            throw new NotFoundException("题单不存在");
        }

        if (isPublic(problemSet.getIsPublic())
                || canManageProblemSet(problemSet, userId)
                || problemSet.isClassMember()
                || problemSet.isSharedUser()) {
            return;
        }
        throw new ForbiddenException("无权访问该题单");
    }

    /**
     * 判断当前用户是否具备“比赛管理者”身份。
     *
     * <p>满足以下任一条件即视为可管理：
     * <ul>
     *     <li>系统管理员 / 超级管理员</li>
     *     <li>存在于 {@code contest_manager_rel} 的比赛管理者关联中</li>
     * </ul>
     */
    private boolean canManageContest(ProblemAccessMapper.ContestAccessRow contest, Long userId) {
        return contest.isManager() || isAdmin(userId);
    }

    /**
     * 判断当前用户是否具备题单管理身份。
     *
     * <p>题单所有权来自 {@code user_problem_set_rel}，因此这里只认系统管理员或题单关联用户。
     */
    private boolean canManageProblemSet(ProblemAccessMapper.ProblemSetAccessRow problemSet, Long userId) {
        return problemSet.isOwner() || isAdmin(userId);
    }

    /**
     * 判断用户是否具备系统管理员权限。
     */
    private boolean isAdmin(Long userId) {
        return userRoleService.isAdmin(userId);
    }

    /**
     * 将公开标记统一解释为布尔语义。
     */
    private boolean isPublic(Boolean isPublic) {
        return Boolean.TRUE.equals(isPublic);
    }

}
