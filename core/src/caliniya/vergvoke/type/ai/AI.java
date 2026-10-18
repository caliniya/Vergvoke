package caliniya.vergvoke.type.ai;

import caliniya.vergvoke.base.ecs.*;

public abstract class AI {

	/// 玩家是否已给该单位下过指令（主线程置位；置了就不自动认领建造计划——玩家指令优先）
	public boolean ordered;

	/// 每帧思考（ai 线程调用）；delta 为 60TPS 基准帧时间倍率（1.0 = 理想一帧）
	public abstract void update(Unit u, float delta);

	/// 类型原型 → 运行实例（UnitType.create 用；原型本身保持无状态）
	public abstract AI copy();
}
