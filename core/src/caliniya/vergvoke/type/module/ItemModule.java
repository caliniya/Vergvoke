package caliniya.vergvoke.type.module;

import arc.util.io.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.type.type.*;

/**
 * 物品存储模块，管理物品存储、容量及过滤规则。
 *
 */
public class ItemModule extends Module {

	/** 每种物品的存储上限 */
	public int capacity = 100;

	/** 存储数组，索引=物品ID，值=当前数量 */
	public int[] items;

	/** 过滤数组，true=允许存储；null表示允许所有 */
	public boolean[] filter;

	/**
	 * 构造指定容量的物品模块。
	 *
	 * @param capacity 每种物品的存储上限
	 */
	public ItemModule(int capacity) {
		this.capacity = capacity;
		int size = Math.max(Contents.totalItemCount + 1, 10);
		this.items = new int[size];
	}

	/**
	 * 设置过滤器，仅允许指定物品类型存入。
	 *
	 * @param types 允许的物品列表
	 */
	public void setFilter(ItemType... types) {
		filter = new boolean[items.length];
		if (types != null) {
			for (ItemType type : types) {
				if (type != null && type.id < filter.length) {
					filter[type.id] = true;
				}
			}
		}
	}

	/** 清除过滤器，允许所有物品存入。 */
	public void clearFilter() {
		filter = null;
	}

	/**
	 * 检查是否允许存储指定类型的物品。
	 *
	 * @param type 物品类型
	 *
	 * @return 允许存储返回true，否则false
	 */
	public boolean accepts(ItemType type) {
		return type != null && type.id < items.length && (filter == null || filter[type.id]);
	}

	/**
	 * 尝试添加物品。
	 *
	 * @param type   物品类型
	 * @param amount 添加数量
	 *
	 * @return 实际添加数量
	 */
	public int addItem(ItemType type, int amount) {
		if (!accepts(type) || amount <= 0) return 0;
		int space = capacity - items[type.id];
		if (space <= 0) return 0;
		int added = Math.min(amount, space);
		items[type.id] += added;
		return added;
	}

	/**
	 * 尝试添加物品对象。
	 *
	 * @param item 物品对象
	 *
	 * @return 实际添加数量
	 */
	public int addItem(Item item) {
		return (item == null || item.isEmpty()) ? 0 : addItem(item.type, item.amount);
	}

	/**
	 * 尝试移除物品。
	 *
	 * @param type   物品类型
	 * @param amount 移除数量
	 *
	 * @return 实际移除数量
	 */
	public int removeItem(ItemType type, int amount) {
		if (type == null || amount <= 0 || type.id >= items.length) return 0;
		int removed = Math.min(amount, items[type.id]);
		items[type.id] -= removed;
		return removed;
	}

	public int removeItem(Item item) {
		return removeItem(item.type, item.amount);
	}

	/**
	 * 获取物品数量。
	 *
	 * @param type 物品类型
	 *
	 * @return 当前数量
	 */
	public int getAmount(ItemType type) {
		return (type == null || type.id >= items.length) ? 0 : items[type.id];
	}

	@Override
	public void write(Writes write) {
		write.i(capacity);
		// 写入过滤器
		if (filter != null) {
			write.bool(true);
			int count = 0;
			for (int i = 1; i < filter.length; i++) if (filter[i]) count++;
			write.s((short) count);
			for (int i = 1; i < filter.length; i++) if (filter[i]) write.i(i);
		} else {
			write.bool(false);
		}
		// 写入物品数据
		int count = 0;
		for (int i = 1; i < items.length; i++) if (items[i] > 0) count++;
		write.s((short) count);
		for (int i = 1; i < items.length; i++) {
			if (items[i] > 0) {
				write.i(i);
				write.i(items[i]);
			}
		}
	}

	@Override
	public void read(Reads read) {
		capacity = read.i();
		// 读取过滤器
		if (read.bool()) {
			short filterCount = read.s();
			if (filter == null || filter.length != items.length) {
				filter = new boolean[items.length];
			} else {
				java.util.Arrays.fill(filter, false);
			}
			for (int i = 0; i < filterCount; i++) {
				int id = read.i();
				if (id < filter.length) filter[id] = true;
			}
		} else {
			filter = null;
		}
		// 读取物品数据
		short itemCount = read.s();
		java.util.Arrays.fill(items, 0);
		for (int i = 0; i < itemCount; i++) {
			int id = read.i();
			int amt = read.i();
			if (id < items.length) items[id] = amt;
		}
	}
}