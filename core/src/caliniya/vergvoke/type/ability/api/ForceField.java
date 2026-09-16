package caliniya.vergvoke.type.ability.api;

import arc.math.geom.Rect;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.base.tool.Ar;
import caliniya.vergvoke.type.Bullet;

/**
 * 力场：与子弹相关的力场能力
 *
 * <p>
 * 这个接口是和子弹的交互力场，范围性效果不用这东西
 */
public interface ForceField {

    /** 力场实体注册表：当前所有**生效**的力场实体。 由基类 update 维护，BulletProcess（子弹线程）遍历做拦截/效果。 */
    Ar<ForceField> force = new Ar<>(false, 32);

    /** 覆盖力场的 AABB（四叉树粗筛候选子弹）。 */
    void hitbox(Rect out);

    default void register() {
        synchronized (force) {
            if (!force.contains(this, true))
                force.add(this);
        }
    }

    Entity owner();

    /** 力场当前是否生效（默认 true，实现类按自身状态覆写）。 */
    default boolean isActive() {
        return true;
    }

    /** 点是否在力场内（正多边形或圆形）。 */
    boolean contains(float x, float y);

    /** 子弹进入力场回调。返回 true 表示**拦截**该子弹（由子弹系统移除）。 默认放行，子类覆写实现具体效果。 */
    boolean onBullet(Bullet b);
}
