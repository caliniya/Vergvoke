package caliniya.vergvoke.core.meta.stat;

import arc.func.*;
import arc.struct.*;
import arc.util.*;

/**
 * 统计值单元。自身可携带子条目（datas）构成树。
 */
public class StatData {

	// 自身的信息类型
	public Stat stat;
	// 所属统计组，默认情况下 我们用通用
	public StatType type = StatType.general;
	// 对于布尔类型 除了"1"以外的值都视为否
	public float value;
	// 可选的最大值，默认情况下为-1
	public float valueMax = -1f;
	// 用于指定的缩进指数
	public int level = 1;
	// 是否显式设置过 level（add 时不再自动覆盖）
	public boolean levelSet;
	// 单位
	public StatUnit unit;
	// 处理完毕后的值，应该是包含缩进的
	public String data;
	// 原始文本（不含缩进），add 重新生成缩进时使用
	public String raw = "";

	/** 可选身份标签：区分内容相同（如同名能力）的条目；null 表示不参与匹配。 */
	public Object tag;

	/** 动态数值源：非 null 时渲染端每帧经 {@link #getData()} 取最新值（静态条目保持 null）。 */
	public @Nullable Floatp live;

	/** 显示拼接缓冲（单线程：仅渲染路径使用；返回给 Label 后立即被复制消费）。 */
	private final StringBuilder builder = new StringBuilder(64);

	// 分组的另一种解决方法
	// 自身作为分组标题，该分组所属的内容 直接加入到自身
	public Ar<StatData> datas = new Ar<>();

	/**
	 * 数值条目：默认单位、层级 1、无最大值。
	 */
	public StatData(Stat stat, float value) {
		this(stat, value, stat.unit);
	}

	/**
	 * 数值条目：默认单位，带最大值（进度格式）。
	 */
	public StatData(Stat stat, float value, float valueMax) {
		this(stat, value, stat.unit, 1, valueMax);
	}

	/**
	 * 数值条目：指定单位，无最大值。
	 */
	public StatData(Stat stat, float value, StatUnit unit) {
		this(stat, value, unit, 1, -1f);
	}

	/**
	 * 数值条目。
	 */
	public StatData(Stat stat, float value, StatUnit unit, int level, float valueMax) {
		this.stat = stat;
		this.value = value;
		this.unit = unit;
		this.level = level;
		this.valueMax = valueMax;
		this.type = stat != null ? stat.type : StatType.none;
		this.raw = (stat != null ? stat.localizedName + ": " + unit.format(value) : "");
		this.data = indent() + raw;
	}

	/**
	 * 纯文本条目（介绍等非数值内容）。
	 */
	public StatData(String data) {
		this(data, 1, StatType.none);
	}

	/**
	 * 纯文本条目：指定缩进层级。
	 */
	public StatData(String data, int level) {
		this(data, level, StatType.none);
	}

	/**
	 * 纯文本条目：指定所属分组。
	 */
	public StatData(String data, StatType type) {
		this(data, 1, type);
	}

	/**
	 * 纯文本条目。
	 */
	public StatData(String data, int level, StatType type) {
		this.level = level;
		this.type = type;
		this.raw = data;
		this.data = indent() + data;
	}

	/**
	 * 创建数值条目：指定单位。
	 */
	public static StatData with(Stat stat, float value, StatUnit unit) {
		return new StatData(stat, value, unit);
	}

	/**
	 * 创建数值条目完整版。
	 */
	public static StatData with(Stat stat, float value, StatUnit unit, int level, float valueMax) {
		return new StatData(stat, value, unit, level, valueMax);
	}

	/**
	 * 创建数值条目：带最大值（进度格式）。
	 */
	public static StatData with(Stat stat, float value, float valueMax) {
		return new StatData(stat, value, valueMax);
	}

	/**
	 * 创建数值条目：默认单位。
	 */
	public static StatData with(Stat stat, float value) {
		return new StatData(stat, value);
	}

	/**
	 * 创建纯文本条目。
	 */
	public static StatData with(String data) {
		return new StatData(data);
	}

	/**
	 * 创建纯文本条目：指定缩进层级。
	 */
	public static StatData with(String data, int level) {
		return new StatData(data, level);
	}

	/**
	 * 创建纯文本条目：层级 + 分组。
	 */
	public static StatData with(String data, int level, StatType type) {
		return new StatData(data, level, type);
	}

	/**
	 * 创建纯文本条目：指定分组。
	 */
	public static StatData with(String data, StatType type) {
		return new StatData(data, type);
	}

	/**
	 * 只更新值（最大值视为无）。
	 */
	public StatData set(float value) {
		return set(value, -1);
	}

