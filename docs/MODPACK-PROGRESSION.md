# 能力成长与开发机数值 / Ability progression and developer numbers

数据包目录 / datapack directory: `data/<namespace>/academy_progression/*.json`

- 所有字段可选；缺省值就是内置的 AcademyCraft 1.0.7 数值，模组本身不附带任何 JSON。
  All fields are optional; absent fields keep the built-in 1.0.7 values. The mod ships no JSON files.
- 多个文件按 id 排序后依次合并，后面的文件覆盖前面文件里写到的单个字段。
  Files merge in id order; a later file overrides only the fields it names.
- `/reload` 生效并同步到客户端（技能树的能量估算、等级进度条与服务端一致）。已在线玩家的最大 CP/过载在下次重算时（升级、学习技能、重新登录等）更新。
  Applied on `/reload` and synced to clients, so skill-tree estimates match the server. Online players'
  max CP/overload pick up the change on their next recalculation (level-up, learning a skill, relog).
- 非法字段（数组长度不是 6、非有限数、负数、类型错误）会在日志里警告并保留原值；部分数值会被夹取。
  Invalid fields (array length not 6, non-finite, negative, wrong type) are logged and keep the previous
  value; some values are clamped.

## 平衡档位 / Balance profile

服务端配置 `balance.profile`（默认 `MODERN`）选择档位层 `data/<命名空间>/academy_balance/<modern|legacy>/progression/*.json`，格式与本页相同。层次：内置 1.0.7 数值 → 当前档位的文件 → `academy_progression/` 文件，后者逐字段覆盖前者。切换档位立即生效。

Server config `balance.profile` (default `MODERN`) picks the layer `data/<namespace>/academy_balance/<modern|legacy>/progression/*.json`, in the format below. Layers: built-in 1.0.7 values → the profile's files → the `academy_progression/` files, each overriding single fields. Switching applies at once.

本模组附带的 `modern` 档位 / the shipped `modern` profile (`academy_balance/modern/progression/modern.json`)：

| 字段 / Field | 1.0.7（legacy） | modern |
|---|---|---|
| `cp.initial` | 1800, 1800, 2800, 4000, 5800, 8000 | 2000, 2000, 3500, 6000, 10000, 16000 |
| `cp.max_added` | 0, 900, 1000, 1500, 1700, 12000 | 0, 1000, 1750, 3000, 5000, 8000 |
| `overload.initial` | 100, 100, 150, 240, 350, 500 | 150, 150, 240, 380, 560, 800 |
| `overload.max_added` | 0, 40, 70, 80, 100, 500 | 0, 60, 105, 120, 150, 750 |
| `level_up.threshold_per_skill` | 0.666 ×4, 1.333, 0.666 | 0.4 ×4, 0.8, 0.4 |
| `rules.cp_recovery_multiplier` | 1（空到满约 115 秒 / ~115 s empty to full） | 2.25（约 51 秒 / ~51 s） |
| `rules.usage_growth_multiplier` | 1 | 8 |
| `rules.proficiency_gain_multiplier` | 1 | 2 |
| `rules.keep_usage_growth_on_level_up` | false | true |
| `rules.overload_unlock_fraction` | 0 | 0.5 |
| `rules.proficiency_cp_discount` | 0 | 0.5 |
| `rules.prerequisite_proficiency_cap` | 1 | 0.75 |
| `cp_potion.max_restore` | 2000 | 1000000000 |

## Schema（括号内为默认值 / defaults in brackets）

