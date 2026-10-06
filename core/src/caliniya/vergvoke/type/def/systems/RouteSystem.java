package caliniya.vergvoke.type.def.systems;

import arc.struct.Ar;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.base.ecs.Unit;
import caliniya.vergvoke.game.data.WorldData;
import caliniya.vergvoke.system.System;

/**
 * 导航系统：nav 线程上的薄调度层——快照 moveunits，逐单位调用组件的导航更新 （算路径、给速度向量；逻辑本体在
 * {@code RouteComp} 的 @Updata 里）。
 *
 * <p>
 * 实例由生成的 {@code Systems.startThreads()} 创建并起独立线程（60TPS）； 到达 / 不可达后把实体摘出
 * moveunits（组件源码里拿不到实体引用，只能在这里摘）。
 */
@ThreadDef(name = "route")
@SystemDef(name = "RouteSystem", thread = "route", index = 1)
public class RouteSystem extends System<RouteSystem> {

    /**
     * 全局实例（构造器赋值；实例化由 Systems.startThreads 完成）。
     */
    public static RouteSystem it;

    private final Ar<Unit> processList = new Ar<>();

    public RouteSystem() {
        it = this;
    }

    @Override
    public void update(float delta) {
        processList.clear();
        synchronized (WorldData.moveunits) {
            processList.addAll(WorldData.moveunits);
        }

        for (int i = 0; i < processList.size; ++i) {
            Unit u = processList.get(i);

            if (u == null || u.health <= 0) {
                synchronized (WorldData.moveunits) {
                    WorldData.moveunits.remove(u);
                }
                continue;
            }

            u.updateRouteComp(delta);

            // 到达 / 不可达：组件已清速度并置空路径（path == null），摘出 moveunits
            if (u.path == null) {
                synchronized (WorldData.moveunits) {
                    WorldData.moveunits.remove(u);
                }
            }
        }
    }
}
