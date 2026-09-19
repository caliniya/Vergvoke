package caliniya.vergvoke.type;

import arc.util.*;
import arc.math.*;
import arc.util.io.*;
import arc.math.geom.*;
import arc.graphics.g2d.*;
import arc.util.pooling.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.type.ability.Ability;
import caliniya.vergvoke.type.enhance.EnhancementType;
import caliniya.vergvoke.content.*;
import caliniya.vergvoke.base.tool.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.type.type.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.type.module.*;

public class Unit extends Entity {

    public UnitType type;
    public TeamData teamData;

    public Ar<Weapon> weapons = new Ar<>();
    public Weapon mainFixedWeapon = null;

    // --- 物理属性 ---
    public volatile float speedX, speedY, angle;

    /** 击退速度衰减速率（0~1，越大停得越快）。 */
    public float knockDamp = 0.1f;

    public float rotationSpeed;
    public float rotation;
    public float angleToTarget, distToTarget;

    public boolean canShoot = true;

    // --- 导航属性 ---
    public float targetX, targetY;
    public Ar<Point2> path;
    public int pathIndex = 0;
    public boolean pathed;
    public boolean velocityDirty = true;

    // --- 状态属性 ---
    public boolean isSelected = false;
    public boolean moving = false;
    public float size, speed;
    public TextureRegion region, cell;
    public float pathFindCooldown = 0f;

    // --- 碰撞体积缓存 (世界坐标) ---
    /** 存储旋转后的碰撞盒数据。 格式: [世界X1, 世界Y1, 尺寸1, 世界X2, 世界Y2, 尺寸2, ...] */
    public float[] hitboxData;

    protected Unit() {
    }

    public static Unit create(TeamTypes team, UnitType type, float x, float y) {
        Unit u = Pools.obtain(Unit.class, Unit::new);
        u.type = type;
        u.team = team;
        u.teamData = team.data();
        u.x = x;
        u.y = y;
        u.item = new ItemModule(type.itemCap);
        if (type.liquidCap > 0)
            u.liquid = new LiquidModule(type.liquidCap);
        if (type.powerCap > 0)
            u.power = new PowerModule(type.powerCap);
        u.init();
        u.id = Entities.assignID();
        u.updateTeamData();
        u.updateHitbox();
        Entities.add(u);
        return u;
    }

    public static Unit create(UnitType type) {
        Unit u = Pools.obtain(Unit.class, Unit::new);
        u.type = type;
        u.item = new ItemModule(type.itemCap);
        if (type.liquidCap > 0)
            u.liquid = new LiquidModule(type.liquidCap);
        if (type.powerCap > 0)
            u.power = new PowerModule(type.powerCap);
        u.init();
        return u;
    }

    public void init() {
        if (this.type == null) {
            this.type = UnitTypes.test;
            Log.err(this.toString() + "@ No unitTpye used test");
        }

        // 对象池复用防污染：清空能力与战斗基础属性
        abilities.clear();
        armor = 0;
        armorMax = 0;
        armorValue = 0;
        energy = 0;
        energyMax = 0;
        energyRegen = 0;

        this.speed = this.type.speed; // speed 是像素/帧（带 t 的 type.speedt 是格每秒）
        this.rotationSpeed = this.type.rotationSpeend;
        this.region = this.type.region;
        this.cell = this.type.cell;
        this.maxHealth = this.type.health;
        this.health = this.type.health;
        this.armorMax = this.type.armorMax;
        this.armorValue = this.type.armorValue;
        this.armorResist = this.type.armorResist.clone();
        this.energyMax = this.type.energyMax;
        this.energyRegen = this.type.energyRegen;
        this.size = this.type.size;
        // 当前护甲初始为满甲（无护甲时自然为 0）
        this.armor = this.armorMax;

        this.type.abilities.each(a -> this.addAbility(a.copy()));

        // --- 初始化碰撞数据数组 ---
        if (type.hitbox != null) {
            hitboxData = new float[type.hitbox.length];
            // 自动计算外接圆直径
            calculateBoundingSize();
        } else {
            hitboxData = new float[3];
            // 没有自定义体积时，使用类型定义的默认尺寸
            this.size = this.type.size;
        }

        canShoot = true; // 单位一般情况都是可以射击的

        weapons.clear();
        mainFixedWeapon = null;
        for (WeaponType wType : type.weapons) {
            Weapon w = new Weapon(wType, this);
            weapons.add(w);
            if (!wType.rotate && mainFixedWeapon == null) {
                mainFixedWeapon = w;
            }
        }

        this.rotation = 0f;
        this.speedX = 0f;
        this.speedY = 0f;

        this.targetX = this.x;
        this.targetY = this.y;
    }

