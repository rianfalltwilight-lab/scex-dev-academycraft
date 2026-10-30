# 客户端第二轮逐文件审计（2026-10-25）

工作树：`academycraft-deep-audit-0.0.29-20261025`；固定基线 `ef1617b`。本域覆盖 `client/**`、`utils/RenderUtils`、`tutorial/**`、`terminal/**`、`worldgen/**`、`world/fluid/**`、`world/fluidtype/**`，共 **127 个 Java 文件**。逐文件路径清单为 [client-secondary-reviewed-files.txt](client-secondary-reviewed-files.txt)，下表提供每个文件的审阅重点。计数含本轮 root 新增的 3 个 renderer。

## 方法与证据边界

逐文件读取方法体并追踪客户端注册、事件订阅、会话清理、菜单数据和资源生命周期；对照固定 AC 上游 `7b1401cd420bd6888a2b9d8db5cd8a69fe314bb9`、Extra 上游 `d66a190` 及本地 NeoForge 21.1.248/MC 1.21.1 sources。检查未把“搜到某词”“编译通过”或“截图存在”等同于行为通过。本代理未启动 Gradle、客户端、服务器或生产环境；编译及 Vicerach/Monarch 真实运行由根协调执行。

**状态：本域实现已冻结；Monarch client-r2 实际客户端 19 项探针通过、正常保存退出，根已查看五个 renderer 截图。** 证据收件位置为 `evidence/audit-20261025/monarch-client-r2`，真实按键六 case 验收另行记录。 新的 `ClientSecondaryAuditChecks` 由 `ClientDeepAuditGate` 调用，包含真实客户端对象、实际 render/click/scroll/JOrbis 构造；没有仅比较源码字符串来宣称完成。

## 已确认并修复

|优先级|位置/原问题|修复与实际断言|
|---|---|---|
|P1|`SkillInputClientState` 静态 Connection 在登出后仍保留 transport/listener|新增 `disconnect`，只在实际 logout 释放 connection/lastSession/revision；维度 reset 保留拒旧 ack，sequence 保持进程单调。probe 调用实际 logout listener 并覆盖跨维旧 ack 负例。|
|P1|`SkillTreeGui.tick` 每 tick 将旧客户端 BE 余额写回，抵消正确服务端结果；删除全量能量广播后更明显|网络代理新增 owner-only、原 nonce 校验、5 tick dirty-only `DevEnergyPacket`；本域客户端 probe 验证 24 字节 codec、旧 nonce/spent 拒绝与旧 BE=3 时 tick 不覆盖服务器 12000。服务端实际协商通道测试归网络代理。|
|P2|`CatEngineRender` 90 作为弧度、每帧改 BE.rH，速度取决于 FPS 且未按真实 generation|恢复上游双面 quad/朝相机；BE 每 client tick 依据 thisTickGeneration 推进角度，render 仅 partial 插值；变化 generation 最多每10 tick广播。probe 连续144次真实渲染不改 BE/pose，500/0/200 IF/t 对应250/0/100度/tick。|
|P2|AIM GUI 余额只取初始/config响应|实际标签始终取 menu ContainerData；probe 输入37→12400→8，严格要求文本37/10000 IF→10000/10000 IF→8/10000 IF，包含容量钳制及下一次减少刷新。|
|P2|ScreenMaskOverlay 9参 blit 把目标窗口尺寸当源尺寸，遮罩裁剪/重复|11参完整源512×288映射到目标窗口；追踪实际 blit 实参。|
|P2|AbilityHudOverlay 总高度按错误行距计算，最下槽越界|总高度与实际30px步长统一；真实绘制四图标最大向下偏移仍在窗口内。|
|P2|外部媒体点击错误要求唱片解锁|已索引外部轨道可播放；内置未解锁继续拒绝。probe 调用真实 mouseClicked/play，音量0避免无关异步文件播放。|
|P2|外部文件在扫描后损坏，JOrbis 构造失败不自动关输入流|共享工厂在 IOException/RuntimeException 关闭输入并保留suppressed close错误；坏 Ogg 实际构造失败释放，合法内置 Ogg 保持打开直到 AudioStream.close。|
|P2|SettingsAppGui 裁剪区外隐藏行仍命中并更改设置|渲染 hover 与实际 click 都验证 viewport；同一 SHOW_HUD 行位于标题后不响应、移入视口可切换。|
|P2|SkillSlotGui 长列表超过窗口后尾部技能永远不可达|限制可见行数、增加滚轮偏移/指示条；实际320×180窗口首/尾条目命中和返回首项。|
|P2|终端只读 SkillTree 详情被 devType==null/readOnly 早退拦住|网络代理将只读详情放通并限制学习动作，空 devType 只显示不满足机器条件；本域实际 click/render 覆盖已学/未学详情及无学习按钮。|
|测试契约|ExtraItemTextureVisualGate 固定42资源，新增waterman后必失败|保留原42个具体路径，另要求waterman路径存在，逐一哈希检查全部manifest项，允许将来合法新增。|

