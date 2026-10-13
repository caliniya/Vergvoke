package caliniya.vergvoke.type.ai;

import caliniya.vergvoke.base.ecs.Unit;

/**
 * AI 抽象基类：与组件解耦的独立实现（组件只挂字段，逻辑在这里）。
 *
 * <p>
 * 挂载链：{@code UnitType.ai}（类型原型，null = 无 AI）→ {@code UnitType.create} copy 成实例 →
 * {@code AiComp.ai}（组件字段）持有 → {@code AiSystem}（ai 并行线程）逐帧驱动。
 *
 * <p>
 * 线程纪律（实现必须遵守）：只允许"思考"与写白名单数据——自身字段、{@code BuildPlan} 静态锁内、
 * {@code WorldData.moveunits}（synchronized 既有锁）、单位 {@code item}；创建/销毁实体、
 * 碰 {@code EntityArs} / {@code RouteData} / 渲染 UI 等生命周期副作用一律登记
 * {@code BuildPlan.pend}，由主线程 {@code GameProcess} drain 执行。
 */
public abstract class Ai {

	/// 玩家是否已给该单位下过指令（主线程置位；置了就不自动认领建造计划——玩家指令优先）
	public boolean ordered;

	/// 每帧思考（ai 线程调用）；delta 为 60TPS 基准帧时间倍率（1.0 = 理想一帧）
	public abstract void update(Unit u, float delta);

	/// 类型原型 → 运行实例（UnitType.create 用；原型本身保持无状态）
	public abstract Ai copy();
}
