package caliniya.vergvoke.base.api;

import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.type.ability.Ability;

public interface EntityType {

    Entity create(Entity entity);

    void update(Entity entity);

    default void sync(Entity e, float dt) {
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
            a.update(this, dt);
        }
        // 强化模组：只需每帧更新的（实现 Updatable 接口的）
        for (Updatable u : updatableEnhancements) {
            u.update(this, dt);
        }
    }

    void draw(Entity entity);

}
