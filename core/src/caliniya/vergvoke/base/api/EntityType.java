package caliniya.vergvoke.base.api;

import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.type.*;

public interface EntityType {

    Entity create(Entity entity);

    void update(Entity entity);

    default void sync(Entity e, float dt) {
        
        float cool = heatSpeed / 60f * dt;
        if (e.locked) {
            heat -= cool;
            if (heat <= 0f) {
                heat = 0f;
                locked = false;
            }
            return;
        }

        heat = Math.max(0f, heat - cool);

        float use = 0f;
        for (Ability a : abilities) {
            use += a.energyUse();
        }
        float net = energyRegen / 60f - use; // energyRegen 以秒设计，这里转成每帧
        if (net != 0f) {
            energy = Math.min(energyMax, energy + net * dt);
        }
        for (Ability a : abilities) {
            a.update(this, dt);
        }
        // 强化模组：只需每帧更新的（实现 Updatable 接口的）
        for (Updatable u : updatableEnhancements) {
            u.update(this, dt);
        }
    } 

    void draw(Entity entity);

}