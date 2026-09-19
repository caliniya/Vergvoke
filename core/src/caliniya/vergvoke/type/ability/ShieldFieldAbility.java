package caliniya.vergvoke.type.ability;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Rect;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.base.type.DamageType;
import caliniya.vergvoke.core.meta.stat.*;
import arc.util.io.Reads;
import arc.util.io.Writes;
import caliniya.vergvoke.core.meta.ui.Pal;
import caliniya.vergvoke.type.Bullet;
import caliniya.vergvoke.type.ability.api.*;

/**
 * 力场护盾 在一片空间中拦截子弹
 *
 * <p>
 * 按照标准的护盾机制执行
 */
public class ShieldFieldAbility extends Ability implements Shield, ForceField {

    /** 最大力场容量。 */
    public float max;

    /** 当前力场容量。 */
    public float current;

    /** 回充速率（以秒为单位设计）。 */
    public float regen;

    /** 回充速率（每帧 = regen / 60）。 */
    public float regenFrame;

    /** 开启时每秒消耗的能量（以秒为单位设计）。 */
    public float energyCost;

    /** 开启时每帧消耗的能量（= cost / 60）。 */
    public float costFrame;

    /** 最大护盾强度（满盾时的强度，默认 2）。 */
    public float maxStrength = 2f;

    // 半径旋转边数(边数为零就是圆形)
    public float radius = 195f, rotation = 0f;

    public int sides = 6;

    public Entity e;

    /** 护盾对各类伤害的百分比抗性（0~1），与单体护盾一致。 */
    public float[] resist = new float[DamageType.values().length];

    public float resist(DamageType type) {
        return resist[type.ordinal()];
    }

    /** 开关。 */
    public boolean active = true;

    /** 破盾冷却总时长（秒）；破盾（容量归零）后需等待此时间才能重新回充。 */
    public float cooldownMax;

    /** 当前破盾冷却剩余时间（秒）；0 = 不在冷却。 */
    public float cooldown;

    @Override
    public ShieldFieldAbility onCreate(Entity e) {
        register();
        this.e = e;
        return (ShieldFieldAbility) super.onCreate(e);
    }

    @Override
    public Entity owner() {
        return e;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        this.active = enabled;
    }

    public ShieldFieldAbility(float max, float radius) {
        super("shieldfield");
        this.max = max;
        this.current = max;
        this.radius = radius;
        this.toggleable = true;
        syncFrames();
    }

    /** 把"每秒"数值同步到"每帧"（update 时再同步一次以支持外部改字段）。 */
    private void syncFrames() {
        regenFrame = regen / 60f;
        costFrame = energyCost / 60f;
    }

    @Override
    public boolean isActive() {
        return active && current > 0f;
    }

    @Override
    public float energyUse() {
        return active ? costFrame : 0;
    }

    @Override
    public void update(Entity e, float dt) {
        if (!active)
            return;
        syncFrames();
        if (costFrame > 0 && e.energy <= 0) {
            active = false; // 能量耗尽自动关闭
            return;
        }
        if (cooldown > 0) {
            // 破盾冷却中：不回充，仅递减计时
            cooldown = Math.max(0f, cooldown - dt);
        } else {
            current = Math.min(max, current + regenFrame * dt);
        }
    }

    /** 减伤机制（与单体护盾一致）：按护盾强度百分比减伤， 支持破盾（无视强度减伤）与穿盾（直接穿过）。 */
    @Override
    public float applyDamage(
            Entity e, float damage, DamageType type, boolean breakShield, boolean bypassShield) {
        // 穿盾：护盾完全不拦截，伤害直接穿过
        if (bypassShield)
            return damage;
        if (!active || current <= 0)
            return damage;

        float p = current / max;
        float strength = p * maxStrength;
        // 破盾：无视护盾强度减伤（全伤害扣盾）
        float reduction = breakShield ? 1f : (1f / strength);
        float actual = damage * type.shieldMult * (1f - resist(type)) * reduction;
        // 破盾瞬间（从有盾到归零）触发一次冷却，避免冷却期间每帧刷新计时
        if (current > 0 && current - actual <= 0) {
            cooldown = cooldownMax;
        }
        current = Math.max(0f, current - actual);

        return 0; // 破盾溢出不传递
    }

