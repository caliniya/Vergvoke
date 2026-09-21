package caliniya.vergvoke.base.api;

import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.type.ability.Ability;
import caliniya.vergvoke.type.enhance.api.Updatable;

/**
 * 一种实体的类型
 *
 * <p>实体定义注解 {@code @Entity(type = XxxType.class)} 必须指向实现本接口的类；
 * 生成实体继承 {@code Entity<XxxType>}，工厂 {@code create(type)} 委托 {@link #create} 填配置。
 */
public interface EntityType {
    /** 用本类型配置填充实体（创建 / 读档后初始化），返回同一实例。 */
    Entity<?> create(float x, float y, TeamTypes team);

    /** 类型级每帧逻辑（实体自身 update 之后按需调用；类型对象共享，勿存每实例状态）。 */
    void update(Entity<?> entity, float dt);

    /** 公共战斗循环：散热/锁定、能量净回复、能力与可更新强化。 */
    default void sync(Entity<?> e, float dt) {
        float cool = e.heatSpeed / 60f * dt;
        if (e.locked) {
            e.heat -= cool;
            if (e.heat <= 0f) {
                e.heat = 0f;
                e.locked = false;
            }
            return;
        }

        e.heat = Math.max(0f, e.heat - cool);

        float use = 0f;
        for (Ability a : e.abilities) {
            use += a.energyUse();
        }
        float net = e.energyRegen / 60f - use; // energyRegen 以秒设计，这里转成每帧
        if (net != 0f) {
            e.energy = Math.min(e.energyMax, e.energy + net * dt);
        }
        for (Ability a : e.abilities) {
            a.update(e, dt);
        }
        for (Updatable u : e.updatableEnhancements) {
            u.update(e, dt);
        }
    }

    /** 类型级绘制。 */
    void draw(Entity<?> entity);

    public default void kill(Entity<?> entity) {
        entity.remove();
    }

    void remove(Entity<?> entity);

}
