package caliniya.vergvoke.system.world;

import caliniya.vergvoke.base.tool.Ar;
import caliniya.vergvoke.world.Floor;
import arc.struct.ObjectIntMap;
import caliniya.vergvoke.world.ENVBlock;
import caliniya.vergvoke.io.*;
import arc.math.Mathf;
import arc.util.Log;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.game.data.WorldData;
import caliniya.vergvoke.system.System;
import caliniya.vergvoke.type.Weapon;

/**
 * 实体处理系统，运行在独立线程（60TPS）。
 *
 * <p>在这里处理索敌锁定，为实体指定目标。 底层网格已通过 {@code TeamData.gridLock} 实现线程安全。 具体的开火逻辑在主线程运行。
 *
 * <p>存档写入是一个跨帧的状态机：
 *
 * <pre>
 *   copy()  : 写 头(MAGIC/版本/W/H) + tags        —— 同步，在 EP 线程外
 *   task    : 扫描 + 写 调色板(floor/block)         —— 必须在瓦片之前
 *   task2   : 写 地图瓦片 (W×H × 2 short)
 *   task3   : 写 unitCount + 单位, buildingCount + 建筑, 然后落盘
 * </pre>
 *
 * 写入顺序必须与 {@link caliniya.vergvoke.io.DataIO#read} 的读取顺序严格一致。
 */
public class EntityProces extends System<EntityProces> {

  /** 全局实例（由 Data.loadSystems() 创建）。 */
  public static EntityProces it;

  public volatile boolean task2 = false, task3 = false;

  public Ar<Floor> floorPalette;
  public ObjectIntMap<Floor> floorMap;
  public Ar<ENVBlock> blockPalette;
  public ObjectIntMap<ENVBlock> blockMap;

  @Override
  public EntityProces init() {
    return super.init(true);
  }

  @Override
  public void update() {
    // --- 单位处理（战斗逻辑，每帧执行）---
    WorldData.units.each(
        u -> {
          if (u == null) return;

          for (Weapon w : u.weapons) {

            float wx = u.x + w.type.x;
            float wy = u.y + w.type.y;

            if (w.rotate) {
              // 旋转武器（炮塔）：独立锁敌
              // 目标失效 或 超出射程 → 重搜
              if (w.target == null
                  || w.target.health <= 0
                  || Mathf.dst2(wx, wy, w.target.x, w.target.y) > w.type.range * w.type.range) {
                w.type.findTarget(w, wx, wy);
              }
            } else {
              // 固定武器：直接瞄准单位锁定的目标
              w.target = u.target;
            }
          }
        });

    // --- 建筑处理（战斗逻辑，每帧执行）---
    WorldData.buildings.each(
        b -> {
          if (b == null) return;

          if (b.target == null || b.target.health <= 0) {
            b.target = b.block.findTarget(b);
          }
        });

    if (task2) {
      // 任务二：写入地图瓦片（每格 floorId + blockId，各一个 short）
      for (int y = 0; y < WorldData.world.H; y++) {
        for (int x = 0; x < WorldData.world.W; x++) {
          Floor floor = WorldData.world.getFloor(x, y);
          ENVBlock block = WorldData.world.getENVBlock(x, y);
          DataIO.w.s(floor == null ? 0 : floorMap.get(floor, 0));
          DataIO.w.s(block == null ? 0 : blockMap.get(block, 0));
        }
      }
    }

    if (task) {
      // 任务一：扫描调色板并写入（必须在瓦片数据之前）
      floorPalette = new Ar<>();
      floorMap = new ObjectIntMap<>();
      blockPalette = new Ar<>();
      blockMap = new ObjectIntMap<>();

      floorPalette.add((Floor) null);
      blockPalette.add((ENVBlock) null);

      int width = WorldData.world.W;
      int height = WorldData.world.H;

      // 阶段1：扫描调色板
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          Floor floor = WorldData.world.getFloor(x, y);
          ENVBlock block = WorldData.world.getENVBlock(x, y);

          if (floor != null && !floorMap.containsKey(floor)) {
            floorMap.put(floor, floorPalette.size);
            floorPalette.add(floor);
          }
          if (block != null && !blockMap.containsKey(block)) {
            blockMap.put(block, blockPalette.size);
            blockPalette.add(block);
          }
        }
      }

      // 阶段2：写入调色板（size + 各内容 internalName，index 0 为 null）
      DataIO.w.s((short) floorPalette.size);
      for (int i = 0; i < floorPalette.size; i++) {
        Floor f = floorPalette.get(i);
        DataIO.w.str(f == null ? "null" : f.internalName);
      }
      DataIO.w.s((short) blockPalette.size);
      for (int i = 0; i < blockPalette.size; i++) {
        ENVBlock b = blockPalette.get(i);
        DataIO.w.str(b == null ? "null" : b.internalName);
      }
    }

    if (task3) {
      // 任务三：写入实体（单位 + 建筑），再落盘
      // 先收集有效实体，保证写入的数量与实际条数一致（跳过 null / 已死亡）
      Ar<Unit> outUnits = new Ar<>();
      WorldData.units.each(
          u -> {
            if (u != null && u.health > 0) outUnits.add(u);
          });
      DataIO.w.i(outUnits.size);
      for (int i = 0; i < outUnits.size; i++) {
        Unit u = outUnits.get(i);
        DataIO.w.str(u.type.internalName); // 读取端据此 Contents.get 还原类型
        u.write(DataIO.w);
        DataIO.w.b(DataIO.END_MARKER);
      }

      Ar<Building> outBuildings = new Ar<>();
      WorldData.buildings.each(
          b -> {
            if (b != null && b.health > 0) outBuildings.add(b);
          });
      DataIO.w.i(outBuildings.size);
      for (int i = 0; i < outBuildings.size; i++) {
        Building b = outBuildings.get(i);
        DataIO.w.str(b.block.internalName);
        b.write(DataIO.w);
        DataIO.w.b(DataIO.END_MARKER);
        // 至此内存中的存档数据写入完成
      }

      task3 = false;
      DataIO.data = DataIO.bos.toByteArray();
      DataIO.copyed = true;
      GameIO.save(DataIO.saveTarget);
    }

    if (task2) {
      task2 = false;
      task3 = true;
    }

    if (task) {
      task = false;
      task2 = true;
    }
  }
}
