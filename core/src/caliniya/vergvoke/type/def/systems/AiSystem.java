package caliniya.vergvoke.type.def.systems;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.system.System;

/**
 * AI 系统：ai 并行线程上统一驱动所有单位 AI。
 *
 * <p>
 * 实例由生成的 {@code Systems.startThreads()} 创建并起独立线程（60TPS）。
 * 本系统只当"调度壳"：遍历走 {@code each}（读锁全程持有，EntityAr 的
 * iterator 非线程安全，不能 for-each），逐个调 {@code u.ai.update}。
 * 实体生命周期副作用由各 AI 登记 {@code BuildPlan.pend}，主线程 {@code GameProcess}
 * drain 执行（线程纪律见 {@code Ai}）。
 */
@ThreadDef(name = "ai")
@SystemDef(name = "AiSystem", thread = "ai", index = 2)
public class AiSystem extends System<AiSystem> {

	/**
	 * 全局实例（构造器赋值；实例化由 Systems.startThreads 完成）。
	 */
	public static AiSystem it;

	public AiSystem() {
		it = this;
	}

	@Override
	public void update(float delta) {
		EntityArs.Unit.each(u -> {
			// 跨帧引用防护：已死 / 已回池（reset 置空 type）的单位跳过
			if (u.type == null || u.health <= 0f) {
				return;
			}
			if (u.ai != null) {
				u.ai.update(u, delta);
			}
		});
	}
}