共同核验但由 root 实施：RenderUtils 私有 ByteBuffer 释放、ChargingHud 5 tick 插值、能力 loop 死亡清理、ExternalMedia.reload 旧 cover 释放、PhaseLiquid atlas ID、Railgun ArcData 帧率寿命、新 Aero/Cruise/Waterman renderer。这些以 root 的代码与真实测试记录为准。

Monarch fallback `runs/client-r1` 已实际启动并正常存盘退出，前置液体 atlas、动态纹理释放、railgun 帧率、session 和穿透共享 probe 通过。AIM probe 在输入 12400 时失败：机器容量固定 10000 IF，menu 正确限制了超容量输入，旧 probe 却错误要求显示 12400。此为测试预期错误；现保留原三步输入，分别检查 menu 值和完整标签文本，并在失败时记录实际文本。生产 GUI 未因此修改；修正后的三步完整文本断言已在 Monarch client-r2 真实通过。

## 后续核验与保留边界

下列四项在集成阶段完成修复，并由 Monarch client-r2 的真实客户端探针验证；对应根台账 C10–C13：

- `LocationTeleportGui` 行数随高度变化，窄屏单列；输入、添加、分页互不重叠。320×180实际绘制/点击/滚轮覆盖八条记录，最后页可达。
- `TutorialAppGui` 返回按钮 hover 与 click 共用缩放坐标函数；0.5x、2x实际按钮绘制/点击与范围外负例通过。
- `TutorialMdParser` 关闭代码块恢复原 section；非空/空代码块之后正文保留。现包无 fenced code block，此修复覆盖已有自定义内容解析能力。
- 穿透预览与服务端共用 `PenetrateTargeting`，正负坐标、完整碰撞箱、薄玻璃片和第二堵墙前 lastSafe 在真实 ClientLevel 中验证。

以下为明确未接入现役路径的旧实现，不当作已实现现役功能的证据：
- 旧 `client/effect` 嵌套 renderer 有未完善部分：ElectroArc 以成对顶点喂 QUADS、RippleMark 预先取得 pose matrix 而未应用位移等。已追踪到这些旧路径未进入现役注册/调用；不为完成覆盖而把旧空壳重新接入。现役效果位于 `client/renderer`/`client/render` 与真实实体注册链。
- `RenderUtils` 未使用的旧 renderPart/renderQuad/addVertex helper 不能当作现役完整实现的证据；本轮不注册或扩展退役入口。

## 可选性能项（OPT，未改）

- MediaPlayerApp 在 scissor 内仍遍历最多256个媒体行；ExternalMedia 首次索引同步扫描本地文件，可能造成一次 UI 停顿；边界已有限制，未编造无限流风险。
- Matrix/Wind 每帧少量 quad/RNG 临时对象；LegacyAbilityPresentation 的 ThunderClap 最多48束分别flush，可在不改变透明排序前提下另测批处理。
- GuiUtils 长文本 wrap 的 substring/font.width 会重复扫描；当前内容有限，未据此扩展全新文本缓存框架。

## 已排除的误报

