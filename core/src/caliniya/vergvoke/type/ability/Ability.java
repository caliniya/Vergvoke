package caliniya.vergvoke.type.ability;

import arc.*;
import arc.util.*;
import arc.util.io.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.core.meta.stat.*;

/**
 * 能力
 *
 * <p>特殊机制（护盾、过热等）都做成能力，可组合地附加到单位或建筑上，默认不带。
 */
public abstract class Ability implements Cloneable {

	public String localizedName, name;

	/** 能力介绍（bundle：ability.名称.description），用于类型信息展示。 */
	public @Nullable String description;

	/** 是否可以被主动开启/关闭（指挥面板提供开关按钮）。 */
	public boolean toggleable;

	/** 能力当前是否开启（可开关能力的通用状态）。 */
	public boolean enabled = true;

	public Ability(String name) {
		this.name = name;
		localizedName = Core.bundle.get("ability." + name);
		description = Core.bundle.getOrNull("ability." + name + ".description");
	}

	/// 当一个能力被加入到实体的时候的回调，用于执行一些操作(比如立场类能力 应该在这一步将实体加入到列表)
	public Ability onCreate(Entity<?, ?> e) {
		return this;
	}

	/** 开关能力（toggleable 时由 UI 调用）。子类覆写以同步自己的开关状态。 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/** 能力激活时每秒消耗的能量。由 Entity 的净回复统一扣除，避免能量条抖动。 */
	public float energyUse() {
		return 0;
	}

	/** 向 StatStack 上报能力相关的类型参数（如护盾容量/强度/回充）。 */
	public void stats(StatStack stack) {
	}

	//运行时参数
	public void statAbility(StatStack stat) {
	}

	/** 每帧逻辑：回充、耗能、积累热量等。 */
	public void update(Entity<?, ?> e, float dt) {
	}

	/**
	 * 受伤拦截：返回穿透到下一层的伤害。
	 *
	 * <p>例如护盾能力在这里吸收伤害并返回 0；不拦截时原样返回 damage。
	 *
	 * @param breakShield  本次攻击是否破盾（无视护盾强度减伤）
	 * @param bypassShield 本次攻击是否穿盾（直接穿过护盾）
	 */
	public float applyDamage(
			Entity<?, ?> e, float damage, DamageType type, boolean breakShield, boolean bypassShield) {
		return damage;
	}

	/** 可选视觉绘制（护盾光圈等）。 */
	public void draw(Entity<?, ?> e) {
	}

	public void write(Entity<?, ?> e, Writes w) {
		write(w);
	}

	/** 序列化运行时状态（开关等）。类型定义参数由 {@link #copy()} 从类型复制，不写存档。 */
	public void write(Writes w) {
		w.bool(enabled);
	}

	/** 反序列化运行时状态，覆盖 {@link #copy()} 得到的类型默认值。 */
	public void read(Reads r) {
		enabled = r.bool();
	}

	public void read(Entity<?, ?> e, Reads r) {
		read(r);
	}

	public Ability copy() {
		try {
			return (Ability) this.clone();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
