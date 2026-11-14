# 整合包机器数值 / Modpack Machine Numbers

服务端配置 `config/academy-server.toml`（存档内为 `serverconfig/`）的 `[machines]` 段控制机器能量、缓存与加工数值。默认值与 AcademyCraft 1.0.7 一致。数值在使用时读取，修改并重载配置后对已放置的机器生效；若调低容量，机器内超出的能量会在读档或下一个 tick 被截到新容量。

The `[machines]` section of the server config (`academy-server.toml`, per-world under `serverconfig/`) sets machine energy, buffers and processing. Defaults equal AcademyCraft 1.0.7. Values are read when used, so a config reload applies to placed machines. If a capacity is lowered, stored energy above it is clamped to the new capacity on load or on the next tick.

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
