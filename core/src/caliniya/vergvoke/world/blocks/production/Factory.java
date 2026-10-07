package caliniya.vergvoke.world.blocks.production;

import caliniya.vergvoke.type.type.*;
import caliniya.vergvoke.world.*;

/**
 * 工厂：生产建筑的配置载体（行为在 {@code CraftComp}，create 时把配方拷进实例）。
 *
 * <p>第一期配方无输入，每 {@code craftTime} 产出 {@code outputAmount} 个 {@code outputItem}
 * 堆进自身 ItemModule；仓库满则停产等待（进度钳在 craftTime）。
 */
public class Factory extends Block {

	/** 工时（delta 累积单位，60 = 1 秒）。 */
	public float craftTime = 60f;

	/** 产出物品（null = 不生产）。 */
	public ItemType outputItem;

	/** 每次产出的数量。 */
	public int outputAmount = 1;

	public Factory(String name) {
		super(name);
		this.solid = true;
	}
}
