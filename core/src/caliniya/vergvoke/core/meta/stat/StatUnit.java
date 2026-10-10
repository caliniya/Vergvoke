package caliniya.vergvoke.core.meta.stat;

import arc.*;
import arc.util.*;

/** 数值单位定义。控制统计信息中数字的显示格式。 */
public enum StatUnit {
	none("none"),
	percent("percent", false),
	multiplier("multiplier", false),
	perSecond("perSecond", false),
	perMinute("perMinute", false),
	perShot("perShot", false),
	timesSpeed("timesSpeed", false),
	bool("boolean", false),
	blocks("blocks"),
	blocksSquared("blocksSquared"),
	tilesSecond("tilesSecond"),
	degrees("degrees"),
	degreesSecond("degreesSecond"),
	seconds("seconds"),
	minutes("minutes"),
	shots("shots"),
	items("items"),
	itemsSecond("itemsSecond");

	public final String name;
	public final boolean space;
	public @Nullable String icon;
	public final String localizedName;

	StatUnit(String name, boolean space) {
		this.name = name;
		this.space = space;
		this.localizedName = Core.bundle.get("statUnit." + name);
	}

	StatUnit(String name) {
		this(name, true);
	}

	public StatUnit icon(String icon) {
		this.icon = icon;
		return this;
	}

	/** 格式化数值（四舍五入到整数） */
	public String format(float value) {
		if (this == none)
			return String.valueOf(Math.round(value));
		if (this == percent)
			return Math.round(value * 100) + localizedName;
		if (this == multiplier)
			return "×" + Math.round(value) + localizedName;
		if (this == blocks)
			return Math.round(value / 32) + localizedName;
		if (this == bool)
			return value == 1f ? Core.bundle.get("statUnit.true") : Core.bundle.get("statUnit.false");
		return Math.round(value) + (space ? " " : "") + localizedName;
	}
}
