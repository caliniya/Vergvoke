package caliniya.vergvoke.base.game;

import java.util.*;
import arc.math.geom.*;
import arc.math.geom.QuadTree.QuadTreeObject;
import arc.util.io.*;
import arc.util.pooling.*;
import arc.util.pooling.Pool.Poolable;
import caliniya.vergvoke.base.api.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.tool.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.core.meta.stat.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.type.ability.*;
import caliniya.vergvoke.type.ability.api.*;
import caliniya.vergvoke.type.enhance.api.*;
import caliniya.vergvoke.type.module.*;

/**
 * 游戏实体基类。实现了 {@link QuadTreeObject}，以便放入 EntityGroup 的四叉树空间索引。
 *
 * <p>
 * {@code T} = 该实体的类型目标（{@link EntityType} 实现类，由 {@code @Entity(type=...)} 声明）；
 * {@code E} = 实体自身类型（生成实体传入自身，例如 {@code Entity<UnitType, Unit>}），便于链式调用返回
 * {@code E}。
 */
public abstract class Entity<T extends EntityType, E extends Entity<?, ?>> implements Poolable, QuadTreeObject {

    /** 类型目标（模板）：配置与类型级行为来源。 */
    public T type;

    // --- 公共坐标 ---
    public float x, y;

    /** 碰撞/绘制尺寸（像素；0 时 hitboxSize 回退默认）。 */
    public float size;

    /** 渲染朝向（度）。 */
    public float rotation;

    /** 旋转后碰撞盒缓存，世界坐标。格式: [x1, y1, size1, x2, y2, size2, ...] */
    public float[] hitboxData;

    // --- 公共状态 ---
    public volatile float health;
    public float maxHealth;
    public int id;

    public volatile TeamTypes team;

    public volatile TeamData teamData;

    public ItemModule item;
    public LiquidModule liquid;
    public PowerModule power;

    public float armor;

    /** 最大护甲容量。 */
    public float armorMax;

    /** 护甲强度：固定减伤值（护甲存在时生效，可直接减到 0）。 */
    public float armorValue;

    /** 能量池：当前能量。 */
    public float energy;

    /** 能量池上限。 */
    public float energyMax;

    /** 能量恢复速率（每秒）。 */
    public float energyRegen;

    /// 能量恢复速率（每帧，60TPS 基准）。 */
    public float energyRegenTick;

    /** 当前热量 / 过热阈值（heat 到顶触发锁定）。 */
    public float heat, heatMax;

    /** 散热速率（每秒）。 */
    public float heatSpeed;

    /** 散热速率（每帧，60TPS 基准）。 */
    public float heatSpeedTick;

    /** 这个实体是否具有过热机制 */
    public boolean heatable;

    /** 实体当前是否处于锁定状态（可能但不限于热量引起的）。 */
    public boolean locked;

    /** 护甲对各类伤害的百分比抗性（0~1），索引 = DamageType.ordinal()。 */
    public float[] armorResist = new float[DamageType.values().length];

    /** 护甲对指定伤害类型的抗性（0~1）。 */
    public float armorResist(DamageType type) {
        return armorResist[type.ordinal()];
    }

    /** 击退冲量分量（后台子弹线程写、主线程读，volatile 保证可见性）。 */
    public volatile float knockX, knockY;

    // --- 能力 ---
    /** 能力列表：护盾/过热等可组合能力，默认不带。 */
    public Ar<Ability> abilities = new Ar<>();

    // --- 强化模组 ---
    /** 强化模组列表（全部，用于来源记录/开关管理）。 */
    public Ar<Enhancement> enhancements = new Ar<>();

    /** 只需每帧更新的强化模组（实现 {@link Updatable} 接口的），避免空转。 */
    public Ar<Updatable> updatableEnhancements = new Ar<>();

    public float shield, shieldMax;

    protected Entity() {
    }

    public abstract void update(float delta);

