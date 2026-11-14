# 客户端、资源与构建审计

本记录对照冻结基线 ef1617b 与实际 Minecraft 1.21.1 / NeoForge 21.1.248 源码。逐文件复核及补充问题另见 client-secondary-audit.md。源码扫描、JUnit 和真实客户端运行分别列证据，不能用构建成功证明视觉效果。

| ID | 等级 | 基线问题 | 已实现修改 | 验证入口 |
| --- | --- | --- | --- | --- |
| C01 | P1 | RenderUtils.renderText 每次创建 ByteBufferBuilder，却从不 close；原生 malloc 不由 Java GC 回收 | 使用 GuiGraphics 所拥有的 BufferSource 并 flush | ClientDeepAuditGate 重复真实文字绘制并跟踪 GUI buffer 获取；旧路径不会获取该 buffer |
| C02 | P1 | 蓄力 HUD 完全依赖逐 tick 进度包，不能配合服务端节流 | ChargePresentationState 按未暂停客户端 tick 外推、partial tick 平滑、收到包校正，终止清空；超过100个游戏tick无包失效 | ChargePresentationStateTest 正常推进、回退校正、上限、终止与超时；真实客户端死亡测试 |
| C03 | P1 | 持续音效只检查 removed，死亡到终止包之间仍持有/播放循环 | 创建与 tick 均检查 isAlive；死亡清 HUD，既有会话结束清全部音效 | 客户端真实循环先存在，死亡后实际 owner 引用与 HUD 均清空 |
| C04 | P1 | ExternalMediaManager.invalidateForResourceReload 清掉 cover ID 集合，却没有释放动态纹理；Minecraft TextureManager.reload 不删除 DynamicTexture | 清登记前逐个 TextureManager.release | 真实 NativeImage/DynamicTexture 正向注册、失效后 pixels=null 与登记集合为空 |
| C05 | P2 | PhaseLiquidType 把 textures/block/black.png 当作 block atlas sprite ID，产生缺失纹理 | 静态/流动使用 academy:block/black；全屏覆盖仍使用完整资源路径 | 真实客户端查 atlas sprite 不为 missing texture，静态/流动一致 |
| C06 | P2 | 日文仅299键，英/中各560键，蓄力提示为中英混合硬编码；既有日文精神课程漏20%数值 | 补齐日文261键、三语充能提示与5个实体名，并补原数值 | AuditLanguageParityTest 比较三语全键集合、占位符与损坏字符；新增后各573键 |
| C07 | P2 | Railgun ArcSegment.life 在每次render递减，寿命随FPS变化 | life 由 entity.tickCount 推导，同一tick多次绘制不变 | 客户端144次同tick调用不衰减，再推进10tick恰减10 |
| C08 | P2 | 波纹列表只等下一次渲染清空，未纳入统一会话拆卸 | ClientSessionState.reset 显式 reset 波纹 | 会话调用链审阅 |
| C09 | P1 | 新独立probe JAR若与主模组同包，javac通过而NeoForge模块解析失败 | probe统一独立regression包；verifyAuditPackageIsolation 纳入check/JAR | Vicerach gt-r2捕获split-package；gt-r3已实际启动并执行424项，模块问题已消除 |
| C10 | P2 | Tutorial 返回按钮 hover 未乘缩放，和实际点击范围不同 | 绘制与点击统一现有 frame 坐标换算 | 真实按钮绘制/点击的0.5x、2x与范围外负例 |
| C11 | P2 | TutorialMdParser 结束代码块后进入 NONE，后续正文丢失 | 退出代码块恢复原 section，删除无用途 tableBuffer | 实际 parser 的非空/空代码块前后正文均保留 |
| C12 | P2 | LocationTeleportGui 固定5行在小窗口覆盖页脚；正常宽度下分页箭头也与添加按钮/输入框重叠 | 行数随高度、窄屏单列，页脚按输入/添加/分页分配互不重叠区域 | 真实320×180绘制、点击/滚轮到最后页，八条记录均可达 |
| C13 | P2 | 穿透标记用负坐标截断及未经安全检查的最终游标，与服务器落点不一致 | 与服务器共享 PenetrateTargeting；完整玩家碰撞箱、lastSafe、CP倍率上限一致 | 真实ClientLevel正负坐标、薄玻璃片和第二堵墙前安全点；同tick还原临时块 |

## 原版表现与资源

新 AeroProjectileRenderer 使用单一 POSITION_COLOR / QUADS buffer 绘制上游球面、月牙及尾迹；CruiseBombRenderer 为上游半径0.1、蓝色、alpha0.5球体。没有交替取得不同RenderType后回写旧consumer。LiquidShadowRenderer 使用僵尸形状及抬手姿态，独立水人实体不再借用Drowned行为或网络身份；水人PNG与固定ExtraAcC快照逐字节一致，纳入43张原始资源的SHA-256验证。

既有 MdBall、Shield、Plasma、Railgun、PhaseLiquid、Coin 和 MagManip renderer 检查了消费者切换与矩阵栈控制流：各不同类型的顶点均在取下一个consumer之前完成。正常分支未发现push/pop失配；这不等于保证任意第三方渲染回调抛异常时客户端仍可继续运行。

本地测试用 ClientDeepAuditGate 位于独立 auditTest JAR，仅在显式属性启用时工作；创建新的隔离平坦世界，实际调用字体/纹理/实体renderer/音频生命周期，保留截图，结束时正常保存退出。直接构造的客户端展示实体仅证明renderer路径；服务器飞行、碰撞与费用另由真实GameTest验证。

ExtraSkillVisualGate 另要求三个空气弹、巡航弹与水人实际出现在客户端实体跟踪表，并记录实体ID/位置；它通过正常KeyInputHandler注入按键，服务器验证实际费用和命中。PerfectPaper环境用例改为先持纸20tick不训练，再真实调用纸飞机物品入口付费训练，不能沿用已修除的免费经验假设；水人观察改为实际独立LiquidShadowEntity。

根补读的工具/注册/驱动见root-reviewed-files.txt。RandUtils.rangei唯一现役调用为Tutorial分配1000..19000御坂号，范围恒正；Resources效果序列当前调用n=3。VisualLightning只发客户端动画包，不把闪电加入服务器产生铜/避雷针副作用。EffectHelper仅发送视觉实体/粒子，实际伤害由技能入口结算。

## 世界生成与流体

AcademyPlacementModifiers 的概率使用 nextDouble<0.3，没有 nextInt(0)；开关从服务器配置读取。四矿脉（reso/constraint/crystal/imagsil）按 1.12.2 WorldGenMinable 恢复为大小 9/12/12/11、每区块 8/8/12/8 次，实际生成为datapack+vanilla OreFeature/LakeFeature，不存在自定义跨块同步查找循环。删除了不做任何工作的 ServerAboutToStart 空订阅，保留实际datapack注册。PhaseFluid保持源液量8、流动LEVEL、非无限源和容器掉落路径。

## 构建与兼容

Gradle wrapper使用已缓存9.2.1，Java21；JUnit固定5.11.4并显式声明Maven Central。构建仍固定NeoForge21.1.248，运行声明放宽为[21.1,21.2)，按同Minecraft/API系列给边界，不把本轮测试patch号当唯一支持版本。其他21.1小版本未逐一实跑。新增实体要求两端升级，网络协议v19，能力数据版本v4。

实际服务端、客户端结果完成后在最终验证回执记录；本文件不会把待运行项目提前标为通过。
