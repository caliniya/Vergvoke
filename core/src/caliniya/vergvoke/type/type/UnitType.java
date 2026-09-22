package caliniya.vergvoke.type.type;

import arc.*;
import arc.util.*;
import arc.util.pooling.*;
import arc.graphics.*;
import arc.math.geom.*;
import arc.graphics.g2d.*;
import caliniya.vergvoke.core.meta.ui.Pal;
import caliniya.vergvoke.type.ability.Ability;
import caliniya.vergvoke.ui.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.base.api.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.tool.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.core.meta.stat.Stat;
import caliniya.vergvoke.core.meta.stat.StatType;
import caliniya.vergvoke.core.meta.stat.StatUnit;
import caliniya.vergvoke.base.ecs.*;


public class UnitType extends ContentType implements EntityType, DrawType<Unit>, TechNodeContent {

    public float speedt = 60f, // 格每秒
            health = 100f,
            speed, // 像素每帧（load() 里由 speedt 换算出来）
            rotationSpeend = 1f // 旋转速度(单位帧每度)
    ;

    // 物理数据，若碰撞盒为空 则使用size进行填充
    public float[] hitbox = null;
    public float size = 100f;

    // 单位的容量，使用通用的模块规则
    public int itemCap = 50;
    public float liquidCap;
    public float powerCap;

    // 防护（类型默认，实例可覆盖）
    public float armorMax; // 护甲容量上限
    public float armorValue; // 护甲强度（固定减伤）

    /** 护甲对各类伤害的百分比抗性（0~1），索引 = DamageType.ordinal()。 */
    public float[] armorResist = new float[DamageType.values().length];

    // 能量回充速率（每秒，类型默认）
    public float energyRegen;
    public float energyMax;

    public Ar<Ability> abilities = new Ar<Ability>();

    public Ar<WeaponType> weapons = new Ar<WeaponType>();

    // 渲染资源
    public TextureRegion region, cell;

    public UnitType(String name) {
        super(name, CType.Unit);
    }

    @Override
    public TechNodeContent[] requirements() {
        return requirements; // ContentType 里的前置字段（默认 null）
    }

    // 加载资源 (在 Assets 加载完成后调用)
    public void load() {
        this.speed = (speedt * WorldData.TILE_SIZE) / 60f;
        region = Core.atlas.find(name, "white");
        cell = Core.atlas.find(name + "-cell", "air");
        for (WeaponType weapon : weapons) {
            weapon.load(name);
        }
        // 基础
        stat.add(Stat.healthMax, health);
        stat.add(Stat.speed, speedt);
        stat.add(Stat.rotateSpeed, rotationSpeend);
        stat.add(Stat.energyMax, energyMax);
        stat.add(Stat.energyRegen, energyRegen);
        // 防护
        stat.add(Stat.armorMax, armorMax);
        stat.add(Stat.armorValue, armorValue);
        for (DamageType t : DamageType.values()) {
            if (t.ordinal() < armorResist.length) {
                stat.add(
                        Core.bundle.format(
                                "stat.armorResist",
                                t.localizedName,
                                StatUnit.percent.format(armorResist[t.ordinal()])),
                        StatType.protect);
            }
        }
        abilities.each(e -> e.stats(stat));
    }

    /** {@link EntityType}：用本类型配置填充实体（生成工厂 create(type) 会调用）。 */
    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Unit create(TeamTypes team, float x,float y) {

        Unit u = Pools.obtain(Unit.class,Unit::new);

        // Entity<?> 的 type 是捕获类型，这里经 raw 写入本类型
        ((Entity) u).type = this;
        u.maxHealth = health;
        u.health = health;
        u.armorMax = armorMax;
        u.armorValue = armorValue;
        if (armorResist != null) {
            u.armorResist = armorResist.clone();
        }
        u.energyMax = energyMax;
        u.energy = energyMax;
        u.energyRegen = energyRegen;
        u.abilities.clear();
        for (Ability a : abilities) {
            u.addAbility(a.copy());
        }
            u.size = size;
            if (weapons != null) {
                if (u.weapons == null) {
                    u.weapons = new Ar<>();
                } else {
                    u.weapons.clear();
                }
                for (WeaponType wt : weapons) {
                    u.weapons.add(new Weapon(wt, u));
                }
        }
        return u;
    }

