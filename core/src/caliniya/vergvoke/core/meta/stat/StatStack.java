package caliniya.vergvoke.core.meta.stat;

import arc.func.*;
import arc.struct.*;

import java.util.*;

/**
 * 一个完整的信息组：按 {@link StatType} 分组的 StatData 列表。
 * <p>方法分三类，按用途选：
 * {@link #add} 系列 —— 无条件追加新条目（重复调用会产生重复行），适合一次性组装静态信息；
 * {@link #get} 系列 —— 查到就地更新、查不到新建，动态（每帧刷新）条目用这类；
 * {@link #find} 系列 —— 只查不改，找不到返回 null。
 * <p>重载规律：带 Stat 参数的按 stat 引用匹配（数值条目），带 String 参数的按 raw 文本全等匹配
 * （纯文本条目/标题），带 Object tag 的额外用身份标签区分同名条目（如能力实例自身作 tag）；
 * 可选参数依次为 unit、level、valueMax、type。
 */
public class StatStack {

	/** 有序map和有序数组。 */
	private final OrderedMap<StatType, Ar<StatData>> types = new OrderedMap<>();

	public static ObjectMap<StatType, StatData> cache;

	static {
		cache = new ObjectMap<>();
		for (StatType s : StatType.values()) {
			cache.put(s, new StatData(s.localizedName, 0));
		}
	}

	/**
	 * 为每个 StatType 初始化一个空分组。
	 */
	public StatStack() {
		for (StatType s : StatType.values()) {
			types.put(s, new Ar<StatData>());
		}
	}

	// ---------------- add：无条件追加（静态组装用；重复调用会产生重复条目） ----------------

	/**
	 * 追加数值条目：默认单位、层级 1、无最大值。
	 */
	public StatStack add(Stat stat, float value) {
		return add(stat, value, stat.unit);
	}

	/**
	 * 追加数值条目：指定单位。
	 */
	public StatStack add(Stat stat, float value, StatUnit unit) {
		return add(stat, value, unit, 1, -1f);
	}

	/**
	 * 追加数值条目：指定单位 + 最大值（进度格式）。
	 */
	public StatStack add(Stat stat, float value, StatUnit unit, float valueMax) {
		return add(stat, value, unit, 1, valueMax);
	}

	/**
	 * 追加数值条目：指定缩进层级。
	 */
	public StatStack add(Stat stat, float value, StatUnit unit, int level) {
		return add(stat, value, unit, level, -1f);
	}

	/**
	 * 追加数值条目完整版（分组取自 stat.type）。
	 */
	public StatStack add(Stat stat, float value, StatUnit unit, int level, float valueMax) {
		types.get(stat.type).add(new StatData(stat, value, unit, level, valueMax));
		return this;
	}

	/**
	 * 追加现成条目（分组取自 data.type）。
	 */
	public StatStack add(StatData data) {
		types.get(data.type).add(data);
		return this;
	}

	/**
	 * 追加纯文本条目（默认加在 function 分组、层级 1）。
	 */
	public StatStack add(String raw) {
		types.get(StatType.function).add(new StatData(raw));
		return this;
	}

	/**
	 * 追加纯文本条目：默认 function 分组，指定层级。
	 */
	public StatStack add(String raw, int level) {
		types.get(StatType.function).add(new StatData(raw, level));
		return this;
	}

	/**
	 * 追加纯文本条目：指定分组、层级 1。
	 */
	public StatStack add(String raw, StatType type) {
		types.get(type).add(new StatData(raw));
		return this;
	}

	/**
	 * 追加纯文本条目完整版。
	 */
	public StatStack add(String raw, int level, StatType type) {
		types.get(type).add(new StatData(raw, level, type));
		return this;
	}

	// ---------------- get：查到就地更新，查不到新建插入（动态刷新用） ----------------

