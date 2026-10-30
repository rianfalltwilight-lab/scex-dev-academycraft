# 四类基础能力固定上游数值对照

基线：`D:/Minecraft-Projects/upstream-cache/AcademyCraft-7b1401c`，完整SHA `7b1401cd420bd6888a2b9d8db5cd8a69fe314bb9`。`a→b`表示熟练度0→1；CP/OL均指运行调用实际参数，冷却单位tick。所有本地值还受既有DynamicSkillRules配置倍率影响，下表列默认原始曲线。与源文件/调用顺序共同核对，未把声明但未使用的局部变量当成实际机制。

| 类别/本地技能 | CP / OL | 伤害、距离与时间 | 固定上游文件（skill目录）/对照结论 |
|---|---|---|---|
| EM arc_gen | 30→70 / 18→11 | 伤害5→9；距离6→15；CD15→5 | java/electromaster/ArcGen.java，一致 |
| EM charging | 每tick3→7；启动OL65→48 | 上游整数输出15→35；距离15 | scala/electromaster/CurrentCharging.scala；当前分支保留既有2.5倍输出，ChargingOutput只累计半单位余数，FE=4IF |
| EM mag_movement | 每tick15→8；启动OL60→30 | 归一化拉力，到达仍持有；exp=max(.005,.0011×距离) | scala/electromaster/MagMovement.scala，一致 |
| EM mag_manip | 投掷140→270 / 35→20 | 速度.5→1；实际伤害10；CD60→40 | java/electromaster/MagManip.java；8→15局部damage未传入实体，保留实际10 |
| EM mine_detect | 1500→1000 / 200→180 | 范围min(28,15→30)，100tick，8400结果，CD900→400 | scala/electromaster/MineDetect.scala，一致；服务端仅owner同步 |
| EM body_intensify | 前40tick20→15；启动OL200→120 | buff系数1.5→2.5；CD900→600；exp.01 | scala/electromaster/BodyIntensify.scala；未使用shuffle结果、预增索引行为保留 |
| EM thunder_bolt | 280→420 / 50→27 | 主伤10→25、AOE6→15；主射线20，AOE球半径8；CD120→50 | scala/electromaster/ThunderBolt.scala，一致 |
| EM railgun | 200→450 / 180→120 | 伤60→110，能量900→2000，半径2，50步/视觉45；CD300→160 | scala/electromaster/Railgun.scala；硬币QTE立即，手持材料蓄20tick |
| EM thunder_clap | 前40tick18→25；启动OL390→252 | 40..60tick；伤36→72 × (1+.2×(ticks−40)/60)；半径15→30；CDticks×(10→6) | scala/electromaster/ThunderClap.scala，一致 |
| MD electron_bomb | 35→80 / 16→13 | 伤6→12，距离15；寿命20或熟练≥.8时5，余2tick发射；CD20→10 | java/meltdowner/ElectronBomb.java，一致；本次补跨维取消 |
| MD scatter_bomb | 前80tick3→6；启动OL80→60 | 20..80每10tick一球共7；伤5→9；200tick自伤6后结束发射；每球exp.001 | scala/meltdowner/ScatterBomb.scala，一致；生命周期中断不跨维补发 |
| MD light_shield | upkeep9→4；启动OL110→60；伤害吸收实际CP5→3/OL50→30 | 容量15→50，触碰伤2→6，时长120→180，CD2ticks→ticks | java/meltdowner/LightShield.java；吸收consume原参数倒置保留 |
| MD meltdowner | 前充能每tick10→15；启动OL200→170 | 20..40蓄力，容忍100；倍率.8→1.2；伤18→50，能量300→700，半径2→3；CD倍率×20×(15→7) | scala/meltdowner/Meltdowner.scala；反射伤.5×(20→50)，独立10格视觉 |
| MD mine_ray_basic | 每tick12→7；启动OL200→150 | 范围10、采掘等级2、速.2→.4；CD40→20 | scala/meltdowner/MineRayBasic.scala+MineRaysBase.scala，一致；本次仅修原state和保护复核 |
| MD mine_ray_expert | 每tick25→15；启动OL300→200 | 范围20、采掘等级5、速.5→1；CD60→30 | scala/meltdowner/MineRayExpert.scala，一致 |
| MD mine_ray_luck | 每tick50→35；启动OL350→300 | 专家参数+fortune3；CD60→30 | scala/meltdowner/MineRayLuck.scala，一致 |
| MD ray_barrage | 450→380 / 300→140 | 主伤25→60，散射10→18；主距离20，扇形yaw55/pitch110；CD100→40 | scala/meltdowner/RayBarrage.scala，一致 |
| MD jet_engine | 实际CP60→50 / OL170→140；启动预检CP170→140 | 伤7→20；位移向量除8，执行16tick；CD60→30 | scala/meltdowner/JetEngine.scala；原consume参数倒置与未clamp外推保留 |
| MD electron_missile | 启动OL200；每tick12→5；攻击60→25 / 9→4 | 伤10→18，半径5→13，时长80→200；最多5球，零起始10tick生成/8tick攻击；实际CD700 | scala/meltdowner/ElectronMissile.scala；clampi(700,400,exp.toInt)实际700 |
| VM dir_shock | 50→100 / 18→12 | 有效7..49tick、超时200；伤7→15，距离3；命中才CD60→20 | scala/vecmanip/DirectedShock.scala，一致 |
| VM ground_shock | 80→150 / 15→10 | 蓄≥5；伤4→6、能量60→120、迭代10→25；CD80→40 | scala/vecmanip/Groundshock.scala；90弧度五道足迹保留 |
| VM vec_accel | 120→80 / 30→15 | 20tick，速度sin(lerp(.4,1,进度))×2.5；俯仰−10°；CD80→50 | scala/vecmanip/VecAccel.scala；熟练≤.5需要2格内地面 |
| VM vec_deviation | 启动OL80→50；每tickCP13→5加5→2.5/OL.5→.2 | 实体消耗15→12不乘difficulty；伤害剩余.6→.1 | scala/vecmanip/VecDeviation.scala；强制耗费夹零，不因余额不足直接免于结算 |
| VM dir_blast | 160→200 / 50→30 | 7..49tick、超时200；伤10→25，主距离4、AOE3；CD80→50 | scala/vecmanip/DirectedBlastwave.scala，一致；本次补方块加载/权限 |
| VM storm_wing | 激活后每tickCP40→25 / OL10→7 | 蓄70→30，严格大于时转ACTIVE；速度(.7或1.2)×(2→3)；CD30→10 | scala/vecmanip/StormWing.scala；只mayfly，不强制vanilla flying |
| VM blood_retro | 280→350 / 55→40 | 伤30→60，距离2，30tick自动释放；步速20tick内.1→.007；CD90→40 | scala/vecmanip/BloodRetrograde.scala，一致 |
| VM vec_reflection | 启动OL350→250；每tick15→11；反射伤耗CP(20→15)×damage | 反伤.6→1.2；实体CPdifficulty×(300→160) | scala/vecmanip/VecReflection.scala；新增/kill与void旁路属于本次明确安全修复 |
| VM plasma_cannon | 启动OL500→400；充能前60→30tick每tick18→25 | 定额伤80→150；爆炸半径12→15；CD1000→600 | scala/vecmanip/PlasmaCannon.scala；早撞仍在destination爆炸保留，冷边界限制见core-audit |
| TP threatening_teleport | 35→100 / 18→10 | 伤3→6，针×1.5，距离8→15；CD30→15 | java/teleporter/ThreateningTeleport.java；活体忽略墙、30%恢复物品保留 |
| TP penetrate_teleport | 每格14→9 / 80→50 | 最大距离10→35，步长.8、三阶段穿墙；CD50→30 | java/teleporter/PenetrateTeleport.java；服务器夹紧客户端滚轮距离 |
| TP mark_teleport | 每格12→4 / 40→20 | 最大25→60，每tick标记前进2，最短3；CD30→0 | scala/teleporter/MarkTeleport.scala，一致 |
| TP flesh_ripping | 130→270 / 60→50 | 伤5→12，距离6→14；CD90→40 | java/teleporter/FleshRipping.java；按实际getter核对，一致 |
| TP location_teleport | (200→150)×跨维系数×max(8,sqrt(min(800,距离))) / 240 | 跨维需熟练>.8；exp原距离≥200为.03否则.015；CD30→20 | scala/teleporter/LocationTeleport.scala；本次移除同步加载但保留冷区目的地 |
| TP shift_tp | 260→320 / 40→30 | 伤15→35，距离25→35；CD100→60 | java/teleporter/ShiftTeleport.java；ItemStack.useOn保留NeoForge放置事务 |
| TP flashing | 启动CP80→60 / OL250→180；每次13→6 | 距离12→18、持续60→150、重力取消40tick；CD900→400 | java/teleporter/Flashing.java；方向/epoch/按下预览松开提交由服务器约束 |

上表路径相对于 `src/main/{java|scala}/cn/academy/ability/vanilla/`。技能树、学习前置和课程顺序的本地完整表在SkillRegistry；本次没有改它们的基础曲线。旧纯公式/字符串单测不能替代真实执行测试，详见core-audit。

被动及资源：初始CP数组{1800,1800,2800,4000,5800,8000}、可成长CP{0,900,1000,1500,1700,12000}；初始OL{100,100,150,240,350,500}、可成长OL{0,40,70,80,100,500}。CP成长率.0025，OL.0058且单次增长≤10。普通/高级脑课程+1000/+1500 CP，高级+100 OL，思维修养恢复×1.2。辐射后续伤害×1.4→1.8，首个创建标记的伤害不放大，标记60tick；传送暴击三级倍率1.3/1.6/2.6。对应LegacyResourceProgression、PassiveSkillMath以及上游CPData/default.conf/MDDamageHelper/TPSkillHelper；数据版本仍4。
