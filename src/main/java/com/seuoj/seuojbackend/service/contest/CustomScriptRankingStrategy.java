package com.seuoj.seuojbackend.service.contest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuoj.seuojbackend.entity.Contest;
import com.seuoj.seuojbackend.entity.ContestScriptInputProfile;
import com.seuoj.seuojbackend.entity.Submission;
import com.seuoj.seuojbackend.entity.SubmissionDetail;
import com.seuoj.seuojbackend.entity.UserInfo;
import com.seuoj.seuojbackend.exception.BadRequestException;
import com.seuoj.seuojbackend.exception.InternalServerException;
import com.seuoj.seuojbackend.mapper.ContestScriptInputProfileMapper;
import com.seuoj.seuojbackend.mapper.SubmissionDetailMapper;
import com.seuoj.seuojbackend.service.contest.custom.ContestScriptInputFieldRegistry;
import com.seuoj.seuojbackend.vo.contest.ContestProblemOverviewVO;
import com.seuoj.seuojbackend.vo.contest.ContestStandingsRecordVO;
import com.seuoj.seuojbackend.vo.contest.ContestStandingsScoreDetailVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CustomScriptRankingStrategy implements ContestRankingStrategy {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final SubmissionDetailMapper submissionDetailMapper;
    private final ContestScriptInputProfileMapper contestScriptInputProfileMapper;
    private final ContestScriptInputFieldRegistry contestScriptInputFieldRegistry;

    public CustomScriptRankingStrategy(SubmissionDetailMapper submissionDetailMapper,
                                       ContestScriptInputProfileMapper contestScriptInputProfileMapper,
                                       ContestScriptInputFieldRegistry contestScriptInputFieldRegistry) {
        this.submissionDetailMapper = submissionDetailMapper;
        this.contestScriptInputProfileMapper = contestScriptInputProfileMapper;
        this.contestScriptInputFieldRegistry = contestScriptInputFieldRegistry;
    }

    @Override
    public List<ContestStandingsRecordVO> computeStandings(
            List<ContestProblemOverviewVO> problems,
            Map<Long, UserInfo> registeredUsers,
            List<Submission> finishedSubmissions,
            Contest contest,
            Map<Long, String> problemIdToPid) {

        String script = contest.getScoringScript();
        if (script == null || script.isBlank()) {
            throw new BadRequestException("自定义赛制缺少 scoring_script");
        }

        Map<Long, SubmissionDetail> submissionDetailMap = buildSubmissionDetailMap(finishedSubmissions);
        Map<String, Object> input = buildInput(
                problems, registeredUsers, finishedSubmissions, submissionDetailMap, contest, problemIdToPid);

        try (Context context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.NONE)
                .allowNativeAccess(false)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowIO(IOAccess.NONE)
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {

            String inputJson = OBJECT_MAPPER.writeValueAsString(input);
            String wrappedScript = script + "\n\ncomputeStandings(JSON.parse(inputJson));";

            context.getBindings("js").putMember("inputJson", inputJson);

            Value result;
            try {
                result = context.eval("js", wrappedScript);
            } catch (Exception e) {
                throw new BadRequestException("自定义脚本执行错误: " + e.getMessage());
            }

            return parseScriptResult(result);

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Custom script execution failed", e);
            throw new InternalServerException("自定义脚本执行异常");
        }
    }

    private Map<String, Object> buildInput(
            List<ContestProblemOverviewVO> problems,
            Map<Long, UserInfo> registeredUsers,
            List<Submission> finishedSubmissions,
            Map<Long, SubmissionDetail> submissionDetailMap,
            Contest contest,
            Map<Long, String> problemIdToPid) {

        Map<String, Object> input = new HashMap<>();

        Map<String, Object> contestInfo = new HashMap<>();
        contestInfo.put("start_time", contest.getStartTime().toString());
        contestInfo.put("end_time", contest.getEndTime().toString());
        contestInfo.put("config", contest.getScoringConfig());
        input.put("contest", contestInfo);

        List<Map<String, Object>> problemsList = new ArrayList<>();
        for (ContestProblemOverviewVO p : problems) {
            Map<String, Object> pm = new HashMap<>();
            pm.put("pid", p.getPid());
            pm.put("sort_order", p.getSortOrder());
            problemsList.add(pm);
        }
        input.put("problems", problemsList);

        List<String> enabledFields = loadContestEnabledFields(contest.getId());

        // Group submissions by user and problem
        Map<Long, Map<String, List<Map<String, Object>>>> userSubmissions = new HashMap<>();
        for (Submission s : finishedSubmissions) {
            String pid = problemIdToPid.get(s.getProblemId());
            if (pid == null) continue;
            SubmissionDetail detail = submissionDetailMap.get(s.getId());

            userSubmissions
                    .computeIfAbsent(s.getUserId(), k -> new HashMap<>())
                    .computeIfAbsent(pid, k -> new ArrayList<>())
                    .add(contestScriptInputFieldRegistry.buildSubmissionInput(s, detail, enabledFields));
        }

        Map<String, Object> users = new LinkedHashMap<>();
        for (Map.Entry<Long, UserInfo> entry : registeredUsers.entrySet()) {
            Long userId = entry.getKey();
            UserInfo user = entry.getValue();
            Map<String, Object> userObj = new HashMap<>();
            userObj.put("username", user.getUsername());
            userObj.put("nickname", user.getNickname());
            userObj.put("submissions", userSubmissions.getOrDefault(userId, new HashMap<>()));
            users.put(userId.toString(), userObj);
        }
        input.put("users", users);

        return input;
    }

    private List<String> loadContestEnabledFields(Long contestId) {
        if (contestId == null) {
            return List.of();
        }
        ContestScriptInputProfile profile = contestScriptInputProfileMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ContestScriptInputProfile>()
                        .eq(ContestScriptInputProfile::getContestId, contestId));
        if (profile == null || profile.getEnabledFields() == null || profile.getEnabledFields().isBlank()) {
            return List.of();
        }
        try {
            List<String> enabledFields = OBJECT_MAPPER.readValue(
                    profile.getEnabledFields(),
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
            return contestScriptInputFieldRegistry.normalizeEnabledFields(enabledFields);
        } catch (Exception e) {
            log.warn("Invalid contest script input profile, fallback to default. contestId={}", contestId, e);
            return List.of();
        }
    }

    private Map<Long, SubmissionDetail> buildSubmissionDetailMap(List<Submission> finishedSubmissions) {
        if (finishedSubmissions == null || finishedSubmissions.isEmpty()) {
            return Map.of();
        }

        List<Long> submissionIds = finishedSubmissions.stream()
                .map(Submission::getId)
                .filter(id -> id != null)
                .toList();
        if (submissionIds.isEmpty()) {
            return Map.of();
        }

        List<SubmissionDetail> details = submissionDetailMapper.selectList(
                new LambdaQueryWrapper<SubmissionDetail>()
                        .in(SubmissionDetail::getSubmissionId, submissionIds));
        Map<Long, SubmissionDetail> map = new HashMap<>();
        for (SubmissionDetail detail : details) {
            if (detail == null || detail.getSubmissionId() == null) continue;
            map.put(detail.getSubmissionId(), detail);
        }
        return map;
    }

    private List<ContestStandingsRecordVO> parseScriptResult(Value result) {
        if (!result.hasArrayElements()) {
            throw new BadRequestException("自定义脚本必须返回数组");
        }

        List<ContestStandingsRecordVO> records = new ArrayList<>();
        for (long i = 0; i < result.getArraySize(); i++) {
            Value item = result.getArrayElement(i);
            ContestStandingsRecordVO record = new ContestStandingsRecordVO();
            record.setRank((int) (i + 1));

            if (item.hasMember("user_id")) {
                // user_id is available for script to pass through; username/nickname resolved below
            }

            record.setScore(item.hasMember("score") ? item.getMember("score").asInt() : 0);

            if (item.hasMember("username")) {
                record.setUsername(item.getMember("username").isNull() ? null : item.getMember("username").asString());
            }
            if (item.hasMember("nickname")) {
                record.setNickname(item.getMember("nickname").isNull() ? null : item.getMember("nickname").asString());
            }

            Map<String, ContestStandingsScoreDetailVO> scoreDetails = new LinkedHashMap<>();
            if (item.hasMember("score_details")) {
                Value details = item.getMember("score_details");
                for (String pid : details.getMemberKeys()) {
                    Value d = details.getMember(pid);
                    ContestStandingsScoreDetailVO detail = new ContestStandingsScoreDetailVO();
                    if (d.hasMember("score")) detail.setScore(d.getMember("score").asInt());
                    if (d.hasMember("accepted")) detail.setAccepted(d.getMember("accepted").asBoolean());
                    if (d.hasMember("unacceptedCount")) detail.setUnacceptedCount(d.getMember("unacceptedCount").asInt());
                    if (d.hasMember("acceptedTime")) detail.setAcceptedTime(d.getMember("acceptedTime").asLong());
                    if (d.hasMember("weighted_score")) detail.setWeightedScore(d.getMember("weighted_score").asInt());
                    if (d.hasMember("judge_id")) detail.setJudgeId(d.getMember("judge_id").asLong());
                    scoreDetails.put(pid, detail);
                }
            }
            record.setScoreDetails(scoreDetails);
            records.add(record);
        }
        return records;
    }
}
