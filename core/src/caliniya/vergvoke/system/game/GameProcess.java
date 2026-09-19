package caliniya.vergvoke.system.game;

import arc.util.*;
import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.base.tool.*;
import caliniya.vergvoke.content.*;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.system.*;
import caliniya.vergvoke.system.world.BulletProcess;

// 在这里进行主线程游戏内容的更新
@SystemDef(name = "GameProcess", thread = "main", index = 5)
public class GameProcess extends caliniya.vergvoke.system.System<GameProcess> {

  /** 全局实例（由 Data.loadSystems() 创建）。 */
  public static GameProcess it;

  public Ar<Unit> deadUnits;
  public Ar<Building> deadBuildings;
  public Ar<Entity> freshKilled; // 接收 BulletProcess 的即时击杀通知

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
    // 先处理 BulletProcess 线程刚击杀的实体（延迟最小化，防止血量变负才死）
    BulletProcess.it.drainFreshKills(freshKilled);
    for (Entity e : freshKilled) {
      e.kill();
    }
    freshKilled.clear();

    // 读锁遍历执行逻辑（update 只更新位置字段，不写四叉树，避免读锁内写锁死锁）
    Ar<Unit> moved = new Ar<>();
    WorldData.units.each(
        u -> {
          if (u == null) return;
          if (u.health <= 0) {
            deadUnits.add(u);
            return;
          } else {
            u.update(delta);
            u.canShoot = true;
            u.updateWeapons(delta);
            if (u.velocityDirty) moved.add(u);
          }
        });
    // 对位置变化的单位逐个短暂写锁更新四叉树（写锁不长时间持有，读方几乎不阻塞）
    for (Unit u : moved) {
      WorldData.units.move(u, u.x, u.y);
      u.velocityDirty = false;
    }
    for (Unit u : deadUnits) {
      u.kill();
    }
    deadUnits.clear();

    WorldData.buildings.each(
        b -> {
          if (b == null) return;
          if (b.health <= 0) {
            deadBuildings.add(b);
            return;
          } else {
            b.update(delta);
          }
        });
    for (Building b : deadBuildings) {
      b.kill();
    }
    deadBuildings.clear();
  }
}
