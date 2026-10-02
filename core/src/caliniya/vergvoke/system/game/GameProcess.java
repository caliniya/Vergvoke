package caliniya.vergvoke.system.game;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.base.ecs.Building;
import caliniya.vergvoke.base.ecs.EntityArs;
import caliniya.vergvoke.base.ecs.Unit;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.base.tool.*;
import caliniya.vergvoke.game.Entities;
import caliniya.vergvoke.game.data.*;

// 在这里进行主线程游戏内容的更新
@SystemDef(name = "GameProcess", thread = "main", index = 5)
public class GameProcess extends caliniya.vergvoke.system.System<GameProcess> {

    /** 全局实例（由 Data.loadSystems() 创建）。 */
    public static GameProcess it;

    public Ar<Unit> deadUnits;
    public Ar<Building> deadBuildings;
    /** 待销毁实体（伤害结算可能在后台线程登记，真正的销毁只在这里做）。 */
    public Ar<Entity> freshKilled;

    @Override
    public GameProcess init() {
        index = 5;
        deadUnits = new Ar<>();
        deadBuildings = new Ar<>();
        freshKilled = new Ar<>();
        return super.init(false);
    }

    @Override
    public void update(float delta) {
        // 先处理刚判死的实体（延迟最小化，防止血量变负才死）。
        // 这是**唯一的销毁出口**：伤害结算可能在 BulletProcess 线程发生，那里只登记不销毁，
        // 注销容器 / 回收 ID / 回池一律在这里（主线程）做。
        Entities.drainDead(freshKilled);
        for (Entity e : freshKilled) {
            e.kill();
        }
        freshKilled.clear();

        // 读锁遍历执行逻辑（update 只更新位置字段，不写四叉树，避免读锁内写锁死锁）
        Ar<Unit> moved = new Ar<>();
        EntityArs.Unit.each(
                u -> {
                    if (u == null)
                        return;
                    if (u.health <= 0) {
                        deadUnits.add(u);
                        return;
                    } else {
                        u.update(delta);
                        u.canShoot = true;
                        u.updateWeapons(delta);
                        if (u.velocityDirty)
                            moved.add(u);
                    }
                });
        // 对位置变化的单位逐个短暂写锁更新四叉树（写锁不长时间持有，读方几乎不阻塞）
        for (Unit u : moved) {
            EntityArs.Unit.move(u, u.x, u.y);
            u.velocityDirty = false;
        }
        for (Unit u : deadUnits) {
            u.kill();
        }
        deadUnits.clear();

        EntityArs.Building.each(
                b -> {
                    if (b == null)
                        return;
                    if (b.health <= 0) {
                        deadBuildings.add(b);
                        return;
                    } else {
                        b.update(delta);
                        // 类型级钩子（委托 Block.update，给非炮塔的自有行为留入口）
                        if (b.type != null)
                            b.type.update(b, delta);
                    }
                });
        for (Building b : deadBuildings) {
            b.kill();
        }
        deadBuildings.clear();
    }
}
