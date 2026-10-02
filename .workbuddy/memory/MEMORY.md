# Vergvoke 项目长期约定

## 作者明确要求（来自 ai-using/main.txt、ai-using/agent.txt，必须遵守）

- 项目处于**极早期**，大量 `test` 字样变量 / 占位方法 / 占位类。开发阶段**不要**为了规范性去改它们，
  AI 读代码和改代码时也不要把它们当错误指出。
- `tools/.../base/tool/Ar.java` 是作者从 Arc `Seq` 克隆后改名的（作者觉得名字好听）。
  不要用"不规范/重复造轮子"为由去改。
- `android-old` 是兼容老版本安卓（sdk14）的构建模块，**不要**动不动提议删掉它。
- 临时代码放到 `ai-using/src/temp/`，不要放工作区根目录；记得定时清空。
- 不要一天到晚追求规范。
- **不要为了存档兼容去 bump `io/GameIO.SAVE_VERSION`**（2026-10-01 用户明确否决）。
  游戏还在极早期，没有真实旧档需要保护；改了序列化直接跑就行，测试档重开即可。

## 技术栈

- Arc v157.1（Anuken/Mindustry 底层引擎）+ Java 17 + Gradle 8.10 多模块。
- 模块：core / desktop / android / android-old / tools / annotation。
- 编译期 ECS：`annotation` 模块的 `ECProcessor` 生成 `caliniya.vergvoke.base.ecs` 下的
  `Unit.java`、`Building.java`、`Systems.java`、`EntityArs.java`
  （产物在 core/build/generated/sources/annotationProcessor/java/main）。

## 当前重构方向（2026-09-22 起）

- 实体的**创建与销毁**正从生成类迁移到 `EntityType`：生成的实体已不再带 `static create(...)` 工厂，
  统一走 `XxxType.create(...)`（内部 `Pools.obtain`）。
- `Entity` 的 `draw/remove/kill` 已从 abstract 改为委托给 `type` 的具体方法。
- 实体与内容的链接：`Block` 通过 `final BuildingType buildingType` 反向持有类型，
  `BuildingType.block` 作为"过渡桥"供 size/psize/health/形状（等配置搬进 type 后拆掉）。
- 数据目录统一由 `io/DataPaths` 解析（`-Dvergvoke.datadir` → `<程序目录>/data` → `%APPDATA%/Vergvoke`）。

### ECProcessor 的铁律（改组件前必读）

- **`@Import` 借不到 `Entity.type`** —— 基类它是泛型字段 `T`，类型校验必然失败
  （`has type XxxType, but the borrowed field has type T`）。要用 type 的逻辑放 `XxxType` 侧，由调用方调。
- **实体级错误会让整个 `@Entity` 不生成**，只留一片"找不到符号 类 Xxx"。
  排查永远先看注解处理器那几条 error，后面全是连锁反应。
- 单个组件内 `@Save` 字段与 `@Write/@Read` **不能混用**（放不同组件则无妨）。
- 组件里的其他方法（含 private/static）会一并注入生成实体，方法体里 `this` 在两边都得能编译。

## 实体销毁（2026-10-01 定案）

`UnitType.remove` / `BuildingType.remove` 是唯一的销毁出口（`kill()` 默认转调 `remove()`）。铁律：

- **顺序**：摘干净所有持有者 → 注销容器 → `freeID` → `Pools.free`。反了就是悬垂引用。
- **只在主线程销毁**（2026-10-02 定案，之前那套"后台线程直接销毁+加锁"是错的）：
  伤害结算会在 BulletProcess 线程发生，`applyDamage` 血量归零只做 `Entities.markDead(this)` 登记，
  真正的销毁由 `GameProcess` 每帧开头 `Entities.drainDead` 后统一 `kill()`。
  后台线程碰对象池 = 实体 free 后仍躺在待死队列里 → 下一帧对着 `type == null` 再杀一次 → NPE。
- 摸 `WorldData.moveunits` 仍要 `synchronized (WorldData.moveunits)`（UnitMath / InputProcess 共用）。
- **防二次销毁**：同一实体会同时出现在 `deadUnits` 与 `freshKilled`，用 `type == null` 挡
  （`Pools.free` → `reset()` 会置空 type），否则 double-free 池。
- **对象池复用不跑构造**：没有声明初始化式的运行时字段必须在 `create` 里显式托底，否则残留串味。
- **改了实体坐标或 size 必须同步四叉树**：`EntityArs.X.move(e, e.x, e.y)`。
  忘了的话实体仍会渲染/更新（走 array），但所有 `intersect` 查询都查不到它——
  表现是"某些敌人永远打不到"，极难一眼看出来。读档路径尤其容易漏（先在原点 create，read 才填坐标）。

## 本地环境坑

- 本环境 Bash 的 PATH 是坏的，任何 Bash 命令前先 `export PATH="/usr/bin:/bin:$PATH"`。
- 源码多为 CRLF；Git 有 "LF will be replaced by CRLF" 提示，属正常。
- Gradle 8.10 已在本地缓存，可直接 `./gradlew classes` 编译（约 5~30 秒）。
- **Gradle 输出是 GBK**，bash 里看到的是乱码；要读错误信息走 PowerShell 重定向，或用
  `$out = ./gradlew ... 2>&1; $out | Select-String -Pattern "错误|error:|\.java:"`。
- 同时开两个 Gradle 会 `BindException: Address already in use`，先 `./gradlew --stop` 等几秒。
- **grep 注意大小写**：`WorldData.buildings` 是小写 b，只搜 `Building` 会漏掉这类引用点。

## 本地化

- 键格式：`statUnit.xxx`，中文翻译在 `assets/language_zh_CN.properties`。

## 参考文档

- `ai-using/main.txt`：项目概览与包结构（作者自述可能过时）。
- `docs/setting(ban)/`：世界设定文档（部分已标记"此设定已放弃"）。