    /** 根据自定义碰撞体积计算最小外接圆直径 并赋值给 size */
    private void calculateBoundingSize() {
        if (type.hitbox == null)
            return;

        float maxRadiusSq = 0f;

        // 遍历所有碰撞方块，找到离原点最远的角点距离
        for (int i = 0; i < type.hitbox.length; i += 3) {
            float cx = type.hitbox[i];
            float cy = type.hitbox[i + 1];
            float s = type.hitbox[i + 2];

            // 正方形角点到中心的距离 (勾股定理的一半)
            float cornerDist = s / 2f * Mathf.sqrt2;

            // 中心点到原点的距离
            float centerDist = Mathf.len(cx, cy);

            // 最远点距离 = 中心距 + 角距
            float totalDist = centerDist + cornerDist;

            if (totalDist * totalDist > maxRadiusSq) {
                maxRadiusSq = totalDist * totalDist;
            }
        }

        // size 表示直径
        this.size = Mathf.sqrt(maxRadiusSq) * 2f;
    }

    @Override
    public void reset() {
        super.reset();
        this.type = null;
        this.speedX = 0;
        this.speedY = 0;
        this.targetX = 0;
        this.targetY = 0;
        this.rotation = 0;
        this.id = Entities.freeID(this.id);
        this.velocityDirty = true;

        this.isSelected = false;
        this.moving = false;
        this.hitboxData = null;

        this.pathFindCooldown = 0;
        if (path != null)
            path.clear();
    }

    @Override
    public void kill() {
        remove();
    }

    @Override
    public void remove() {
        WorldData.units.remove(this);
        Entities.remove(this);
        this.team = null;
        this.teamData = null;
        isSelected = false;
        // 死亡/移除时从指挥列表剔除
        CommandData.checkedUnits.remove(this);
        Pools.free(this);
    }

    @Override
    public float hitboxSize() {
        return this.size;
    }

    @Override
    public void update(float dt) {
        // 击退冲量：复制副本 → 施加位移 → 平滑衰减（写回共享字段）
        float kx = knockX, ky = knockY;
        if (kx != 0f || ky != 0f) {
            // 位移并 clamp 到地图内（防止击退出界无法指挥）
            float maxX = WorldData.world.W * WorldData.TILE_SIZE;
            float maxY = WorldData.world.H * WorldData.TILE_SIZE;
            x = Mathf.clamp(x + kx * dt, 0f, maxX);
            y = Mathf.clamp(y + ky * dt, 0f, maxY);
            knockX = Mathf.lerpDelta(knockX, 0f, knockDamp);
            knockY = Mathf.lerpDelta(knockY, 0f, knockDamp);
            velocityDirty = true;
            // 四叉树位置由 GameProcess 遍历后统一短写锁更新（避免读锁内写锁）
            updateHitbox();
        }
        updateBase(dt);

        if (locked)
            return;

        float oldX = this.x;
        float oldY = this.y;
        float oldRot = this.rotation;

        distToTarget = Mathf.dst(x, y, targetX, targetY);

        if (path == null && distToTarget < 2f) {
            x = targetX;
            y = targetY;
            distToTarget = 0f;
            speedX = 0;
            speedY = 0;
            moving = false;
        } else {
            x += speedX * dt;
            y += speedY * dt;
            moving = true;
        }

        if (distToTarget > 1f) {
            angleToTarget = Angles.angle(x, y, targetX, targetY);
        }

        if (canShoot) {
            if (mainFixedWeapon != null && distToTarget > 1f) {
                rotation = Angles.moveToward(rotation, angleToTarget - 90, rotationSpeed * dt);
            } else {
                if (Mathf.len(speedX, speedY) > 0.01f && distToTarget > 1f) {
                    rotation = Angles.moveToward(rotation, angle - 90, rotationSpeed * dt);
                }
            }
        } else {
            if (Mathf.len(speedX, speedY) > 0.01f) {
                rotation = Angles.moveToward(rotation, angle - 90, rotationSpeed * dt);
            }
        }

        // 类型级每帧钩子（模组等"不新建实体就扩展行为"的入口）：实体自身逻辑跑完后调用
        type.update(this, dt);

        moving = (x != oldX || y != oldY);
        boolean rotated = !Mathf.equal(rotation, oldRot);

        if (moving || rotated) {
            updateHitbox();
        }
        if (moving) {
            velocityDirty = true; // 标记：由 GameProcess 统一更新四叉树
        }
    }

