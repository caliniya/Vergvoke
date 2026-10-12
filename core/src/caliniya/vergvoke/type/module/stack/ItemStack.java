package caliniya.vergvoke.type.module.stack;

import arc.func.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.type.type.*;

/// 呃,一种用来快速配置的物品栈,我的建议是最好不要用作动态数据
public class ItemStack {

	/** 存储数组，索引=物品ID，值=当前数量 */
	public int[] items;

	/// 通常来说是一次性的,没有任何检查!!!
	public ItemStack set(Item item) {
		items[item.type.id] = item.amount;
		return this;
	}

	public void get(Cons2<ItemType, Integer> it) {
		for (int i = 0; i < items.length; i++) {
			if (items[i] <= 0) continue;
			it.get(Contents.items[i], items[i]);
		}
	}
}