    /** {@link EntityType}：类型级每帧钩子（委托 {@link #update(Unit, float)}）。 */
    @Override
    public void update(Entity<?> entity, float dt) {
        if (entity instanceof Unit u) {
            update(u, dt);
        }
    }

    /** {@link EntityType}：类型级绘制（委托 {@link #draw(Unit)}）。 */
    @Override
    public void draw(Entity<?> entity) {
        if (entity instanceof Unit u) {
            draw(u);
        }
    }

    public void draw(Unit u) {
        if (u.isSelected) {
            Draw.color(Color.green);
            Lines.stroke(2f);
            Lines.circle(u.x, u.y, u.size + 4);
            Draw.color();
        }

        // 绘制资源在类型上，实体不持有 region/cell
        Draw.rect(region, u.x, u.y, u.rotation);
        if (cell != null) {
            Draw.rect(cell, u.x, u.y, u.rotation);
        }

        if (u.weapons != null) {
            for (Weapon weapon : u.weapons) {
                weapon.type.draw(weapon);
            }
        }
        Draw.color();
    }

    /**
     * 绘制单位血条（默认样式）：单位右边缘垂直条， **固定分段 + 段内独立填充**。
     *
     * <p>
     * 1. 固定分段：总容量 = 核心Max + 护甲Max + 护盾原始Max， 各段宽 = 条长 × 该层Max/总容量（段位置固定，互不影响）；
     *
     * <p>
     * 2. 段内独立渲染：每段从左到右按「当前 / 该层最大」填充， 左侧固定、右侧随当前值缩；
     *
     * <p>
     * 3. 护盾段比例用等效容量：比例 = 当前/最大（接近线性）。 子类可覆写此方法定制血条。
     */
    public void drawHealthBar(Unit u) {

        float coreMax = Math.max(0f, u.maxHealth);
        float core = Math.max(0f, u.health);
        float armorMax = Math.max(0f, u.armorMax);
        float armor = Math.max(0f, u.armor);
        // 护盾段 = 所有护盾能力容量之和（单体护盾 + 力场等）
        float shieldMax = Math.max(0f, u.totalShieldMax());
        float shieldCur = Math.max(0f, u.totalShield());

        float totalMax = coreMax + armorMax + shieldMax;
        if (totalMax <= 0f)
            return;

        float barLen = u.size * 1.5f;
        float barW = 8f;
        // 以单位中心为原点：血条贴右边缘（半径 0.5×size），垂直居中于单位中心
        float startX = u.x + u.size * 0.5f;
        float startY = u.y - u.size * 0.75f; // 下端 -0.75×size，上端 +0.75×size（对称）

        // 底色
        Draw.color(Color.darkGray);
        Fill.rect(startX + barW / 2f, startY + barLen / 2f, barW, barLen);

        // 固定分段
        float coreSeg = barLen * coreMax / totalMax;
        float armorSeg = barLen * armorMax / totalMax;
        float shieldSeg = barLen * shieldMax / totalMax;

        // 核心段（最下，红）
        if (coreSeg > 0f && core > 0f) {
            float h = coreSeg * (core / coreMax);
            Draw.color(Color.scarlet);
            Fill.rect(startX + barW / 2f, startY + h / 2f, barW, h);
        }

        // 护甲段（中，白）
        if (armorSeg > 0f && armor > 0f) {
            float h = armorSeg * (armor / armorMax);
            Draw.color(Color.lightGray);
            Fill.rect(startX + barW / 2f, startY + coreSeg + h / 2f, barW, h);
        }

        // 护盾段（最上，蓝）：等效容量比例（= 当前/最大，线性）
        if (shieldSeg > 0f && shieldCur > 0f) {
            float h = shieldSeg * (shieldCur / shieldMax);
            Draw.color(Color.sky);
            Fill.rect(startX + barW / 2f, startY + coreSeg + armorSeg + h / 2f, barW, h);
        }

        // --- 能量条 + 热量条（血条右侧副条，从下往上填充）---
        float gap = -1f; // 条间距
        float energyW = 5f; // 能量条宽度
        float heatW = 3f; // 热量条宽度
        float energyX = startX + barW + gap; // 能量条左缘

        if (u.energyMax > 0f) {
            float energy = Math.max(0f, u.energy);
            float energyH = barLen * Math.min(1f, energy / u.energyMax);

            // 底色（空条，让玩家知道有这个槽位）
            Draw.color(Color.darkGray);
            Fill.rect(energyX + energyW / 2f, startY + barLen / 2f, energyW, barLen);
            // 金色填充，从下往上
            if (energyH > 0f) {
                Draw.color(Pal.light);
                Fill.rect(energyX + energyW / 2f, startY + energyH / 2f, energyW, energyH);
            }
        }

        // 热量条：仅有过热机制（heatable 且上限 > 0）的单位显示，位于能量条右侧
        if (u.heatable && u.heatMax > 0f) {
            float heatX = u.energyMax > 0f ? energyX + energyW + gap : energyX;
            float heatH = barLen * Math.min(1f, Math.max(0f, u.heat) / u.heatMax);

            Draw.color(Color.darkGray);
            Fill.rect(heatX + heatW / 2f, startY + barLen / 2f, heatW, barLen);
            if (heatH > 0f) {
                Draw.color(Color.orange);
                Fill.rect(heatX + heatW / 2f, startY + heatH / 2f, heatW, heatH);
            }
        }

        Draw.color();
    }

