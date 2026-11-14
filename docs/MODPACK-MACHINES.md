# 整合包机器数值 / Modpack Machine Numbers

服务端配置 `config/academy-server.toml`（存档内为 `serverconfig/`）的 `[machines]` 段控制机器能量、缓存与加工数值。默认值与 AcademyCraft 1.0.7 一致。数值在使用时读取，修改并重载配置后对已放置的机器生效；若调低容量，机器内超出的能量会在读档或下一个 tick 被截到新容量。

The `[machines]` section of the server config (`academy-server.toml`, per-world under `serverconfig/`) sets machine energy, buffers and processing. Defaults equal AcademyCraft 1.0.7. Values are read when used, so a config reload applies to placed machines. If a capacity is lowered, stored energy above it is clamped to the new capacity on load or on the next tick.

## 平衡档位倍率 / Balance profile multipliers

下表的配置值是 1.0.7 基准。平衡档位（`balance.profile`）在读取时再乘上进度数据 `machines` 段的倍率：容量类（缓存、节点容量、网络缓存）× `storage_multiplier`，带宽类 × `bandwidth_multiplier`，产能类（太阳能、风力、相位每 mB、猫引擎）× `generation_multiplier`，节点范围 × `range_multiplier`，能量物品（能量单元、能量单元组、便携开发机、虚数能量护甲及其他能量工具）的容量和速率 × `item_energy_multiplier`。开发机的缓存和带宽同样按容量、带宽倍率放大。`fePerIf` 不受档位影响，与奇点迭代的 1 IF = 1 EU 保持对等。

The config values below are the 1.0.7 baseline. The balance profile multiplies them when read, by the `machines` section of the progression data: capacities × `storage_multiplier`, bandwidths × `bandwidth_multiplier`, generation (solar, wind, phase per mB, cat engine) × `generation_multiplier`, node range × `range_multiplier`, and energy items' capacity and rate × `item_energy_multiplier`. Developer buffers and bandwidth take the storage and bandwidth multipliers. `fePerIf` never scales, keeping Singularity Iteration's 1 IF = 1 EU.

| 档位 / Profile | 容量 / storage | 带宽 / bandwidth | 产能 / generation | 范围 / range | 物品 / items |
|---|---|---|---|---|---|
| `legacy` | ×1 | ×1 | ×1 | ×1 | ×1 |
| `modern` | ×10 | ×10 | ×4 | ×1.5 | ×10 |

现代档的开发机每次刺激能耗也提高到约 4 倍（便携 3000、普通 2800、高级 2400 IF）。/ Modern developers also spend about 4x per stimulation (3000 / 2800 / 2400 IF).

## FE 接口 / Forge-energy faces

每台机器的 FE 接口每 tick 在每个方向上最多移动其 IF 带宽（× `fePerIf` FE）。用电设备（开发机、能力干扰器、虚数融合器、金属成型机）只接收；发电机（太阳能、风力底座、相位发电机）只输出；节点双向。RF 输出桥不会把电推回本模组自己的机器（它们走无线网络），避免回路。

Each machine's FE face moves at most its IF bandwidth (× `fePerIf` FE) per tick in each direction. Consumers (developers, ability interferer, imaginary fusor, metal former) only receive; generators (solar, wind base, phase generator) only give; nodes do both. The RF output bridge never pushes into AcademyCraft's own machines (they use the wireless network), so no loop forms.

## 比较器 / Comparators

节点、开发机、能力干扰器、虚数融合器、金属成型机、太阳能、风力底座和相位发电机都输出比较器信号：按储能比例 0–15（有电即至少 1）。

Nodes, developers, the ability interferer, imaginary fusor, metal former, solar generator, wind base and phase generator drive comparators by their stored energy, 0–15 (at least 1 when not empty).

## 配置项 / Keys