    public void draw() {
        type.draw(this);
    }

    public void remove() {
        type.remove(this);
    }

    public void kill() {
        type.kill(this);
    }

    public abstract void write(Writes w);

    public abstract void read(Reads r);

    public void hit(Bullet b) {
        applyDamage(
                b.type.damage,
                b.type.damageType,
                b.type.breakArmor,
                b.type.bypassArmor,
                b.type.breakShield,
                b.type.bypassShield);

        // 动能击退：沿子弹方向施加冲量（力度由 BulletType.knockbackForce 配置）
        if (b.type.damageType.knockback && b.type.knock > 0f) {
            knock(b.rotation, b.type.knock);
        }
    }

    /** 施加击退：方向（角度）+ 击退量。基类默认不击退（比如建筑），可击退的实体（Unit）覆写此方法。 */
    public void knock(float dir, float force) {
    }

    /**
     * 每帧公共战斗更新：热量散热/锁定、能量恢复、能力与强化模组（委托 {@link EntityType#sync}）。
     * 生成侧 {@code Systems.updateAll} 与 Building 都走这里。
     */
    public void sync(float dt) {
        shield = totalShield();
        shieldMax = totalShieldMax();
        if (type != null) {
            type.sync(this, dt);
        }
    }

    /** 挂载一个强化模组，返回自身 */
    @SuppressWarnings("unchecked")
    public E addEnhancement(Enhancement enh) {
        if (enh == null)
            return (E) this;
        enh.entity = this;
        enh.type.rebind(enh);
        enhancements.add(enh);
        if (enh instanceof Updatable u) {
            updatableEnhancements.add(u);
        }
        if (enh.enabled) {
            enh.type.onEnable(enh);
        }
        return (E) this;
    }

    /** 附加一个能力，返回自身 */
    @SuppressWarnings("unchecked")
    public E addAbility(Ability ability) {
        if (ability != null)
            abilities.add(ability.onCreate(this));
        return (E) this;
    }

    /** 取第一个指定类型的能力，没有则返回 null。 */
    @SuppressWarnings("hiding")
    public <T extends Ability> T getAbility(Class<T> S) {
        for (Ability a : abilities) {
            if (S.isInstance(a)) {
                return S.cast(a);
            }
        }
        return null;
    }

    public void addHeat(float amount) {
        if (heatable) {
            heat += amount;
        }
    }

    /** 所有护盾容量 */
    public float totalShield() {
        float total = 0f;
        for (Ability a : abilities) {
            if (a instanceof Shield s) {
                total += s.capacity();
            }
        }
        return total;
    }

    /** 对所有可开关的能力的操作 */
    public void setAllAbilities(boolean enabled) {
        for (Ability a : abilities) {
            if (a.toggleable)
                a.setEnabled(enabled);
        }
    }

    /** 所有护盾能力的最大总容量。 */
    public float totalShieldMax() {
        float total = 0f;
        for (Ability a : abilities) {
            if (a instanceof Shield s) {
                total += s.capacityMax();
            }
        }
        return total;
    }

    /**
     * 对实体造成一次伤害（三层结算：能力拦截 → 护甲 → 本体）。
     *
     * <ol>
     * <li>每个能力依次拦截（护盾吸收等），返回穿透到下一层的伤害；
     * <li>护甲层：对甲倍率 × (1 - 护甲对该类型抗性)，再减护甲强度（最低 0）；
     * <li>本体扣血，归零摧毁。
     * </ol>
     */
    public void applyDamage(float damage, DamageType type) {
        applyDamage(damage, type, false, false, false, false);
    }