    @Override
    public boolean onBullet(Bullet b) {
        if (e.team == null || e == null || b.team == null) {
            return false;
        }
        if (b.owner.team == e.team) {
            return false;
        }
        float remaining = applyDamage(e, b.type.damage, b.type.damageType, b.type.breakShield, b.type.bypassShield);
        return remaining <= 0f;
    }

    public float capacity() {
        return active ? current : 0f;
    }

    @Override
    public void hitbox(Rect out) {
        out.set(e.x - radius, e.y - radius, radius * 2f, radius * 2f);
    }

    public float capacityMax() {
        return max;
    }

    /** 当前护盾强度（= 当前比例 × 最大强度）。 */
    @Override
    public float strength() {
        return max <= 0f ? 0f : (current / max) * maxStrength;
    }

    /** 最大护盾强度（满盾时的强度）。 */
    @Override
    public float maxStrength() {
        return maxStrength;
    }

    /** 护盾对各类伤害的百分比抗性数组（0~1），索引 = DamageType.ordinal()。 */
    @Override
    public float[] resist() {
        return resist;
    }

    /** 护盾回充速率（每秒设计值）。 */
    @Override
    public float regen() {
        return regen;
    }

    /** 护盾耗能速率（每秒设计值）。 */
    @Override
    public float energyCost() {
        return energyCost;
    }

    /** 当前护盾比例（0 ~ 1）。 */
    @Override
    public float percent() {
        return max <= 0f ? 0f : current / max;
    }

    /** 当前破盾冷却剩余时间（秒）。 */
    @Override
    public float cooldown() {
        return cooldown;
    }

    /** 破盾冷却总时长（秒）。 */
    @Override
    public float cooldownMax() {
        return cooldownMax;
    }

    @Override
    public void stats(StatStack stack) {
        StatData group = StatData.with(localizedName, StatType.function);
        group.add(StatData.with(description).setLevel(1));

        group
                .add(StatData.with(Stat.shieldMax, max))
                .add(StatData.with(Stat.shieldStrength, maxStrength, StatUnit.percent))
                .add(StatData.with(Stat.shieldRegen, regen, StatUnit.perSecond))
                .add(StatData.with(Stat.shieldCost, energyCost, StatUnit.perSecond))
                .add(StatData.with(Stat.radius, radius));
        for (DamageType t : DamageType.values()) {
            if (t.ordinal() < resist.length) {
                group.add(
                        StatData.with(
                                Core.bundle.format(
                                        "stat.shieldResist",
                                        t.localizedName,
                                        StatUnit.percent.format(resist[t.ordinal()])))
                                .setLevel(3));
            }
        }
        stack.add(group);
    }

    @Override
    public void statAbility(StatStack stat) {
        stat.get(this, localizedName)
                .get(description).setLevel(1)
                .get(Stat.shield, current, max).setLevel(2).live = () -> current;
    }

    @Override
    public void draw(Entity e) {
        if (!active || current <= 0f)
            return;
        Draw.color(Pal.light, 0.6f);
        if (sides <= 0) {
            Lines.circle(e.x, e.y, radius);
        } else {
            Lines.poly(e.x, e.y, sides, radius, rotation);
        }
        Draw.color();
    }

    /** 点是否在力场内（正多边形或圆形）。 */
    @Override
    public boolean contains(float x, float y) {
        if (sides <= 0) {
            return Mathf.dst2(e.x, e.y, x, y) <= radius * radius;
        }
        return Intersector.isInRegularPolygon(sides, e.x, e.y, radius, rotation, x, y);
    }

    @Override
    public ShieldFieldAbility copy() {
        ShieldFieldAbility a = (ShieldFieldAbility) super.copy();
        // 数组需要手动深拷贝
        a.resist = resist.clone();
        return a;
    }

    @Override
    public void write(Writes w) {
        super.write(w);
        w.f(current);
        w.bool(active);
        w.f(cooldown);
    }

    @Override
    public void read(Reads r) {
        super.read(r);
        current = r.f();
        active = r.bool();
        cooldown = r.f();
    }
}
