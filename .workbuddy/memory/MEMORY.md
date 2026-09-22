# Vergvoke 项目长期约定

## 作者明确要求（来自 ai-using/main.txt、ai-using/agent.txt，必须遵守）

- 项目处于**极早期**，大量 `test` 字样变量 / 占位方法 / 占位类。开发阶段**不要**为了规范性去改它们，
  AI 读代码和改代码时也不要把它们当错误指出。
- `tools/.../base/tool/Ar.java` 是作者从 Arc `Seq` 克隆后改名的（作者觉得名字好听）。
  不要用"不规范/重复造轮子"为由去改。
- `android-old` 是兼容老版本安卓（sdk14）的构建模块，**不要**动不动提议删掉它。
- 临时代码放到 `ai-using/src/temp/`，不要放工作区根目录；记得定时清空。
- 不要一天到晚追求规范。

## 技术栈

- Arc v157.1（Anuken/Mindustry 底层引擎）+ Java 17 + Gradle 8.10 多模块。
- 模块：core / desktop / android / android-old / tools / annotation。
- 编译期 ECS：`annotation` 模块的 `ECProcessor` 生成 `caliniya.vergvoke.base.ecs` 下的
  `Unit.java`、`Systems.java`、`EntityArs.java`（产物在 core/build/generated/sources/annotationProcessor/java/main）。

## 当前重构方向（2026-09-22 起）

- 实体的**创建与销毁**正从生成类迁移到 `EntityType`：生成的 `Unit` 已不再带 `static create(...)` 工厂，
  统一走 `UnitType.create(team, x, y)`（内部 `Pools.obtain`）。
- `Entity` 的 `draw/remove/kill` 已从 abstract 改为委托给 `type` 的具体方法。
- 数据目录统一由 `io/DataPaths` 解析（`-Dvergvoke.datadir` → `<程序目录>/data` → `%APPDATA%/Vergvoke`）。

## 本地环境坑

- 本环境 Bash 的 PATH 是坏的，任何 Bash 命令前先 `export PATH="/usr/bin:/bin:$PATH"`。
- 源码多为 CRLF；Git 有 "LF will be replaced by CRLF" 提示，属正常。
- Gradle 8.10 已在本地缓存，可直接 `./gradlew classes` 编译（约 5 秒）。

## 本地化

- 键格式：`statUnit.xxx`，中文翻译在 `assets/language_zh_CN.properties`。

## 参考文档

- `ai-using/main.txt`：项目概览与包结构（作者自述可能过时）。
- `docs/setting(ban)/`：世界设定文档（部分已标记"此设定已放弃"）。