	/**
	 * 更新值与最大值并就地重生成文本。
	 */
	public StatData set(float value, float valueMax) {
		if (this.value == value && this.valueMax == valueMax) return this;
		this.value = value;
		this.valueMax = valueMax;
		if (stat != null && unit != null) {
			this.raw = stat.localizedName + ": " + unit.format(value);
			this.data = indent() + raw;
		}
		return this;
	}

	/**
	 * 递归子树查找数值条目，返回第 1 个命中项；只查不改，找不到返回 null。
	 *
	 * @param tag 身份标签，null 表示不参与匹配
	 */
	public StatData find(Stat stat, Object tag) {
		for (StatData d : datas) {
			if (d.stat == stat && (tag == null || java.util.Objects.equals(d.tag, tag))) return d;
			StatData found = d.find(stat, tag);
			if (found != null) return found;
		}
		return null;
	}

	/**
	 * 递归子树查找纯文本条目（raw 全等比较），返回第 1 个命中项；只查不改，找不到返回 null。
	 *
	 * @param tag 身份标签，null 表示不参与匹配
	 */
	public StatData find(String raw, Object tag) {
		for (StatData d : datas) {
			if (d.stat == null
					&& d.raw.equals(raw)
					&& (tag == null || java.util.Objects.equals(d.tag, tag))) return d;
			StatData found = d.find(raw, tag);
			if (found != null) return found;
		}
		return null;
	}

	/**
	 * 插入子条目：未显式设置过层级时自动 level+1，并重新生成含缩进的 data。
	 */
	public StatData add(StatData child) {
		if (!child.levelSet) {
			child.level = this.level + 1;
		}
		child.data = child.indent() + child.raw;
		datas.add(child);
		return this;
	}

	/**
	 * 重设缩进层级；显式设置后 {@link #add} 不再自动覆盖 level。
	 */
	public StatData setLevel(int level) {
		if (this.levelSet && this.level == level) return this;
		this.level = level;
		this.levelSet = true;
		this.data = indent() + raw;
		return this;
	}

	/**
	 * 递归遍历自身及全部子条目。
	 */
	public void each(Cons<StatData> con) {
		con.get(this);
		datas.each(d -> d.each(con));
	}

	/**
	 * 渲染端每帧取显示文本：静态条目直接返回 data；动态条目（live 非 null）先按最新值就地刷新。
	 */
	public CharSequence getData() {
		if (live != null) {
			set(live.get(), valueMax);
		}
		if (stat != null && unit != null && valueMax > 0f) {
			String maxStr = unit.format(valueMax);
			builder.setLength(0);
			builder
					.append(indent())
					.append(stat.localizedName)
					.append(": ")
					.append(Strings.padLeft(unit.format(value), maxStr.length()))
					.append("/")
					.append(maxStr)
					.append(" (")
					.append(Strings.padLeft(String.valueOf(Math.round(value / valueMax * 100f)), 3))
					.append("%)");
			return builder;
		}
		return data;
	}

	/**
	 * 按缩进层级生成前缀文本。
	 */
	String indent() {
		return level <= 0 ? "   " : "   " + Strings.repeat("\u3000\u3000", level);
	}

	/**
	 * 数值条目：仅查直接子条目，按 stat 命中则就地 set，未命中则新建插入并返回。
	 * <p>与 {@link #find} 的区别：保证返回非 null 条目，适合「每帧刷新某条」的动态路径。
	 */
	public StatData get(Stat stat, float value, StatUnit unit, int level, float valueMax) {
		for (StatData d : datas) {
			if (d.stat == stat) {
				d.set(value, valueMax);
				return d;
			}
		}
		StatData d = new StatData(stat, value, unit, level, valueMax);
		add(d);
		return d;
	}

	/**
	 * 纯文本条目。
	 */
	public StatData get(String raw, int level, StatType type) {
		for (StatData d : datas) {
			if (d.stat == null && d.raw.equals(raw)) return d;
		}
		StatData d = new StatData(raw, level, type);
		add(d);
		return d;
	}

	/**
	 * 默认单位、层级 1、无最大值。最常用的动态刷新形式。
	 */
	public StatData get(Stat stat, float value) {
		return get(stat, value, stat.unit, 1, -1f);
	}

	/**
	 * 指定单位（覆盖 stat 自带单位）。
	 */
	public StatData get(Stat stat, float value, StatUnit unit) {
		return get(stat, value, unit, 1, -1f);
	}

	/**
	 * 带最大值：走「值/最大 (百分比)」进度格式。
	 */
	public StatData get(Stat stat, float value, float valueMax) {
		return get(stat, value, stat.unit, 1, valueMax);
	}

	/**
	 * 指定单位 + 最大值。
	 */
	public StatData get(Stat stat, float value, StatUnit unit, float valueMax) {
		return get(stat, value, unit, 1, valueMax);
	}

	/**
	 * 指定缩进层级。
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
}
