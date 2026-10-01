package caliniya.vergvoke.type.def.comps;

import arc.math.Angles;
import arc.util.io.Reads;
import arc.util.io.Writes;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.type.Bullet;
import caliniya.vergvoke.type.type.BulletType;

/**
 * 炮塔战斗组件（建筑用，C+B 方案）：TargetComp 管「打谁」，本组件管「怎么打」。
 *
 * <p>状态（reload）与配置（reloadTime / rotateSpeed / bullet，放置时从 Turret 类型拷贝进实例）
 * 都在组件上；索敌完全交给 TargetComp（失效/重搜/半径都是它的事），本组件只消费 target。
 *
 * <p>{@code self} 指向宿主实体：组件被拍平后源码里拿不到 this-as-Entity，
 * 而开火给子弹记击杀归属需要实体引用，工厂里赋 {@code b.self = b}。
 */
@Component(index = 6, name = "Turret")
public class TurretComp {

    /** 宿主实体（工厂里赋 b.self = b）。 */
    public Entity<?, ?> self;

    /** 装填进度。 */
    public float reload;

    // --- 配置（放置时从 Turret 类型拷贝，B 方案：配置进实例）---
    public float reloadTime = 10f;
    public float rotateSpeed = 500f;
    public BulletType bullet;

    // --- 借用 ---
    @Import
    public Entity<?, ?> target; // TargetComp 维护
    @Import
    public float rotation; // Entity 基类
    @Import
    public float x; // Entity 基类
    @Import
    public float y; // Entity 基类

    @Updata
    public void update(float delta) {
        if (target == null) {
            return; // 没目标不转不装填（与原 Turret 行为一致）
        }
        float targetAngle = Angles.angle(x, y, target.x, target.y);
        rotation = Angles.moveToward(rotation, targetAngle, rotateSpeed * delta);
        reload += delta;
        if (reload >= reloadTime && Angles.angleDist(rotation, targetAngle) < 5f) {
            shoot(rotation);
            reload = 0f;
        }
    }

    private void shoot(float angle) {
        if (bullet != null) {
            Bullet.create(bullet, self, x, y, angle, 0f, 0f);
        }
    }

    /** 存档写：炮塔只要状态量（配置 reloadTime/rotateSpeed/bullet 由 {@code type} 重建）。 */
    @Write
    public void write(Writes w) {
        w.f(rotation);
        w.f(reload);
    }

    @Read
    public void read(Reads r) {
        rotation = r.f();
        reload = r.f();
    }
}