	/**
	 * 数值条目完整版：按 stat 查找，命中则 set 值，未命中则新建插入并返回。
	 */
	public StatData get(Stat stat, float value, StatUnit unit, int level, float valueMax) {
		Ar<StatData> list = types.get(stat.type);
		for (int i = 0; i < list.size; i++) {
			StatData d = list.get(i);
			if (d.stat == stat) {
				d.set(value, valueMax);
				return d;
			}
		}
		StatData d = new StatData(stat, value, unit, level, valueMax);
		list.add(d);
		return d;
	}

	/**
	 * 纯文本条目完整版：按 raw 全等查找，命中则返回，未命中则新建插入。
	 */
	public StatData get(String raw, int level, StatType type) {
		Ar<StatData> list = types.get(type);
		for (int i = 0; i < list.size; i++) {
			StatData d = list.get(i);
			if (d.stat == null && d.raw.equals(raw))
				return d;
		}
		StatData d = new StatData(raw, level, type);
		list.add(d);
		return d;
	}

	/**
	 * 数值条目完整版 + tag 匹配（区分同名 stat 的条目）。
	 */
	public StatData get(
			Stat stat, float value, StatUnit unit, int level, float valueMax, Object tag) {
		Ar<StatData> list = types.get(stat.type);
		for (int i = 0; i < list.size; i++) {
			StatData d = list.get(i);
			if (d.stat == stat && Objects.equals(d.tag, tag)) {
				d.set(value, valueMax);
				return d;
			}
		}
		StatData d = new StatData(stat, value, unit, level, valueMax);
		d.tag = tag;
		list.add(d);
		return d;
	}

	/**
	 * 纯文本条目完整版 + tag 匹配。
	 */
	public StatData get(String raw, int level, StatType type, Object tag) {
		Ar<StatData> list = types.get(type);
		for (int i = 0; i < list.size; i++) {
			StatData d = list.get(i);
			if (d.stat == null && d.raw.equals(raw) && Objects.equals(d.tag, tag))
				return d;
		}
		StatData d = new StatData(raw, level, type);
		d.tag = tag;
		list.add(d);
		return d;
	}

	/**
	 * 按 tag 查找纯文本标题条目（默认 function 分组、层级 1）：命中则替换文本，未命中则新建。
	 */
	public StatData get(Object tag, String text) {
		return get(tag, text, 1, StatType.function);
	}

	/**
	 * 按 tag 查找纯文本标题条目：默认 function 分组，指定层级（用于新建时）。
	 */
	public StatData get(Object tag, String text, int level) {
		return get(tag, text, level, StatType.function);
	}

	/**
	 * tag 标题完整版：在指定分组内按 tag（身份标识，如能力实例自身）查找纯文本条目；
	 * 命中则把文本替换为 text（保持原有缩进），未命中则新建插入（层级用 level）。
	 */
	public StatData get(Object tag, String text, int level, StatType type) {
		Ar<StatData> list = types.get(type);
		for (int i = 0; i < list.size; i++) {
			StatData d = list.get(i);
			if (d.stat == null && Objects.equals(d.tag, tag)) {
				d.raw = text;
				d.data = d.indent() + text;
				return d;
			}
		}
		StatData d = new StatData(text, level, type);
		d.tag = tag;
		list.add(d);
		return d;
	}

	/**
	 * 数值条目：带最大值（进度格式）。
	 */
	public StatData get(Stat stat, float value, float valueMax) {
		return get(stat, value, stat.unit, 1, valueMax, null);
	}

	/**
	 * 数值条目 + tag（默认单位/层级）。
	 */
	public StatData get(Stat stat, float value, Object tag) {
		return get(stat, value, stat.unit, 1, -1f, tag);
	}

	/**
	 * 数值条目 + tag：指定单位。
	 */
	public StatData get(Stat stat, float value, StatUnit unit, Object tag) {
		return get(stat, value, unit, 1, -1f, tag);
	}

	/**
	 * 数值条目 + tag：指定单位 + 最大值。
	 */
	public StatData get(Stat stat, float value, StatUnit unit, float valueMax, Object tag) {
		return get(stat, value, unit, 1, valueMax, tag);
	}