    private void updateHitbox() {
        if (hitboxData == null)
            return;

        if (type.hitbox != null) {
            float[] src = type.hitbox;
            float[] dst = hitboxData;

            for (int i = 0; i < src.length; i += 3) {
                float localX = src[i];
                float localY = src[i + 1];
                float boxSize = src[i + 2];

                Vec2 rotated = Tmp.v1.trns(rotation, localX, localY);

                dst[i] = x + rotated.x;
                dst[i + 1] = y + rotated.y;
                dst[i + 2] = boxSize;
            }
        } else {
            hitboxData[0] = x;
            hitboxData[1] = y;
            hitboxData[2] = size;
        }
    }

    public boolean contains(float px, float py) {
        if (hitboxData == null)
            return false;

        for (int i = 0; i < hitboxData.length; i += 3) {
            float cx = hitboxData[i];
            float cy = hitboxData[i + 1];
            float s = hitboxData[i + 2];

            float halfSize = s / 2f;

            if (px >= cx - halfSize
                    && px <= cx + halfSize
                    && py >= cy - halfSize
                    && py <= cy + halfSize) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void draw() {
        type.draw(this);
    }

    @SuppressWarnings("unused")
    public void updateWeapons(float dt) {
        float aimX = targetX;
        float aimY = targetY;

        if (targetX == 0 && targetY == 0) {
            aimX = x + 100;
            aimY = y;
        }

        for (Weapon weapon : weapons) {
            // 过热锁定期间无法射击
            weapon.update(dt, canShoot && !overheated());
        }
    }

    public void impuse(float knockX, float knockY) {
        this.x += knockX;
        this.y += knockY;
        this.velocityDirty = true;
    }

    @Override
    public void write(Writes w) {
        item.write(w);
        if (liquid != null) {
            w.bool(true);
            liquid.write(w);
        } else {
            w.bool(false);
        }
        if (power != null) {
            w.bool(true);
            power.write(w);
        } else {
            w.bool(false);
        }
        w.f(x);
        w.f(y);
        w.f(rotation);
        w.f(health);
        w.f(armor);
        w.f(energy);
        w.f(targetX);
        w.f(targetY);
        w.b(team.ordinal());
        w.i(id);
        // 能力运行时状态（类型定义参数由 copy() 提供，这里只存开关/当前容量等）
        w.i(abilities.size);
        for (Ability a : abilities) {
            a.write(w);
        }
        // 强化模组（运行时安装）：类型 internalName + 实例数据
        w.i(enhancements.size);
        for (Enhancement enh : enhancements) {
            w.str(enh.type.internalName);
            enh.write(w);
        }
    }

    @Override
    public void read(Reads r) {
        item.read(r);
        if (r.bool()) {
            if (liquid == null)
                liquid = new LiquidModule(type.liquidCap);
            liquid.read(r);
        }
        if (r.bool()) {
            if (power == null)
                power = new PowerModule(type.powerCap);
            power.read(r);
        }
        this.x = r.f();
        this.y = r.f();
        this.rotation = r.f();
        this.health = r.f();
        this.armor = r.f();
        this.energy = r.f();
        this.targetX = r.f();
        this.targetY = r.f();
        byte teamId = r.b();
        this.id = Entities.checkoutID(r.i());

        if (teamId >= 0 && teamId < TeamTypes.values().length) {
            this.team = TeamTypes.values()[teamId];
        } else {
            this.team = TeamTypes.Abort;
        }

        teamData = team.data();

        // 能力运行时状态（数量防御：以存档为准，截断到当前能力列表）
        int abilityCount = r.i();
        int count = Math.min(abilityCount, abilities.size);
        for (int i = 0; i < count; i++) {
            abilities.get(i).read(r);
        }

        // 强化模组：按类型 internalName 找类型 → create() 重建实例 → 读数据 → 完整挂载
        int enhCount = r.i();
        for (int i = 0; i < enhCount; i++) {
            String name = r.str();
            EnhancementType t = Contents.get(name, EnhancementType.class);
            if (t != null) {
                Enhancement enh = t.create();
                enh.read(r);
                addEnhancement(enh);
            } else {
                Log.warn("Unknown enhancement in save: @", name);
            }
        }

        this.speedX = 0;
        this.speedY = 0;

        this.path = null;
        this.pathIndex = 0;
        this.pathed = false;
        this.velocityDirty = true;
        Entities.add(this);
        WorldData.moveunits.add(this);
        WorldData.units.move(this, x, y);
        updateHitbox();
    }

    public void knock(float dir, float force) {
        knockX += Mathf.cosDeg(dir) * force;
        knockY += Mathf.sinDeg(dir) * force;
    }

    public void updateTeamData() {
        if (this.team == null)
            this.team = TeamTypes.Abort;
        this.teamData = this.team.data();
    }

    public void setTeam(TeamTypes newTeam) {
        if (this.team == newTeam)
            return;
        Entities.remove(this);
        this.team = newTeam;
        updateTeamData();
    }
}
