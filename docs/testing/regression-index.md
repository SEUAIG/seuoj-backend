# 回归测试索引

| 回归ID | 来源 | 测试类#方法 | 场景 | 预期 |
|---|---|---|---|---|
| REG-AUTH-GH001-001 | GH-001 | `GH001_RegisterRaceRegressionTest#registerConcurrentSameEmailShouldOnlySucceedOnce` | 相同邮箱并发注册 | 仅 1 次成功，另 1 次冲突 |
| REG-AUTH-GH001-002 | GH-001 | `GH001_RegisterRaceRegressionTest#registerShouldFailAfterTooManyWrongCodes` | 验证码连续输错超过阈值 | 返回 `CODE_TOO_MANY_TRIES` |

## 维护规则
- 新增 Bug 修复必须同步增加回归用例并登记本表。
- 回归ID命名：`REG-<模块>-<Issue编号>-<序号>`。