    public void drawDebug(Unit u) {

        Draw.color(Color.yellow);
        Lines.stroke(2f);

        if (u.hitboxData != null) {
            for (int i = 0; i < u.hitboxData.length; i += 3) {
                float cx = u.hitboxData[i];
                float cy = u.hitboxData[i + 1];
                float s = u.hitboxData[i + 2];
                Lines.rect(cx - s / 2f, cy - s / 2f, s, s);
            }
        }

        // 绘制计算出的外接圆 (新增，用于验证 size 计算是否正确)
        Draw.color(Color.sky);
        float radius = u.size / 2f;
        Lines.circle(u.x, u.y, radius);
        Draw.color(Color.yellow); // 还原颜色

        if (Math.abs(u.speedX) > 0.001f || Math.abs(u.speedY) > 0.001f) {
            Draw.color(Color.magenta);
            float scale = 20f;
            Lines.line(u.x, u.y, u.x + u.speedX * scale, u.y + u.speedY * scale);
            Fonts.def.draw(StringApi.format(u.speedX + " " + u.speedY), u.x, u.y + size + 8f, Align.center);
        }

        if (u.targetX != 0 || u.targetY != 0) {
            Draw.color(Color.orange);
            Lines.line(u.x, u.y, u.targetX, u.targetY);
            float s = 8f;
            Lines.line(u.targetX - s, u.targetY - s, u.targetX + s, u.targetY + s);
            Lines.line(u.targetX - s, u.targetY + s, u.targetX + s, u.targetY - s);
        }
        if (u.path != null && !u.path.isEmpty()) {
            Draw.color(Color.cyan);

            float lastX = u.x;
            float lastY = u.y;

            for (int i = u.pathIndex; i < u.path.size; i++) {
                Point2 p = u.path.get(i);
                float wx = p.x * WorldData.TILE_SIZE + WorldData.TILE_SIZE / 2f;
                float wy = p.y * WorldData.TILE_SIZE + WorldData.TILE_SIZE / 2f;

                Lines.line(lastX, lastY, wx, wy);
                Fill.square(wx, wy, 3f);
                lastX = wx;
                lastY = wy;
            }
        }

        for (Weapon weapon : u.weapons) {
            weapon.type.drawDebug(weapon);
        }
        Draw.color();
    }

    public void update(Unit u, float dt) {
        // 实体侧逻辑跑完后的类型级钩子；默认空
    }

    public void addWeapons(WeaponType... newWeapons) {
        for (WeaponType weapon : newWeapons) {
            // 添加主武器
            weapon.isMirror = false;
            weapons.add(weapon);

            // 处理镜像
            if (weapon.mirror) {
                WeaponType copy = weapon.copy();
                copy.flip();
                weapon.otherSide = weapons.size;
                copy.otherSide = weapons.size - 1;

                weapons.add(copy);
            }
        }
    }

    @Override
    public void remove(Entity<?> entity) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'remove'");
    }
}
