package caliniya.vergvoke.game.data;

import arc.util.io.Reads;
import arc.util.io.Writes;
import arc.struct.Ar;

/**
 * 游戏进度。
 *
 * <p>星域/星系节点的定义在代码里（content/Stars.java），因此进度文件只需要记录
 * "进行到哪了"：当前星域 + 每张地图的状态。
 */
public class ProgressData {

  /** 当前所在星域内部名。 */
  public String currentStar = "";

  /** 地图进度列表（地图名 -> 状态）。 */
  public final Ar<MapProgress> maps = new Ar<>();

  public ProgressData() {}

  /** 获取某张地图的进度，不存在返回 null。 */
  public MapProgress get(String mapName) {
    for (MapProgress p : maps) {
      if (p.mapName.equals(mapName)) return p;
    }
    return null;
  }

  /** 获取或创建某张地图的进度。 */
  public MapProgress getOrCreate(String mapName) {
    MapProgress p = get(mapName);
    if (p == null) {
      p = new MapProgress(mapName);
      maps.add(p);
    }
    return p;
  }

  public void write(Writes w) {
    w.str(currentStar);
    w.i(maps.size);
    for (MapProgress p : maps) {
      w.str(p.mapName);
      byte flags = 0;
      if (p.unlocked) flags |= 1;
      if (p.completed) flags |= 2;
      w.b(flags);
    }
  }

  public void read(Reads r) {
    currentStar = r.str();
    int count = r.i();
    maps.clear();
    for (int i = 0; i < count; i++) {
      MapProgress p = new MapProgress(r.str());
      byte flags = r.b();
      p.unlocked = (flags & 1) != 0;
      p.completed = (flags & 2) != 0;
      maps.add(p);
    }
  }

  /** 单张地图的进度状态。 */
  public static class MapProgress {

    /** 地图内部名（== 星系节点名）。 */
    public final String mapName;

    /** 是否已解锁。 */
    public boolean unlocked;

    /** 是否已通关。 */
    public boolean completed;

    public MapProgress(String mapName) {
      this.mapName = mapName;
    }
  }
}
