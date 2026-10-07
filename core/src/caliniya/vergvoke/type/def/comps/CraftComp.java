package caliniya.vergvoke.type.def.comps;

import arc.struct.*;
import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.type.def.craft.cons.*;
import caliniya.vergvoke.type.def.craft.output.*;
import caliniya.vergvoke.type.module.*;

/**
 * 通用生产逻辑
 *
 * <p>配方（craftTime / outputItem / outputAmount）由 {@code Factory} 配置、
 * {@code BuildingType.create} 拷进实例；进度（progress）是运行态，随存档持久化。
 * 无输入消耗——inputs 的位置留好，等物流系统立项再扩展。
 *
 * <p>非工厂建筑 outputItem 为 null，update 直接跳过，挂在 BuildingDef 上无害。
 */
@Component(name = "Craft", index = 8, proc = "main")
public class CraftComp {

	/** 生产进度（运行态；仓库满时钳在 craftTime 等待，不丢产出）。 */
	@Save(index = 1)
	public float progress;

	public Ar<Outputer> outputers;

	public Ar<Consumer> consumers;


	@Import
	public ItemModule item;

	@Updata
	public void update(float delta) {
	}

}
