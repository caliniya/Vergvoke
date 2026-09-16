package caliniya.vergvoke.system.world;

import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Point2;
import caliniya.vergvoke.base.tool.Ar;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.game.data.RouteData;
import caliniya.vergvoke.game.data.WorldData;
import caliniya.vergvoke.system.System;

public class UnitMath extends caliniya.vergvoke.system.System<UnitMath> {

  /** 全局实例（由 Data.loadSystems() 创建）。 */
  public static UnitMath it;

  private Ar<Unit> processList = new Ar<>();
  // 判定到达节点的阈值，稍微宽容一点避免在节点附近抖动
  private static final float NODE_REACH_TOLERANCE = 4f; 

  @Override
  public UnitMath init() {
    return super.init(true);
  }

  @Override
  public void update() {
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

      // 路径请求
      if (!u.pathed) {
        boolean pathFound = calculatePath(u);
        u.pathed = true;
        
        if (!pathFound) continue;
      }

      // 向量计算 (每一帧都根据当前位置计算期望速度向量)
      calculateVelocityVector(u);
    }
  }

  /**
   * 计算路径
   * @return true 如果找到路径，false 如果不可达
   */
  private boolean calculatePath(Unit u) {
    int sx = (int) (u.x / WorldData.TILE_SIZE);
    int sy = (int) (u.y / WorldData.TILE_SIZE);
    int tx = (int) (u.targetX / WorldData.TILE_SIZE);
    int ty = (int) (u.targetY / WorldData.TILE_SIZE);

    // 如果已经在同一个格子，或者需要寻路
    if (sx != tx || sy != ty) {
      // 这里的 2, 1 暂时硬编码
      u.path = RouteData.findPath(sx, sy, tx, ty, 2, 1);

      if (u.path == null) {
        stopAndRemove(u);
        return false;
      }

      if (!u.path.isEmpty()) {
        u.path.remove(0); // 移除起点
      }
      u.pathIndex = 0;
    } else {
      // 起点终点重合，直接走直线，清空 path 让 vector 计算接管
      if (u.path != null) u.path.clear();
    }
    return true;
  }
  
  private void calculateVelocityVector(Unit u) {
    if (u.path == null) return;

    float nextX, nextY;
    boolean isFinalTarget = false;

    if (u.path.isEmpty()) {
      // 情况 A: 同格移动，直接去最终目标
      nextX = u.targetX;
      nextY = u.targetY;
      isFinalTarget = true;
    } else {
      // 情况 B: 沿路径移动
      if (u.pathIndex >= u.path.size) {
        // 容错：越界视为到达最后
        nextX = u.targetX;
        nextY = u.targetY;
        isFinalTarget = true;
      } else if (u.pathIndex == u.path.size - 1) {
        // 最后一个节点：去往精确坐标
        nextX = u.targetX;
        nextY = u.targetY;
        isFinalTarget = true;
      } else {
        // 中间节点：去往网格中心
        Point2 node = u.path.get(u.pathIndex);
        nextX = node.x * WorldData.TILE_SIZE + WorldData.TILE_SIZE / 2f;
        nextY = node.y * WorldData.TILE_SIZE + WorldData.TILE_SIZE / 2f;
        isFinalTarget = false;
      }
    }

    float dist = Mathf.dst(u.x, u.y, nextX, nextY);
    
    if (!isFinalTarget && dist <= u.speed + NODE_REACH_TOLERANCE) {
        u.pathIndex++;
        calculateVelocityVector(u); 
        return;
    }
    if (isFinalTarget && dist <= u.speed) {
        u.speedX = 0;
        u.speedY = 0;
        stopAndRemove(u);
    } else {
        u.angle = Angles.angle(u.x, u.y, nextX, nextY);
        u.speedX = Mathf.cosDeg(u.angle) * u.speed;
        u.speedY = Mathf.sinDeg(u.angle) * u.speed;
    }
  }

  private void stopAndRemove(Unit u) {
    u.speedX = 0;
    u.speedY = 0;
    u.path = null; 
    synchronized (WorldData.moveunits) {
      WorldData.moveunits.remove(u);
    }
  }
}