	/**
	 * 数值条目 + tag：指定缩进层级。
	 */
	public StatData get(Stat stat, float value, StatUnit unit, int level, Object tag) {
		return get(stat, value, unit, level, -1f, tag);
	}

	/**
	 * 纯文本条目 + tag：默认 function 分组、层级 1。
	 */
	public StatData get(String raw, Object tag) {
		return get(raw, 1, StatType.function, tag);
	}

	/**
	 * 纯文本条目 + tag：默认 function 分组，指定层级。
	 */
	public StatData get(String raw, int level, Object tag) {
		return get(raw, level, StatType.function, tag);
	}

	/**
	 * 纯文本条目 + tag：指定分组、层级 1。
	 */
	public StatData get(String raw, StatType type, Object tag) {
		return get(raw, 1, type, tag);
	}

	/**
	 * 数值条目：默认单位、层级 1、无最大值。最常用的动态刷新形式。
	 */
	public StatData get(Stat stat, float value) {
		return get(stat, value, stat.unit, 1, -1f);
	}

	/**
	 * 数值条目：指定单位。
	 */
	public StatData get(Stat stat, float value, StatUnit unit) {
		return get(stat, value, unit, 1, -1f);
	}

	/**
	 * 数值条目：指定单位 + 最大值。
	 */
	public StatData get(Stat stat, float value, StatUnit unit, float valueMax) {
		return get(stat, value, unit, 1, valueMax);
	}

	/**
	 * 数值条目：指定缩进层级。
	 */
	public StatData get(Stat stat, float value, StatUnit unit, int level) {
		return get(stat, value, unit, level, -1f);
	}

	/**
	 * 纯文本条目：默认 function 分组、层级 1。
	 */
	public StatData get(String raw) {
		return get(raw, 1, StatType.function);
	}

	/**
	 * 纯文本条目：默认 function 分组，指定层级。
	 */
	public StatData get(String raw, int level) {
		return get(raw, level, StatType.function);
	}

	/**
	 * 纯文本条目：指定分组、层级 1。
	 */
	public StatData get(String raw, StatType type) {
		return get(raw, 1, type);
	}

	// ---------------- find：只查不改，找不到返回 null ----------------

	/**
	 * 按分组匹配数值条目（stat.type 即其分组），返回第 1 个命中项。
	 */
	public StatData find(Stat stat) {
		for (StatData data : types.get(stat.type)) {
			if (data.stat == stat)
				return data;
		}
		return null;
	}

	/**
	 * 按分组匹配数值条目 + tag（注意：stat 与 tag 是「或」关系，tag 用 == 比较）。
	 */
	public StatData find(Stat stat, Object tag) {
		for (StatData data : types.get(stat.type)) {
			if (data.stat == stat || data.tag == tag)
				return data;
		}
		return null;
	}

	/**
	 * 按 raw 包含匹配纯文本条目，返回第 1 个命中项。
	 */
	public StatData find(String raw, StatType type) {
		for (StatData data : types.get(type)) {
			if (data.raw.contains(raw))
				return data;
		}
		return null;
	}

	/**
	 * 按 raw 包含匹配纯文本条目 + tag，返回第 1 个命中项。
	 */
	public StatData find(String raw, StatType type, Object tag) {
		for (StatData data : types.get(type)) {
			if (data.raw.contains(raw) && Objects.equals(data.tag, tag))
				return data;
		}
		return null;
	}

	// ---------------- 生命周期 / 渲染 ----------------

	/**
	 * 清空所有分组内容（保留分组结构，可复用）。
	 */
	public StatStack clear() {
		types.each((K, V) -> V.clear());
		return this;
	}

	/**
	 * 递归交付全部 StatData（含分组标题条目）。data 已含缩进，渲染端直接显示。
	 */
	public void each(Cons<StatData> cons) {
		types.each(
				(K, V) -> {
					if (!V.any()) {
						return;
					}
					cons.get(cache.get(K));
					V.each(d -> d.each(a -> cons.get(a)));
				});
	}
}
