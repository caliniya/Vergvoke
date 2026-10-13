package caliniya.vergvoke.type.ai;

import caliniya.vergvoke.base.ecs.Unit;

/// 建造 AI（P1 骨架）：字段先立好，认领/前往/推进/扣物品全链路 P3 实现
public class BuildAi extends Ai {

	/// 认领扫描计时：空闲时累加，到 0.5s 找一次首个未认领计划
	public float timer;

	/// 物品消耗浮点累计：跨帧累计，满 1 扣 1（取整去小数，避免每帧截断为 0）
	public float consumed;

	@Override
	public void update(Unit u, float delta) {
		// P3：空闲认领 → 前往 → 进建造半径登记 SPAWN → 推进+扣物品 → 完工登记 FINISH
		// （拆除流 P4：kind=1 持续返还 × Game.deconstructRefund）
	}

	@Override
	public Ai copy() {
		return new BuildAi();
	}
}
