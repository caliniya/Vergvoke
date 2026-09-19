package caliniya.vergvoke.system.render;

import arc.*;
import caliniya.vergvoke.base.tool.Ar;
import caliniya.vergvoke.type.ability.Ability;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.type.Bullet;
import caliniya.vergvoke.game.data.WorldData;
import caliniya.vergvoke.system.System;
import caliniya.vergvoke.system.world.BulletProcess;

public class UnitRender extends System<UnitRender> {

    // 调试开关
    public static boolean debug = true;

    public static Ar<Bullet> temp = new Ar<Bullet>(false, 1000);

    @Override
    public UnitRender init() {
        this.index = 13;
        Events.run(EventType.events.EnterUV, () -> paused = true);
        Events.run(EventType.events.ExitUV, () -> paused = false);
        return super.init(false, false);
    }

    @Override
    public void update(float delta) {
        if (!inited || paused)
            return;
        // 绘制单位
        WorldData.units.each(
                u -> {
                    if (shouldDraw(u.x, u.y, u.size * 2)) {
                        u.draw();
                        // 绘制能力视觉效果（力场等）
                        for (Ability a : u.abilities)
                            a.draw(u);
                        // 血条由单位类型绘制（可覆写）
                        u.type.drawHealthBar(u);
                        // 调用单位内部的调试绘制方法
                        if (debug) {
                            u.type.drawDebug(u);
                        }
                    }
                });

        // 绘制子弹
        // 用与 BulletProcess 相同的固定锁对象，确保与逻辑线程的缓冲交换互斥，
        // 避免拷到正在被清空/重填的缓冲导致子弹闪烁。
        temp.clear();
        synchronized (BulletProcess.it.BULLET_LOCK) {
            temp.addAll(WorldData.bullets);
        }
        temp.each(
                b -> {
                    if (b.recycled)
                        return;
                    if (shouldDraw(b.x, b.y, b.type.size)) {
                        b.type.draw(b);
                    }
                });
    }

    // 通用的剔除方法
    private boolean shouldDraw(float x, float y, float size) {
        float viewX = Core.camera.position.x;
        float viewY = Core.camera.position.y;
        float buffer = debug ? 500f : size;
        float w = Core.camera.width / 2f + buffer;
        float h = Core.camera.height / 2f + buffer;
        return x > viewX - w && x < viewX + w && y > viewY - h && y < viewY + h;
    }
}
