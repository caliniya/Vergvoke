package caliniya.vergvoke.type.def.comps;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.type.ai.Ai;

/// AI 控制组件：持有本实体的 AI 实例（null = 无 AI）；驱动归 AiSystem（ai 线程），本组件无 @Updata
@Component(name = "Ai", index = 10)
public class AiComp {

	/// 本实体的 AI（UnitType.ai 原型 copy 而来；对象池复用由 UnitType.create 无条件重赋，不托底）
	public Ai ai;

	// 未来加：setState(...) 等基础状态方法（停火/战斗/撤退），现在只挂字段
}
