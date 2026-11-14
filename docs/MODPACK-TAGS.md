# 整合包标签 / Modpack Tags

本模组随附以下数据包标签，整合包可以用数据包增删成员（`"replace": false` 合并，`"replace": true` 覆盖）。配方暂未改用这些标签。

The mod ships the tags below. Packs can add or remove members with a datapack (`"replace": false` merges, `"replace": true` overrides). Recipes do not use these tags yet.

## 通用 `c:` 标签 / Common `c:` tags

| 标签 / Tag | 类型 / Kind | 成员 / Members |
| --- | --- | --- |
| `c:ores` | block, item | `#c:ores/constraint_metal`, `#c:ores/imag_crystal`, `#c:ores/resonant_crystal`, `#c:ores/imag_silicon` |
| `c:ores/constraint_metal` | block, item | `academy:constraint_metal` |
| `c:ores/imag_crystal` | block, item | `academy:crystal_ore` |
| `c:ores/resonant_crystal` | block, item | `academy:reso_ore` |
| `c:ores/imag_silicon` | block, item | `academy:imagsil_ore` |
| `c:ores_in_ground/stone` | block, item | 以上四种矿石 / all four ores |
| `c:ingots`, `c:ingots/constraint_metal` | item | `academy:constraint_ingot` |
| `c:ingots`, `c:ingots/imag_silicon` | item | `academy:imag_silicon_ingot` |
| `c:plates`, `c:plates/constraint_metal` | item | `academy:constraint_plate` |
| `c:gems`, `c:gems/imag_crystal` | item | `academy:crystal_low` |
| `c:gems`, `c:gems/resonant_crystal` | item | `academy:reso_crystal` |

矿石进入 `c:ores` 后，其他模组的矿石处理与本模组的矿物探测都能识别。中/高纯度晶体、强化铁板与硅片属于加工产物，不放入通用标签。

Ores in `c:ores` are visible to other mods' ore processing and to Mine Detect. Mid/high-purity crystals, the reinforced iron plate and wafers are processed parts and are not in common tags.

## 本模组标签 / Mod tags

| 标签 / Tag | 类型 / Kind | 作用 / Effect | 默认 / Default |
| --- | --- | --- | --- |
| `academy:vector_immune` | entity_type | 矢量偏移/矢量反射永不推动、也不受反射回击伤害，优先于一切规则 / Vector Deviation and Reflection never move these or hit them with reflected damage; wins over every other rule | 空 / empty |
| `academy:vector_affectable` | entity_type | 让被旧规则排除的生物等实体也受矢量偏移/反射影响 / opt-in for entities the legacy rule excludes (living entities, items) | 空 / empty |
| `academy:mine_ray_immune` | block | 破坏射线永不破坏 / Mine Rays never break these | 空 / empty |
| `academy:metal/normal` | block | 追加到配置 `ability.normalMetalBlocks` / added to config `normalMetalBlocks` | 空 / empty |
| `academy:metal/weak` | block | 追加到配置 `ability.weakMetalBlocks` / added to config `weakMetalBlocks` | 空 / empty |
| `academy:is_ability` | damage_type | 本模组定义的全部伤害类型，供其他模组做“能力伤害”抗性 / every damage type the mod defines, for "ability damage" resistance | 全部 7 种 / all 7 |
| `academy:mag_manip_immovable` | block | 电磁操控不可搬运（已有）/ Mag Manip cannot pick up (existing) | 开发机、矩阵 / developers, matrix |
| `academy:arc_conductors` | block | 开启能力时 5 格内这些方块冒电弧（最近 32 个）/ arcs on these blocks within 5 blocks when the ability turns on (nearest 32) | 奇点迭代的 13 种电缆（可选条目）/ Singularity Iteration's 13 cables (optional entries) |

金属方块判定为“配置列表 **或** 标签”。默认金属仍由服务端配置提供；标签默认为空，只用于追加。要移除默认金属，请修改服务端配置。

A block is metal if it is in the config list **or** the tag. Default metals still come from the server config; the tags ship empty and only add. To remove a default metal, edit the server config.
