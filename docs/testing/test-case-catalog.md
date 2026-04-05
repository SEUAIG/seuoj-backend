# 测试用例目录（统一登记）

说明：本表用于统一登记“用例命名 + 场景 + 预期”。当前不按包分散文档，全部集中维护在 `docs/testing`。

| 用例ID | 测试类#方法 | 场景 | 预期HTTP | 预期业务码 | 关键断言 |
|---|---|---|---|---|---|
| INT-AUTH-001 | `AuthzIntegrationTest#submissionPageShouldReturn401WhenNoToken` | 未携带 token 访问提交分页 | 401 | 40100 | 返回未登录错误 |
| INT-AUTH-002 | `AuthzIntegrationTest#submissionPageShouldReturn401WhenTokenInvalid` | 非法 token 访问提交分页 | 401 | 40100 | 返回未登录错误 |
| INT-AUTH-003 | `AuthzIntegrationTest#createProblemShouldReturn403WhenUserRoleIsInsufficient` | 普通用户创建题目 | 403 | 40300 | 角色不足拒绝 |
| INT-PROBLEM-001 | `ProblemReadIntegrationTest#guestShouldReadPublicProblemDetail` | 游客看公开题 | 200 | 0 | 返回 `pid=p-public` |
| INT-PROBLEM-002 | `ProblemReadIntegrationTest#guestShouldNotReadPrivateProblemDetail` | 游客看私有题 | 404 | 40400 | 资源不可见 |
| INT-PROBLEM-003 | `ProblemReadIntegrationTest#adminShouldReadPrivateProblemDetail` | 管理员看私有题 | 200 | 0 | 返回 `pid=p-private` |
| INT-PROBLEM-004 | `ProblemManagementIntegrationTest#adminShouldCreateProblem` | 管理员创建题目 | 200 | 0 | 返回新 `pid` 且 DB 持久化成功 |
| INT-PROBLEM-005 | `ProblemManagementIntegrationTest#adminShouldEditProblem` | 管理员编辑题目 | 200 | 0 | DB 中标题更新成功 |
| INT-PROBLEM-006 | `ProblemManagementIntegrationTest#deleteShouldFailWhenProblemHasActiveRelations` | 删除仍被关联的题目 | 409 | 40900 | 返回冲突错误 |
| INT-PROBLEM-007 | `ProblemManagementIntegrationTest#deleteShouldSucceedWhenProblemHasNoRelations` | 删除无关联题目 | 200 | 0 | DB 中题目被删除 |
| INT-PROBLEM-008 | `ProblemReadIntegrationTest#shouldRejectWhenContestAndProblemSetContextBothProvided` | 看题上下文参数互斥冲突 | 400 | 40000 | 参数错误 |
| INT-PROBLEM-009 | `ProblemReadIntegrationTest#shouldReturn502WhenJudgeClientFetchContentFailed` | 看题时 judge 内容接口异常 | 502 | 50001 | 上游评测服务异常 |
| INT-SUBMISSION-001 | `SubmissionFlowIntegrationTest#shouldFinishSubmissionAndQueryResult` | 完整提交并查询结果主链路 | 200 | 0 | 状态 `Finished`、`Accepted`、分数 100、计数递增 |
| INT-SUBMISSION-002 | `SubmissionFlowIntegrationTest#shouldRejectSubmissionWhenProblemNotPublic` | 普通用户提交私有题 | 404 | 40400 | 资源不可见 |
| INT-SUBMISSION-003 | `SubmissionFlowIntegrationTest#shouldRejectResultQueryWhenNotOwnerOrAdmin` | 非提交者查询结果 | 403 | 40300 | 越权访问拒绝 |
| INT-SUBMISSION-004 | `SubmissionExceptionIntegrationTest#submitShouldRejectWhenLanguageInvalid` | 提交语言非法 | 400 | 40000 | 参数校验失败 |
| INT-SUBMISSION-005 | `SubmissionExceptionIntegrationTest#submitShouldRejectWhenCodeBlank` | 提交代码为空白 | 400 | 40000 | 参数校验失败 |
| INT-SUBMISSION-006 | `SubmissionExceptionIntegrationTest#submitShouldRejectWhenPidBlank` | 提交题目ID为空白 | 400 | 40000 | 参数校验失败 |
| INT-SUBMISSION-007 | `SubmissionExceptionIntegrationTest#listSubmissionsShouldRejectWhenCurrentLessThanOne` | 提交分页页码非法 | 400 | 40000 | 参数错误 |
| INT-SUBMISSION-008 | `SubmissionExceptionIntegrationTest#listSubmissionsShouldRejectWhenSizeTooLarge` | 提交分页大小超限 | 400 | 40000 | 参数错误 |
| INT-SUBMISSION-009 | `SubmissionExceptionIntegrationTest#getResultShouldReturn404WhenSubmissionNotExists` | 查询不存在提交 | 404 | 40400 | 资源不存在 |
| INT-SUBMISSION-010 | `SubmissionExceptionIntegrationTest#submitShouldMarkFailedWhenJudgeClientThrows` | 提交后 judge 侧调用异常 | 200 | 0 | 提交仍返回成功，记录状态为 `Failed` |
| INT-JUDGE-001 | `JudgeCallbackIntegrationTest#callbackShouldRejectWhenSecretMissing` | judge 回调缺失密钥 | 403 | 40301 | 拒绝回调 |
| INT-JUDGE-002 | `JudgeCallbackIntegrationTest#callbackShouldRejectWhenSecretInvalid` | judge 回调密钥错误 | 403 | 40301 | 拒绝回调 |
| INT-JUDGE-003 | `JudgeCallbackIntegrationTest#callbackShouldRejectWhenStatusUnknown` | judge 回调状态非法 | 400 | 40000 | 参数反序列化失败 |
| INT-JUDGE-004 | `JudgeCallbackIntegrationTest#callbackShouldReturn404WhenSubmissionNotFound` | judge 回调提交号不存在 | 404 | 40400 | 资源不存在 |
| INT-JUDGE-005 | `JudgeCallbackIntegrationTest#callbackShouldRejectWhenSuccessWithoutResultDetail` | 回调 Success 但缺少 resultDetail | 400 | 40000 | 业务参数错误 |
| INT-JUDGE-006 | `JudgeCallbackIntegrationTest#callbackShouldRejectWhenResultDetailItemInvalid` | 回调 resultDetail 子项字段缺失 | 400 | 40000 | Bean 校验失败 |
| INT-JUDGE-007 | `JudgeCallbackIntegrationTest#callbackCompileErrorShouldBeVisibleInResultQuery` | 回调 CompileError 后查询结果 | 200 | 0 | 状态 `Finished`，`verdict=CompileError`，错误详情可见 |
| REG-AUTH-GH001-001 | `GH001_RegisterRaceRegressionTest#registerConcurrentSameEmailShouldOnlySucceedOnce` | 同邮箱并发注册竞态 | - | - | 1 成功 + 1 冲突 |
| REG-AUTH-GH001-002 | `GH001_RegisterRaceRegressionTest#registerShouldFailAfterTooManyWrongCodes` | 验证码错误次数上限 | - | 42901 | 返回错误码 `CODE_TOO_MANY_TRIES` |

## 命名规则
- 集成测试：`INT-<模块>-<三位序号>`
- 回归测试：`REG-<模块>-<Issue编号>-<三位序号>`
- 模块值建议：`AUTH`、`PROBLEM`、`SUBMISSION`