    /**
     * 对实体造成一次伤害（三层结算：能力拦截 → 护甲 → 本体）。
     *
     * @param breakArmor   破甲：无视护甲的固定减伤值（护甲容量照扣）
     * @param bypassArmor  穿甲：直接穿过护甲层攻击核心
     * @param breakShield  破盾：无视护盾的强度减伤（护盾容量照扣）
     * @param bypassShield 穿盾：直接穿过护盾层
     */
    public void applyDamage(
            float damage,
            DamageType type,
            boolean breakArmor,
            boolean bypassArmor,
            boolean breakShield,
            boolean bypassShield) {
        // 1. 能力拦截（护盾等），全部吸收则直接结束
        for (Ability a : abilities) {
            damage = a.applyDamage(this, damage, type, breakShield, bypassShield);
        }
        if (damage <= 0f)
            return;

        // 2. 护甲层（容量 > 0 时存在；穿甲直接跳过护甲打核心）
        if (!bypassArmor && armor > 0f) {
            float armorReduce = breakArmor ? 0f : armorValue;
            float actual = Math.max(0f, damage * type.armorMult * (1f - armorResist(type)) - armorReduce);
            if (actual <= 0f)
                return; // 被护甲完全挡下
            armor -= actual;
            if (armor < 0f)
                armor = 0f;
            return; // 护甲破：剩余伤害不传递
        }

        // 3. 本体（无护甲或被穿甲跳过：无抗性减伤、无固定减伤）
        damage = damage * type.armorMult;
        health -= damage;
        if (health <= 0f) {
            health = 0f;
            kill();
        }
    }

    /** 把实体的实时状态（血量/护甲/护盾/能量/热量/电力……）注册为统计源，供 UI 每帧读取。 */
    public void stat(StatStack stat) {
        stat.get(Stat.health, health, maxHealth).live = () -> health;
        stat.get(Stat.armor, armor, armorMax).live = () -> armor;
        stat.get(Stat.shield, totalShield(), totalShieldMax()).live = () -> totalShield();
        stat.get(Stat.energy, energy, energyMax).live = () -> energy;
        if (heatable)
            stat.get(Stat.heat, heat, heatMax).live = () -> heat;
        if (power != null)
            stat.get(Stat.power, power.power, power.powerMax).live = () -> power.power;
        for (Ability a : abilities) {
            a.statAbility(stat);
        }
    }

    /** 碰撞盒尺寸（直径）：优先用 {@link #size}，未设置时默认 8 像素。子类可覆写。 */
    public float hitboxSize() {
        return size > 0f ? size : 8f;
    }

    /**
     * 世界坐标点是否命中本实体。
     *
     * <p>
     * 默认按 {@link #size} 外接圆判定（size≤0 时走 {@link #hitboxSize()} 兜底）。
     * 异形碰撞等由组件方法 {@code @OverrideEntity} 覆写本方法。
     */
    public boolean contains(float worldX, float worldY) {
        float r = hitboxSize() * 0.5f;
        float dx = worldX - x;
        float dy = worldY - y;
        return dx * dx + dy * dy <= r * r;
    }

    /** 填充实体的粗略包围盒：不能小于实体实际范围，但可以偏大。 */
    @Override
    public void hitbox(Rect out) {
        float half = hitboxSize() / 2f;
        out.set(x - half, y - half, hitboxSize(), hitboxSize());
    }

    @Override
    public void reset() {
        x = 0;
        y = 0;
        size = 0;
        rotation = 0;
        hitboxData = null;
        health = 0;
        maxHealth = 0;
        armor = 0;
        armorMax = 0;
        armorValue = 0;
        energy = 0;
        energyMax = 0;
        energyRegen = 0;
        heat = 0;
        heatMax = 0;
        heatSpeed = 0;
        heatable = false;
        locked = false;
        knockX = 0;
        knockY = 0;
        Arrays.fill(armorResist, 0f);
        for (Ability a : abilities) {
            if (a instanceof ForceField f) {
                ForceField.force.remove(f);
            }
        }
        abilities.clear();
        enhancements.clear();
        updatableEnhancements.clear();
        type = null;
        team = null;
        teamData = null;
        item = null;
        liquid = null;
        power = null;
    }

}
