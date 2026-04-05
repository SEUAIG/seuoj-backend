# 测试用例目录
本目录用于记录集成测试与回归测试的用例名称、场景与预期，确保每个测试都有明确规范，避免无意义测试。

| 用例ID | 测试方法 | 场景 | 预期HTTP | 预期业务码 | 关键断言 |
|---|---|---|---|---|---|
| INT-AUTH-001 | `AuthzIntegrationTest#submissionPageShouldReturn401WhenNoToken` | 未登录访问提交列表 | 401 | 40100 | 返回未登录错误 |
| INT-AUTH-002 | `AuthzIntegrationTest#submissionPageShouldReturn401WhenTokenInvalid` | 非法 token 访问提交列表 | 401 | 40100 | 返回未登录错误 |
| INT-AUTH-003 | `AuthzIntegrationTest#createProblemShouldReturn403WhenUserRoleIsInsufficient` | 普通用户创建题目 | 403 | 40300 | 返回权限不足 |
| INT-PROBLEM-001 | `ProblemReadIntegrationTest#guestShouldReadPublicProblemDetail` | 匿名查看公开题目 | 200 | 0 | 返回 `pid=p-public` |
| INT-PROBLEM-002 | `ProblemReadIntegrationTest#guestShouldNotReadPrivateProblemDetail` | 匿名查看私有题目 | 404 | 40400 | 返回资源不存在 |
| INT-PROBLEM-003 | `ProblemReadIntegrationTest#adminShouldReadPrivateProblemDetail` | 管理员查看私有题目 | 200 | 0 | 返回 `pid=p-private` |
| INT-PROBLEM-004 | `ProblemManagementIntegrationTest#adminShouldCreateProblem` | 管理员创建题目 | 200 | 0 | 创建成功并持久化 |
| INT-PROBLEM-005 | `ProblemManagementIntegrationTest#adminShouldEditProblem` | 管理员编辑题目 | 200 | 0 | 编辑内容持久化 |
| INT-PROBLEM-006 | `ProblemManagementIntegrationTest#deleteShouldFailWhenProblemHasActiveRelations` | 删除有关联的题目 | 409 | 40900 | 返回冲突 |
| INT-PROBLEM-007 | `ProblemManagementIntegrationTest#deleteShouldSucceedWhenProblemHasNoRelations` | 删除无关联题目 | 200 | 0 | 删除成功 |
| INT-PROBLEM-008 | `ProblemReadIntegrationTest#shouldRejectWhenContestAndProblemSetContextBothProvided` | 同时传 contest 与 problem_set 上下文 | 400 | 40000 | 参数冲突被拒绝 |
| INT-PROBLEM-009 | `ProblemReadIntegrationTest#shouldReturn502WhenJudgeClientFetchContentFailed` | 读取题面时 judge 服务异常 | 502 | 50001 | 返回第三方服务错误 |
| INT-SUBMISSION-001 | `SubmissionFlowIntegrationTest#shouldFinishSubmissionAndQueryResult` | 提交成功并收到判题回调后查询结果 | 200 | 0 | `Finished`、`Accepted`、分数正确 |
| INT-SUBMISSION-002 | `SubmissionFlowIntegrationTest#shouldRejectSubmissionWhenProblemNotPublic` | 提交私有题目 | 404 | 40400 | 返回资源不存在 |
| INT-SUBMISSION-003 | `SubmissionFlowIntegrationTest#shouldRejectResultQueryWhenNotOwnerOrAdmin` | 非所有者查询提交结果 | 403 | 40300 | 返回权限不足 |
| INT-SUBMISSION-004 | `SubmissionExceptionIntegrationTest#submitShouldRejectWhenLanguageInvalid` | 提交语言非法 | 400 | 40000 | 参数校验失败 |
| INT-SUBMISSION-005 | `SubmissionExceptionIntegrationTest#submitShouldRejectWhenCodeBlank` | 提交代码为空白 | 400 | 40000 | 参数校验失败 |
| INT-SUBMISSION-006 | `SubmissionExceptionIntegrationTest#submitShouldRejectWhenPidBlank` | 提交 pid 为空白 | 400 | 40000 | 参数校验失败 |
| INT-SUBMISSION-007 | `SubmissionExceptionIntegrationTest#listSubmissionsShouldRejectWhenCurrentLessThanOne` | 分页 current < 1 | 400 | 40000 | 参数校验失败 |
| INT-SUBMISSION-008 | `SubmissionExceptionIntegrationTest#listSubmissionsShouldRejectWhenSizeTooLarge` | 分页 size > 100 | 400 | 40000 | 参数校验失败 |
| INT-SUBMISSION-009 | `SubmissionExceptionIntegrationTest#getResultShouldReturn404WhenSubmissionNotExists` | 查询不存在的提交 | 404 | 40400 | 返回资源不存在 |
| INT-SUBMISSION-010 | `SubmissionExceptionIntegrationTest#submitShouldMarkFailedWhenJudgeClientThrows` | 提交后 judge 调用异常 | 200 | 0 | 记录状态为 `Failed` |
| INT-SUBMISSION-011 | `SubmissionExceptionIntegrationTest#submitShouldReturn401WhenNoToken` | 未登录提交代码 | 401 | 40100 | 返回未登录错误 |
| INT-SUBMISSION-012 | `SubmissionExceptionIntegrationTest#getResultShouldReturn401WhenNoToken` | 未登录查询评测结果 | 401 | 40100 | 返回未登录错误 |
| INT-SUBMISSION-013 | `SubmissionExceptionIntegrationTest#submitShouldRejectWhenCodeTooLong` | 代码超出长度上限 | 400 | 40000 | 参数校验失败 |
| INT-SUBMISSION-014 | `SubmissionExceptionIntegrationTest#listSubmissionsShouldRejectWhenCurrentTypeInvalid` | 分页参数类型错误 | 400 | 40000 | 类型转换失败 |
| INT-JUDGE-003 | `JudgeCallbackIntegrationTest#callbackShouldRejectWhenStatusUnknown` | judge 回调状态非法 | 400 | 40000 | 参数校验失败 |
| INT-JUDGE-004 | `JudgeCallbackIntegrationTest#callbackShouldReturn404WhenSubmissionNotFound` | 回调提交号不存在 | 404 | 40400 | 返回资源不存在 |
| INT-JUDGE-005 | `JudgeCallbackIntegrationTest#callbackShouldRejectWhenSuccessWithoutResultDetail` | Success 但无 `resultDetail` | 400 | 40000 | 业务校验失败 |
| INT-JUDGE-006 | `JudgeCallbackIntegrationTest#callbackShouldRejectWhenResultDetailItemInvalid` | `resultDetail` 子项缺字段 | 400 | 40000 | Bean 校验失败 |
| INT-JUDGE-007 | `JudgeCallbackIntegrationTest#callbackCompileErrorShouldBeVisibleInResultQuery` | CompileError 后查询结果 | 200 | 0 | `verdict=CompileError` 且错误详情可见 |
| INT-JUDGE-008 | `JudgeCallbackIntegrationTest#callbackShouldBeIdempotentWhenRepeatedSuccess` | judge 重复回调 Success | 200 | 0 | AC 统计不重复累计 |
| INT-JUDGE-009 | `JudgeCallbackIntegrationTest#callbackShouldUpdateWhenSubmissionWasFailed` | 提交为 `Failed` 后收到成功回调 | 200 | 0 | 状态更新为 `Finished/Accepted` |
| REG-PROBLEM-GH030-001 | `GH030_ProblemVisibilityRegressionTest#normalUserShouldNotSeePrivateProblemsInProblemPage` | 普通用户查看题库列表 | 200 | 0 | 返回结果不包含私有题 `p-private` |
| REG-PROBLEM-GH030-002 | `GH030_ProblemVisibilityRegressionTest#adminShouldSeeAllProblemsInProblemPage` | 管理员查看题库列表 | 200 | 0 | 返回结果包含公开题与私有题 |
| REG-PROBLEM-GH030-003 | `GH030_ProblemVisibilityRegressionTest#normalUserShouldReceive404WhenReadingPrivateProblemDirectly` | 普通用户按 pid 查看私有题 | 404 | 40400 | 返回资源不存在，避免泄露 |
| REG-PROBLEM-GH030-004 | `GH030_ProblemVisibilityRegressionTest#adminShouldReadPrivateProblemDetailDirectly` | 管理员按 pid 查看私有题 | 200 | 0 | 返回 `pid=p-private` 且 `is_public=false` |
| REG-PROBLEMSET-GH029-001 | `GH029_ProblemSetApiRegressionTest#createProblemSetShouldReturnPublicIdAndDetailShouldContainAllProblems` | 创建题单并配置题目后查看详情 | 200 | 0 | 创建返回 `problem_set_public_id`，详情返回完整 `problem_list` |

## 说明
- 用例 ID 命名规范统一参考 `docs/testing/README.md`，本文件只维护“用例清单”本身，避免重复定义。