| 键 / Key | 默认 / Default | 说明 / Meaning |
| --- | --- | --- |
| `machines.fePerIf` | 4 | 1 IF 折合的 FE（能源桥、FE 物品/方块接口）/ FE per IF at every Forge-energy boundary |
| `machines.wireless.networkBuffer` | 2000 | 每个矩阵网络的共享缓存 IF / shared buffer per matrix network |
| `machines.node.basic.{maxEnergy,bandwidth,range,maxConnections}` | 15000, 150, 9.0, 5 | 基础节点 / basic node |
| `machines.node.standard.{…}` | 50000, 300, 12.0, 10 | 改良节点 / standard node |
| `machines.node.advanced.{…}` | 200000, 900, 19.0, 20 | 高级节点 / advanced node |
| `machines.matrix.capacityPerLevel` | 8.0 | 连接数 = 系数 × L（L = 核心等级 1..3）/ capacity = k·L |
| `machines.matrix.bandwidthPerLevelSquared` | 60.0 | 带宽 = 系数 × L² / bandwidth = k·L² |
| `machines.matrix.rangePerSqrtLevel` | 24.0 | 范围 = 系数 × √L（上限 256）/ range = k·√L, max 256 |
| `machines.solarGen.{buffer,dayRate,rainRate,bandwidth}` | 1000, 3.0, 0.6, 100.0 | 太阳能：晴天/雨天 IF/t / solar: clear / rain IF/t |
| `machines.windGen.{buffer,minRate,maxRate,minHeight,maxHeight,bandwidth}` | 20000, 7.5, 15.0, 70, 160, 300.0 | 风机：高度在 min..max 间线性插值 / wind: linear between the heights |
| `machines.phaseGen.{buffer,energyPerMb,maxDrainPerTick,bandwidth}` | 6000, 0.5, 100, 50.0 | 相位发电机 / phase generator |
| `machines.catEngine.{buffer,generation,bandwidth}` | 2000, 500.0, 200.0 | 猫引擎 / cat engine |
| `machines.imagFusor.{buffer,bandwidth,processTime,energyPerTick}` | 2000.0, 50.0, 120, 12.0 | 虚像融合器 / imaginary fusor |
| `machines.metalFormer.{buffer,bandwidth,processTime,energyPerTick}` | 3000.0, 50.0, 60, 13.3 | 金属成型机 / metal former |
| `machines.energyBridge.{bufferIf,bandwidth}` | 2000, 100.0 | 能源桥，缓存 = bufferIf × fePerIf FE / bridge, stores bufferIf·fePerIf FE |
| `machines.abilityInterferer.{buffer,bandwidth,costPerRangeSquared}` | 10000, 50, 1.0 | 每 10 tick 花费 = ⌈系数 × 范围²⌉ / cost per 10 ticks = ⌈k·range²⌉ |

`processTime` 单位为 tick，`energyPerTick` 单位为 IF/t；单次耗能高于机器缓存的配方永远无法完成。机器界面的能量条通过原版容器数据同步，数值超过 32767 时显示会出错。

`processTime` is in ticks and `energyPerTick` in IF per tick; a recipe whose per-tick cost exceeds the machine buffer never finishes. Machine screens sync energy through vanilla container data, so values above 32767 display incorrectly.

## 配方可选字段 / Optional recipe fields

`academy:metal_forming` 与 `academy:imag_fusor`（及旧 id `academy:imag_fusing`）配方可写：

- `"time"`：整数，1..72000 tick；缺省时用 `processTime`。
- `"energy"`：小数，0..1000000 IF/t；缺省时用 `energyPerTick`。

`academy:metal_forming` and `academy:imag_fusor` (and the legacy `academy:imag_fusing`) recipes accept:

- `"time"`: integer ticks, 1..72000; absent = the machine's `processTime`.
- `"energy"`: IF per tick, 0..1000000; absent = the machine's `energyPerTick`.

现有配方 JSON 无需修改。/ Existing recipe JSON stays valid unchanged.

```json
{
  "type": "academy:metal_forming",
  "mode": "plate",
  "input": { "tag": "c:ingots/iron" },
  "output": { "id": "minecraft:iron_bars", "count": 4 },
  "time": 200,
  "energy": 20
}
```

```json
{
  "type": "academy:imag_fusor",
  "input": { "item": "minecraft:diamond" },
  "phaseLiquid": 3000,
  "output": { "id": "minecraft:nether_star" },
  "time": 600
}
```