| 字段 Field | 说明 Meaning |
|---|---|
| `cp.initial` | 各等级 L0..L5 基础最大 CP `[1800,1800,2800,4000,5800,8000]` |
| `cp.max_added` | 各等级使用技能可增长的 CP 上限 `[0,900,1000,1500,1700,12000]` |
| `overload.initial` | 各等级基础过载上限 `[100,100,150,240,350,500]` |
| `overload.max_added` | 各等级可增长的过载上限 `[0,40,70,80,100,500]` |
| `courses.brain_course_cp` | 脑域课程 CP `1000` |
| `courses.brain_course_advanced_cp` | 高级脑域课程 CP `1500` |
| `courses.brain_course_advanced_overload` | 高级脑域课程过载 `100` |
| `courses.mind_course_recovery` | 心灵课程 CP 恢复倍率 `1.2` |
| `level_up.threshold_per_skill` | 升级进度：当前等级 L0..L5 每个可控技能所需经验 `[0.666,0.666,0.666,0.666,1.333,0.666]` |
| `developer.<portable\|normal\|advanced>.max_level` | 可学习的最高技能等级 portable `2` / normal `3` / advanced `2147483647` |
| `developer.<tier>.sync_rate` | 同步率（百分比，1..100）`30` / `70` / `100` |
| `developer.<tier>.ticks_per_stimulation` | 每次刺激的 tick（1..72000）`25` / `20` / `15`；实际耗时为该值 +1（1.0.7 行为） |
| `developer.<tier>.energy_per_stimulation` | 每次刺激能量（0..1000000）`750` / `700` / `600`；每 tick 扣 `energy / ticks`（整数除法） |
| `stimulations.learn_base` | 学习技能刺激次数 = `(int)(learn_base + 技能等级² × learn_per_level_squared)`，`3` |
| `stimulations.learn_per_level_squared` | `0.5` |
| `stimulations.level_up_per_level` | 升级刺激次数 = 该值 × (当前等级 + 1)，`5` |
| `stimulations.reset_per_level` | 重置能力刺激次数 = 该值 × 当前等级，`10` |
| `stimulations.induction` | 初次觉醒刺激次数 `5` |
| `induction.category_weights.<id>` | 无因子觉醒时随机选择类别的权重，默认每类 `1`；`0` = 永不随机到（因子仍可强制该类别）；全为 0 时退回均匀随机。类别 id: `electromaster`, `meltdowner`, `teleporter`, `vecmanip`, `aerohand`, `telekinesis` |

| `rules.cp_recovery_multiplier` | 乘在配置 `cpRecoverSpeed` 上 `1` / multiplies the config `cpRecoverSpeed` |
| `rules.overload_recovery_multiplier` | 乘在配置 `overloadRecoverSpeed` 上 `1` |
| `rules.usage_growth_multiplier` | 乘在配置 `maxCpIncreaseRate` 与 `maxOverloadIncreaseRate` 上 `1` |
| `rules.proficiency_gain_multiplier` | 乘在配置 `proficiencyIncreaseRate` 上 `1` |
| `rules.keep_usage_growth_on_level_up` | 升级时保留用量增长（仍受新等级上限约束）`false`；1.0.7 升级时清零 / keep usage growth on level-up (still capped by the new level); 1.0.7 cleared it |
| `rules.overload_unlock_fraction` | 过载降到上限的这一比例以下即解除锁定 `0`（1.0.7：降到 0）/ the lock lifts at or below this fraction of max overload |
| `rules.proficiency_cp_discount` | 满熟练度时 CP 消耗减少的比例，按熟练度线性 `0`（0..0.9）/ CP cost cut at full proficiency, linear in proficiency |
| `rules.prerequisite_proficiency_cap` | 高于此值的前置熟练度要求降到此值 `1` / prerequisite proficiency requirements above this are lowered to it |
| `cp_potion.restore_fraction` | CP 药水最多回复到最大 CP 的此比例 `0.5` / a CP potion refills up to this fraction of max CP |
| `cp_potion.max_restore` | 单瓶最多回复的 CP `2000` / at most this much CP per potion |

刺激次数被夹取到 1..1000。/ Stimulation counts are clamped to 1..1000.

## 示例 / Example

`data/mypack/academy_progression/tweaks.json`: Level 5 max CP doubled, advanced developer twice as fast,
Teleporter never picked by a random induction.

```json
{
  "cp": {
    "initial":   [1800, 1800, 2800, 4000, 5800, 16000],
    "max_added": [0, 900, 1000, 1500, 1700, 24000]
  },
  "developer": {
    "advanced": { "ticks_per_stimulation": 7, "energy_per_stimulation": 300 }
  },
  "induction": {
    "category_weights": { "teleporter": 0 }
  }
}
```

(`ticks_per_stimulation` 7 gives 8 real ticks per stimulation instead of 16; energy 300/7 = 42 per tick.)