- WindGenFanRender 的可见性由服务端 working→visualFanVisible 驱动，不能只看 render 内旋转就判断停机仍转。
- MachineLoop Key 虽持有 ClientLevel，但 reset/世界切换会停止并清空；能力音频也有死亡/session清理。
- AcademyBaseUI 的空 renderBg、机器 GUI 的空 renderLabels 是父类/现代绘制入口委托，不能只搜空方法判作空壳。
- 六个终端 App 路由均有实际画面/操作；教程资源、纹理尺寸与封面缓存均有 resource reload 或 session reset 入口。
- GuiGraphics scissor 使用屏幕坐标，不随 pose 自动缩放；教程显式屏幕坐标裁剪正确，未错误修改。

## 逐文件清单

路径相对 `src/main/java/com/mohistmc/academy/`；“审阅”指源码与调用链覆盖，并不代表该文件每条分支均已实跑。

|文件|行数|审阅记录|
|---|---:|---|
|`client/ChargePresentationState.java`|35|核对 5 tick 服务端快照与游戏 tick/partial 插值；root 修复并另有单测。|
|`client/ChargingHudOverlay.java`|121|充能 HUD 同步、取消、会话清理；不使用 render 次数推进计时。|
|`client/ClientPacketBridge.java`|141|命中标记/充能反馈生命周期与 reset；未发现另一个跨连接持有点。|
|`client/ClientSessionState.java`|30|逐个跟踪 reset 调用；换维保留输入 revision、登出另行 disconnect。|
|`client/KeyInputHandler.java`|556|按键边沿、按住/抬起、会话重试、切屏/失焦清理调用链。|
|`client/LegacyVecmanipClientController.java`|151|输入状态、客户端视觉与 reset 调用；由 KeyInputHandler 清理。|
|`client/MediaPlayerManager.java`|279|播放/暂停/停止、异步 stream 所有权；修复 JOrbis 构造失败输入流泄漏。|
|`client/SkillInputClientState.java`|41|修复登出持有 Connection；保留同连接跨维拒旧 ack 与单调 sequence。|
|`client/TerminalInstallProgress.java`|59|安装进度事件与断线 reset；不依赖服务端外部对象。|
|`client/ThunderClapMovementController.java`|45|客户端移动输入/charging 状态检查；服务端仍为最终结算。|
|`client/block/entity/model/CatEngineModel.java`|39|旧猫模型定义仍在，现役 renderer 已改上游双面 quad；未重新注册新玩法。|
|`client/block/entity/render/CatEngineRender.java`|48|对照 1.0.7 RenderCatEngine：quad、朝向、degrees、实际输出游戏 tick 动画；渲染不修改 BE。|
|`client/block/entity/render/MatrixRender.java`|116|模型/盾片、旋转与 buffer/pose 生命周期；每帧有有界 quad 收集，列 OPT。|
|`client/block/entity/render/PhaseLiquidRender.java`|121|材质 atlas sprite 与液面/光照；root 同步修复 fluid atlas ID。|
|`client/block/entity/render/PhaseLiquidVisualMath.java`|14|液面高度与渲染数值边界。|
|`client/block/entity/render/WindGenFanRender.java`|95|可见性依赖 BE working 状态；扇叶旋转不是停机后永动误报。|
|`client/block/gui/AbilityInterfererGui.java`|215|修复余额读取初始/config 快照；实际 renderLabels 改用实时 ContainerData。|
|`client/block/gui/BaseNodeGui.java`|300|菜单身份、响应 revision、编辑草稿与名称/密码 ack，不以旧 BE 反写 UI。|
|`client/block/gui/DevAdvancedGui.java`|137|核对父类委托、槽位/ContainerData 显示、关闭/返回、机器与无线页面切换。|
|`client/block/gui/DevNormalGui.java`|135|核对父类委托、槽位/ContainerData 显示、关闭/返回、机器与无线页面切换。|
|`client/block/gui/EnergyBridgeGui.java`|58|核对父类委托、槽位/ContainerData 显示、关闭/返回、机器与无线页面切换。|
|`client/block/gui/ImagFusorGui.java`|80|核对父类委托、槽位/ContainerData 显示、关闭/返回、机器与无线页面切换。|
|`client/block/gui/MatrixGui.java`|283|初始化/运行两态、资料字段、同步与条目命中。|
|`client/block/gui/MetalFomerGui.java`|150|核对父类委托、槽位/ContainerData 显示、关闭/返回、机器与无线页面切换。|
|`client/block/gui/NodeAdvancedGui.java`|11|核对父类委托、槽位/ContainerData 显示、关闭/返回、机器与无线页面切换。|
|`client/block/gui/NodeBasicGui.java`|11|核对父类委托、槽位/ContainerData 显示、关闭/返回、机器与无线页面切换。|
|`client/block/gui/NodeStandardGui.java`|11|核对父类委托、槽位/ContainerData 显示、关闭/返回、机器与无线页面切换。|
|`client/block/gui/PhaseGenGui.java`|50|核对父类委托、槽位/ContainerData 显示、关闭/返回、机器与无线页面切换。|
|`client/block/gui/SolarGenGui.java`|87|核对父类委托、槽位/ContainerData 显示、关闭/返回、机器与无线页面切换。|
|`client/block/gui/WindBaseGui.java`|64|核对父类委托、槽位/ContainerData 显示、关闭/返回、机器与无线页面切换。|
|`client/block/gui/WindMainGui.java`|45|核对父类委托、槽位/ContainerData 显示、关闭/返回、机器与无线页面切换。|
|`client/effect/ElectroArcEntity.java`|332|核对旧实体/嵌套 renderer；与现役注册调用链区分，未注册旧实现不冒充已完成功能。|
|`client/effect/MeltdownBeamEntity.java`|93|核对旧实体/嵌套 renderer；与现役注册调用链区分，未注册旧实现不冒充已完成功能。|
|`client/effect/RippleMarkEntity.java`|126|核对旧实体/嵌套 renderer；与现役注册调用链区分，未注册旧实现不冒充已完成功能。|
|`client/effect/ShieldEffectEntity.java`|69|核对旧实体/嵌套 renderer；与现役注册调用链区分，未注册旧实现不冒充已完成功能。|
|`client/effect/SpriteEffectEntity.java`|232|核对旧实体/嵌套 renderer；与现役注册调用链区分，未注册旧实现不冒充已完成功能。|
|`client/effect/WaveEffectEntity.java`|152|核对旧实体/嵌套 renderer；与现役注册调用链区分，未注册旧实现不冒充已完成功能。|
|`client/entity/CoinRenderer.java`|96|完整读取构造/tick/render/资源引用；核对顶点格式、RenderType切换、pose与生命周期。|
|`client/gate/ConcurrentMenuClientGate.java`|139|完整读取默认关闭驱动：property/阶段/超时/实际输入/截图回执/退出；仅隔离 fixture 启用。|
|`client/gate/ExtraItemTextureVisualGate.java`|353|原 42 路径与新增 waterman 必须存在，所有 manifest 项逐一 SHA 验证；替换过时固定数量 42。|
|`client/gate/ExtraSkillVisualGate.java`|252|完整读取默认关闭驱动：property/阶段/超时/实际输入/截图回执/退出；仅隔离 fixture 启用。|
|`client/gate/MachineVisualGate.java`|1643|完整读取默认关闭驱动：property/阶段/超时/实际输入/截图回执/退出；仅隔离 fixture 启用。|
|`client/gate/MatrixSsidClientGate.java`|140|完整读取默认关闭驱动：property/阶段/超时/实际输入/截图回执/退出；仅隔离 fixture 启用。|
|`client/gate/MatrixVisualGate.java`|81|完整读取默认关闭驱动：property/阶段/超时/实际输入/截图回执/退出；仅隔离 fixture 启用。|
|`client/gate/NodeConfigClientGate.java`|413|完整读取默认关闭驱动：property/阶段/超时/实际输入/截图回执/退出；仅隔离 fixture 启用。|
|`client/gate/RecheckClientPersistenceProbe.java`|54|完整读取默认关闭驱动：property/阶段/超时/实际输入/截图回执/退出；仅隔离 fixture 启用。|
|`client/gate/RecheckSessionClientGate.java`|192|完整读取默认关闭驱动：property/阶段/超时/实际输入/截图回执/退出；仅隔离 fixture 启用。|
|`client/gui/AbilityHudOverlay.java`|136|修复四槽实际行高与底边约束，真实 drawOutline probe。|
|`client/gui/AboutAppGui.java`|234|完整读取布局/输入/绘制/生命周期；已核对有界状态、pose push/pop 与实际调用。|
|`client/gui/AcademyBaseUI.java`|1189|菜单增量/无线列表分页、request correlation、关闭 cache；空 renderBg 由现代路径承担。|
|`client/gui/AcademyColors.java`|48|完整读取布局/输入/绘制/生命周期；已核对有界状态、pose push/pop 与实际调用。|
|`client/gui/AcademyScreen.java`|69|完整读取布局/输入/绘制/生命周期；已核对有界状态、pose push/pop 与实际调用。|
|`client/gui/CPBarOverlay.java`|208|完整读取布局/输入/绘制/生命周期；已核对有界状态、pose push/pop 与实际调用。|
|`client/gui/DataTerminalGui.java`|301|6 个内置 App 实际打开目标；未发现空路由。|
|`client/gui/DevMachineLayout.java`|81|完整读取布局/输入/绘制/生命周期；已核对有界状态、pose push/pop 与实际调用。|
|`client/gui/FreqTransmitterGui.java`|264|完整读取布局/输入/绘制/生命周期；已核对有界状态、pose push/pop 与实际调用。|
|`client/gui/GuiRenderHelper.java`|37|完整读取布局/输入/绘制/生命周期；已核对有界状态、pose push/pop 与实际调用。|
|`client/gui/GuiUtils.java`|75|文字换行/图标渲染；长文本 substring/font.width 代价为局部 OPT。|
|`client/gui/HudCustomizeGui.java`|167|完整读取布局/输入/绘制/生命周期；已核对有界状态、pose push/pop 与实际调用。|
|`client/gui/InfoArea.java`|248|完整读取布局/输入/绘制/生命周期；已核对有界状态、pose push/pop 与实际调用。|
|`client/gui/LegacyVectorWaveOverlay.java`|101|完整读取布局/输入/绘制/生命周期；已核对有界状态、pose push/pop 与实际调用。|
|`client/gui/LocationConsentScreen.java`|21|同意/拒绝/关闭动作；不将一般鼠标事件当作服务端授权。|
|`client/gui/LocationTeleportGui.java`|241|会话/响应/分页/重命名；固定 5 行在小窗口的布局风险尚未实跑。|
|`client/gui/MediaPlayerAppGui.java`|437|修外部文件错误要求服务端唱片解锁；内置媒体仍拒绝未解锁点击。|
|`client/gui/MediaPlayerHudOverlay.java`|47|完整读取布局/输入/绘制/生命周期；已核对有界状态、pose push/pop 与实际调用。|
|`client/gui/NotifyOverlay.java`|129|完整读取布局/输入/绘制/生命周期；已核对有界状态、pose push/pop 与实际调用。|
|`client/gui/RegularMachineLayout.java`|65|完整读取布局/输入/绘制/生命周期；已核对有界状态、pose push/pop 与实际调用。|
|`client/gui/ScreenMaskOverlay.java`|104|修复 9 参 blit 将窗口尺寸当源采样尺寸；改 11 参完整 512x288。|
|`client/gui/SettingsAppGui.java`|247|修复 clip 外隐藏行命中；真实 render/click 正反 probe。|
|`client/gui/SkillSlotGui.java`|427|修复长下拉尾部不可达；窗口内可见行、滚轮偏移、滚动指示；真实小窗口 probe。|
|`client/gui/SkillTreeGui.java`|1116|识别旧 BE 覆盖权威余额与只读详情早退；网络代理修 DevEnergy/详情，本域提供客户端真实 probe。|
|`client/gui/TutorialAppGui.java`|660|正文/配方渲染、scissor、滚动、缩放；返回 hover 坐标差异列待复现。|
|`client/gui/TutorialMdParser.java`|272|markdown 状态/资源缓存；代码块后 section 恢复缺口，现包无代码块，列边界。|
|`client/gui/WirelessDisplayName.java`|13|完整读取布局/输入/绘制/生命周期；已核对有界状态、pose push/pop 与实际调用。|
|`client/gui/tutorial/RecipeViews.java`|386|配方视图/材料/索引缓存；切换选择时查询，未见逐帧全世界扫描。|
|`client/jei/AcademyJeiPlugin.java`|149|软联动注册、显示/转移、能量子类型；额外视觉驱动仅显式 property 开启。|
|`client/jei/ExtraJeiTransferGate.java`|404|完整读取 opt-in JEI 验证驱动，真实配方/handler/网络/截图与 fixture 断言；不等同已实跑。|
|`client/jei/ExtraJeiVisualGate.java`|117|完整读取 opt-in JEI 验证驱动，真实配方/handler/网络/截图与 fixture 断言；不等同已实跑。|
|`client/jei/ImagFusorJeiCategory.java`|41|配方布局、显示数量/流体与父类 buffer 使用；与实际 recipe 输入保持对应。|
|`client/jei/MetalFormerJeiCategory.java`|62|配方布局、显示数量/流体与父类 buffer 使用；与实际 recipe 输入保持对应。|
|`client/media/ExternalMediaManager.java`|302|本地资源约束、metadata、封面注册；root 修 reload 释放旧封面；同步扫描列 OPT。|
|`client/media/ExternalOggInspector.java`|102|有限 Ogg 扫描、错误拒绝、文件流关闭；与实际播放 TOCTOU 区分。|
|`client/particle/AcademyTextureParticle.java`|49|完整读取构造/tick/render/资源引用；核对顶点格式、RenderType切换、pose与生命周期。|
|`client/particle/SilbarnFragmentParticle.java`|53|完整读取构造/tick/render/资源引用；核对顶点格式、RenderType切换、pose与生命周期。|
|`client/render/LegacyAbilityPresentationRenderer.java`|490|各技能视觉分支、pose/buffer、输入预览；穿透传送负坐标/终点差异列待运行验证，雷击多 flush 列 OPT。|
|`client/render/MagManipBlockRenderer.java`|75|完整读取构造/tick/render/资源引用；核对顶点格式、RenderType切换、pose与生命周期。|
|`client/render/MineDetectBatchRenderer.java`|102|一次批次顶点提交、客户端矿物结果/移除与会话状态；无本域新确定 bug。|
|`client/render/OreHighlightRenderer.java`|77|有限结果高亮 buffer 与 pose；不自行发起服务端世界扫描。|
|`client/render/PsychoProjectileRenderer.java`|60|完整读取构造/tick/render/资源引用；核对顶点格式、RenderType切换、pose与生命周期。|
|`client/renderer/AeroProjectileRenderer.java`|112|root 新增真实实体渲染；POSITION_COLOR 与 QUADS 顶点匹配，单 buffer 不跨类型复用。|
|`client/renderer/BloodSplashRenderer.java`|56|完整读取构造/tick/render/资源引用；核对顶点格式、RenderType切换、pose与生命周期。|
|`client/renderer/BloodSprayDecalRenderer.java`|35|完整读取构造/tick/render/资源引用；核对顶点格式、RenderType切换、pose与生命周期。|
|`client/renderer/CruiseBombRenderer.java`|21|root 新增上游蓝色球体，复用同一合法 RenderType/顶点格式。|
|`client/renderer/LegacyFieldEffectRenderer.java`|77|完整读取构造/tick/render/资源引用；核对顶点格式、RenderType切换、pose与生命周期。|
|`client/renderer/LiquidShadowRenderer.java`|29|root 新增原 Waterman 贴图与僵尸抬手 humanoid；不继承 Drowned 叠层。|
|`client/renderer/MdBallRenderer.java`|60|完整读取构造/tick/render/资源引用；核对顶点格式、RenderType切换、pose与生命周期。|
|`client/renderer/MeltdownBarrageRenderer.java`|88|完整读取构造/tick/render/资源引用；核对顶点格式、RenderType切换、pose与生命周期。|
|`client/renderer/MeltdownBeamRenderer.java`|151|完整读取构造/tick/render/资源引用；核对顶点格式、RenderType切换、pose与生命周期。|
|`client/renderer/PlasmaOrbRenderer.java`|87|完整读取构造/tick/render/资源引用；核对顶点格式、RenderType切换、pose与生命周期。|
|`client/renderer/RailgunBeamRenderer.java`|308|发现 ArcData 按帧缩短寿命并交 root 修为 tick 时间；client-r2 真实帧率探针通过。|
|`client/renderer/ShieldEffectRenderer.java`|52|完整读取构造/tick/render/资源引用；核对顶点格式、RenderType切换、pose与生命周期。|
|`client/renderer/SilbarnRenderer.java`|68|完整读取构造/tick/render/资源引用；核对顶点格式、RenderType切换、pose与生命周期。|
|`client/renderer/StormWingVisualRenderer.java`|88|完整读取构造/tick/render/资源引用；核对顶点格式、RenderType切换、pose与生命周期。|
|`client/sound/AbilityLoopSoundManager.java`|163|root 修死亡/世界切换停止；按技能会话拥有 loop，不残留已死玩家。|
|`client/sound/ClientSoundUtils.java`|25|音效播放入口与客户端限定。|
|`client/sound/MachineLoopSoundManager.java`|108|Key 持 ClientLevel 但 world/reset 主动清理；仅已加载有效 BE 维持循环。|
|`client/sound/SkillSoundConfigHandler.java`|28|配置变化停止/重开条件，无独立长期句柄。|
|`terminal/AppRegistry.java`|73|完整读取终端注册/媒体/数据 codec；对应现役 GUI 或附件入口，未发现空壳路由。|
|`terminal/BuiltinApp.java`|54|完整读取终端注册/媒体/数据 codec；对应现役 GUI 或附件入口，未发现空壳路由。|
|`terminal/MediaTrack.java`|44|完整读取终端注册/媒体/数据 codec；对应现役 GUI 或附件入口，未发现空壳路由。|
|`terminal/MediaTrackRegistry.java`|60|完整读取终端注册/媒体/数据 codec；对应现役 GUI 或附件入口，未发现空壳路由。|
|`terminal/TerminalApp.java`|24|完整读取终端注册/媒体/数据 codec；对应现役 GUI 或附件入口，未发现空壳路由。|
|`terminal/TerminalData.java`|47|完整读取终端注册/媒体/数据 codec；对应现役 GUI 或附件入口，未发现空壳路由。|
|`terminal/TerminalDataCodec.java`|63|完整读取终端注册/媒体/数据 codec；对应现役 GUI 或附件入口，未发现空壳路由。|
|`tutorial/ACTutorial.java`|129|完整读取教程注册/条件/视图/解锁与内容 cache；reload/reset 调用链已核对。|
|`tutorial/Condition.java`|46|完整读取教程注册/条件/视图/解锁与内容 cache；reload/reset 调用链已核对。|
|`tutorial/Conditions.java`|43|完整读取教程注册/条件/视图/解锁与内容 cache；reload/reset 调用链已核对。|
|`tutorial/TutorialInit.java`|96|完整读取教程注册/条件/视图/解锁与内容 cache；reload/reset 调用链已核对。|
|`tutorial/TutorialRegistry.java`|51|完整读取教程注册/条件/视图/解锁与内容 cache；reload/reset 调用链已核对。|
|`tutorial/TutorialUnlocks.java`|62|完整读取教程注册/条件/视图/解锁与内容 cache；reload/reset 调用链已核对。|
|`tutorial/ViewGroup.java`|29|完整读取教程注册/条件/视图/解锁与内容 cache；reload/reset 调用链已核对。|
|`tutorial/ViewGroups.java`|79|完整读取教程注册/条件/视图/解锁与内容 cache；reload/reset 调用链已核对。|
|`utils/RenderUtils.java`|145|root 修 NativeImage/ByteBuffer 释放；资源 reload 纹理尺寸 cache 清理；未消费旧 helper 另记退役。|
|`world/fluid/PhaseFluid.java`|132|液体状态、方块交互与既有原版流体生命周期。|
|`world/fluidtype/PhaseLiquidType.java`|68|root 修 atlas ID 不含 textures/ 与 .png；仍/流两材质齐备。|
|`worldgen/AcademyPlacementModifiers.java`|73|Placement codec/注册路径，无独立运行状态 cache。|
|`worldgen/AcademyWorldGen.java`|84|注册/生成条件与分布调用链；root 负责世界生成实现调整，不跨域修改。|
