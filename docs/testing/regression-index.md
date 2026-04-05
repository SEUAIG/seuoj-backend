# 回归测试索引

| 回归ID | 来源 | 测试类#方法 | 场景 | 预期 |
|---|---|---|---|---|
| REG-PROBLEM-GH030-001 | GH-030 | `GH030_ProblemVisibilityRegressionTest#normalUserShouldNotSeePrivateProblemsInProblemPage` | 普通用户查看题库列表 | 不返回私有题 |
| REG-PROBLEM-GH030-002 | GH-030 | `GH030_ProblemVisibilityRegressionTest#adminShouldSeeAllProblemsInProblemPage` | 管理员查看题库列表 | 返回公开题与私有题 |
| REG-PROBLEM-GH030-003 | GH-030 | `GH030_ProblemVisibilityRegressionTest#normalUserShouldReceive404WhenReadingPrivateProblemDirectly` | 普通用户按 pid 查看私有题 | 返回 404，隐藏资源存在性 |
| REG-PROBLEM-GH030-004 | GH-030 | `GH030_ProblemVisibilityRegressionTest#adminShouldReadPrivateProblemDetailDirectly` | 管理员按 pid 查看私有题 | 返回 200 且题面详情完整 |
| REG-PROBLEMSET-GH029-001 | GH-029 | `GH029_ProblemSetApiRegressionTest#createProblemSetShouldReturnPublicIdAndDetailShouldContainAllProblems` | 创建题单并配置题目后查看详情 | 创建返回 `problem_set_public_id`，详情返回完整题目列表 |

## 维护规则
- 新增 Bug 修复必须同步新增回归用例并登记本表。
- 回归 ID 命名：`REG-<模块>-<Issue编号>-<序号>`。
- 回归用例必须落到可复现的断言（HTTP + 业务码 + 关键业务字段/持久化结果）。
