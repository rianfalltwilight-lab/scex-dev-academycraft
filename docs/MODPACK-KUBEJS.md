# KubeJS 集成 / KubeJS integration

AcademyCraft 自带一个 KubeJS 插件（KubeJS 2101.7.x，NeoForge 1.21.1）。未安装 KubeJS 时插件不会加载，模组行为不变。
AcademyCraft ships a KubeJS plugin (KubeJS 2101.7.x, NeoForge 1.21.1). Without KubeJS it is never loaded.

## 全局对象 `Academy` / The `Academy` binding

技能 id 可写 `railgun` 或 `academy:railgun`。修改玩家数据的方法只应在服务端脚本中调用，会自动同步客户端。
Skill ids accept `railgun` or `academy:railgun`. Player mutators belong in server scripts and resync the client.

| 方法 / Method | 说明 / Notes |
|---|---|
| `getCategory(p)` / `setCategory(p, 'electromaster' \| null)` | 能力系；切换时保留等级、清空已学技能 / keeps level, clears learned skills |
| `getLevel(p)` / `setLevel(p, 1..5)` | 能力等级 / ability level |
| `getCP(p)` `getMaxCP(p)` `setCP(p, v)` `addCP(p, v)` | 计算力 / CP |
| `getOverload(p)` `getMaxOverload(p)` `setOverload(p, v)` `isOverloaded(p)` | 过载与锁定 / overload and lockout |
| `isActive(p)` / `setActive(p, bool)` | 能力开关 / ability on/off |
| `learn(p, skill)` `unlearn(p, skill)` `hasLearned(p, skill)` `learnedSkills(p)` | 学习（跳过前置，限当前能力系）/ learn (no prerequisites, current category) |
| `getProficiency(p, skill)` / `setProficiency(p, skill, 0..1)` | 熟练度 / proficiency |
| `clearCooldown(p, skill)` / `clearCooldowns(p)` | 冷却 / cooldowns |
| `cast(mob, skill)` / `cast(mob, skill, proficiency, target)` | 生物施法，返回结果名 `CAST`/`ON_COOLDOWN`/… / mob cast, returns the result name |
| `setMobSkill(mob, skill, p)` `removeMobSkill(mob, skill)` `mobSkills(mob)` | 生物技能（会挂上施法 AI）/ mob skills (attaches the cast AI) |
| `supportedMobSkills()` `categories()` `skills(category)` | 查询 / queries |

## 事件组 `AcademyEvents` / Event group `AcademyEvents` (server)

| 事件 / Event | 可取消 / Cancel | 目标 / Target | 字段 / Fields |
|---|---|---|---|
| `skillCast` | 是 / yes（不扣费 / nothing paid） | 技能 id / skill id | `player`, `skill` |
| `skillDamage` | 是 / yes（丢弃此次伤害 / drops the hit） | — | `target`, `attacker`, `source`, `originalAmount`, `amount`（可写 / writable） |
| `mobSkillCast` | 是 / yes | 技能 id / skill id | `entity`（施法者 / caster）, `skill`, `proficiency`, `target` |
| `skillLearned` | 否 / no | 技能 id / skill id | `player`, `skill` |
| `levelChanged` | 否 / no | — | `player`, `oldAbilityLevel`, `abilityLevel` |
| `overloaded` | 否 / no | — | `player`, `overload`, `maximum` |

`skillDamage` 的 `amount` 已包含配置倍率与 `academy:skill_damage` / `academy:skill_resistance` 属性。
`skillDamage.amount` already includes the config multipliers and the skill damage/resistance attributes.

## 配方 / Recipes

```js
ServerEvents.recipes(event => {
  // 金属成型机：产物, 原料, 模式（plate / incise / etch / refine）
  event.recipes.academy.metal_forming('minecraft:diamond', 'minecraft:coal_block', 'plate').time(100).energy(20)
  event.recipes.academy.metal_forming('2x academy:wafer', '#c:ingots/silicon', 'incise').count(2)
  // 虚像融合器：产物, 原料, 相位液体 mB
  event.recipes.academy.imag_fusor('academy:crystal_pure', 'academy:crystal_normal', 8000).time(200)
})
```

可选方法 / Optional: `.time(ticks)`、`.energy(IF/t)`、`.count(n)`（原料数量 / input count），金属成型机另有 `.outputTag('c:ingots/x')` 与 `.outputCount(n)`。未写的字段不会写入配方，沿用机器配置。
Unset optional fields are not written and fall back to the machine config.

## 示例 / Example

[`docs/kubejs/academy_probe.js`](kubejs/academy_probe.js)：取消 `arc_gen`、按攻击者标签加倍技能伤害、各添加一条机器配方。
It cancels `arc_gen`, doubles ability damage from attackers tagged `academy_probe_double`, and adds one recipe of each machine.

## 开发者验证 / Developer check

```bash
mkdir -p /tmp/kjsrun/gametest/kubejs/server_scripts && cp docs/kubejs/academy_probe.js /tmp/kjsrun/gametest/kubejs/server_scripts/
./gradlew runGameTestServer -PwithKubeJS=true -PacceptanceRunRoot=/tmp/kjsrun
```

`KubeJSIntegrationGameTests` 的三个用例需在此环境下通过。注意：KubeJS 会在玩家登录时发送自己的网络包，GameTest 的模拟玩家连接无法承载，
因此此模式下凡是让模拟玩家登录的既有用例都会失败；探针脚本额外添加的两条配方也会让配方总数断言失败。这些是测试环境限制，不影响实际游戏。
The three `KubeJSIntegrationGameTests` must pass there. KubeJS sends its own payload when a player logs in, which the GameTest mock
connection cannot carry, so existing tests that log a mock player in fail in this mode, and the probe's two recipes break the recipe
count assertion — test-harness limits, not gameplay issues.
