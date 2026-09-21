package caliniya.vergvoke.io;

import arc.Core;
import arc.files.Fi;
import arc.struct.StringMap;
import arc.util.io.*;
import arc.util.*;
import caliniya.vergvoke.base.ecs.Unit;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.system.*;
import caliniya.vergvoke.system.world.*;
import java.io.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.type.type.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.world.*;
import caliniya.vergvoke.core.*;

import java.io.*;
import java.util.concurrent.*;

// 负责和游戏数据交互
public class DataIO {

  /** 单位/建筑结束标记：8 字节 0xAE，读取未知类型时跳过到此标记。 */
  public static final byte[] END_MARKER = {
    (byte) 0xAE, (byte) 0xAE, (byte) 0xAE, (byte) 0xAE,
    (byte) 0xAE, (byte) 0xAE, (byte) 0xAE, (byte) 0xAE
  };

  // 以下三个数据流存储实体数据
  public static volatile ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 20); // 预分配 1MB
  public static volatile DataOutputStream stream = new DataOutputStream(bos);
  public static volatile Writes w = new Writes(stream);
  public static volatile byte[] data;
  public static volatile boolean copyed; // 向内存中复制数据是否已完成
  public static volatile boolean loaded; // 已经完成从磁盘向内存中加载数据

  /** 序列化完成后要写盘的目标文件 */
  public static volatile Fi saveTarget;

  // 命令实体处理线程开始写入数据
  // 线程会使用三次循环来写入地图数据和实体数据
  // 写入地图元数据
  public static void copy(@Nullable StringMap tags) {
    w.b(GameIO.MAGIC.getBytes());
    w.i(GameIO.SAVE_VERSION);
    w.i(WorldData.world.W);
    w.i(WorldData.world.H);

    // --- Tags ---
    if (tags == null) tags = new StringMap();
    tags.put("space", String.valueOf(WorldData.world.space));
    w.s(tags.size);
    for (var entry : tags) {
      w.str(entry.key);
      w.str(entry.value);
    }
    EntityProces.it.task = true;
  }

  // 调用此方法来实现保存
  public static void setSave(Fi file, @Nullable StringMap tags) {
    saveTarget = file;
    copy(tags);
  }

  public static void load() {
    load(data);
  }

  public static void load(byte[] bytes) {
    load(bytes, null);
  }

  /** 从内存数据恢复存档，加载并进入游戏后执行 {@code onEnter}（主线程）。 */
  public static void load(byte[] bytes, Runnable onEnter) {
    if (!loaded) return;
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      Reads r = new Reads(in);

      String magic = new String(r.b(4));
      if (!magic.equals(GameIO.MAGIC)) throw new IOException("Invalid file format");

      int ver = r.i();
      int width = r.i();
      int height = r.i();

      StringMap tags = new StringMap();
      int tagCount = r.s();
      for (int i = 0; i < tagCount; i++) {
        tags.put(r.str(), r.str());
      }
      boolean isSpace = tags.getBool("space");

      WorldData.initWorld(width, height, isSpace);
      GameIO.submitIo(
          () -> {
            read(r, width, height);
            Core.app.post(
                () -> {
                  Data.loadSystems();
                  Data.enter();
                  if (onEnter != null) onEnter.run();
                });
          });

    } catch (Throwable e) {
      Log.err("Restore failed", e);
    }
  }

  /** 读取存档：调色板 → 地图数据 → 单位 → 建筑 → 寻路初始化。 调用前 WorldData.world 必须已经通过 reBuildAll 初始化。 */
  private static void read(Reads r, int width, int height) {
    // --- 调色板 ---
    int floorPaletteSize = r.s();
    Floor[] floorLookup = new Floor[floorPaletteSize];
    for (int i = 0; i < floorPaletteSize; i++) {
      String name = r.str();
      floorLookup[i] = name.equals("null") ? null : Contents.get(name, Floor.class);
    }

    int blockPaletteSize = r.s();
    ENVBlock[] blockLookup = new ENVBlock[blockPaletteSize];
    for (int i = 0; i < blockPaletteSize; i++) {
      String name = r.str();
      blockLookup[i] = name.equals("null") ? null : Contents.get(name, ENVBlock.class);
    }

    // --- 地图数据 ---
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        short floorId = r.s();
        short blockId = r.s();
        Floor floor = (floorId >= 0 && floorId < floorLookup.length) ? floorLookup[floorId] : null;
        ENVBlock block =
            (blockId >= 0 && blockId < blockLookup.length) ? blockLookup[blockId] : null;
        WorldData.world.setFloor(x, y, floor);
        WorldData.world.setENVBlock(x, y, block);
      }
    }

    // --- Units ---
    int unitCount = r.i();
    for (int i = 0; i < unitCount; i++) {
      String typeName = r.str();
      UnitType type = Contents.get(typeName, UnitType.class);
      if (type != null) {
        // TODO 读档：坐标 / 阵营暂时给占位，等基类 write/read 落地后从这里恢复
        Unit u = type.create(0f, 0f, TeamTypes.Abort);
        u.read(r);
        skipToEndMarker(r); // 校验结束标记
      } else {
        Log.warn("Unknown unit type in save: @, skipping...", typeName);
        skipToEndMarker(r);
      }
    }

    // --- Buildings ---
    int buildingCount = r.i();
    for (int i = 0; i < buildingCount; i++) {
      String typeName = r.str();
      Block type = Contents.get(typeName, Block.class);
      if (type != null) {
        Building b = type.create();
        b.read(r);
        skipToEndMarker(r); // 校验结束标记
        WorldData.world.setBuilding(b);
      } else {
        Log.warn("Unknown block type in save: @, skipping...", typeName);
        skipToEndMarker(r);
      }
    }
  }

  /**
   * 从当前读取位置开始，一路跳过字节直到找到 END_MARKER（8 个 0xAE）。 调用前假设 Reader 正处于未知数据的开头，调用后 Reader 位于 END_MARKER 之后。
   */
  private static void skipToEndMarker(Reads r) {
    int matched = 0;
    while (matched < 8) {
      byte b = r.b();
      if (b == END_MARKER[matched]) {
        matched++;
      } else {
        matched = 0;
        if (b == END_MARKER[0]) matched = 1;
      }
    }
  }
}
