package caliniya.vergvoke.campaign;

import arc.Core;
import arc.files.Fi;
import caliniya.vergvoke.game.data.ProgressData;
import caliniya.vergvoke.io.ProgressIO;
import caliniya.vergvoke.world.stars.StarNode;

/**
 * 战役管理器。
 *
 * <p>星域/星系节点在代码里定义（content/Stars.java），随游戏打包的原始地图放在
 * assets/campaign/星域/maps/ 下（只读）。
 *
 * <p>玩家玩过某张地图（内容被修改）后，按同样的结构保存到数据目录
 * （Android/data/包名）：数据目录/campaign/星域/maps/地图名.aes。
 *
 * <p>加载时「数据目录优先，内置兜底」：数据目录里存在这份地图存档，
 * 本身就等于"这图被玩过/改过"的标记。进度单独存 progress.aevp。
 */
public class Campaign {

  /** 战役数据所在的目录名（内置资源与数据目录共用）。 */
  public static final String DIR = "campaign";

  private static ProgressData progress = new ProgressData();

  private Campaign() {}

  // ==================== 进度 ====================

  public static ProgressData progress() {
    return progress;
  }

  /** 从数据目录加载进度（没有则用空进度）。 */
  public static void loadProgress() {
    progress = ProgressIO.load();
  }

  public static void saveProgress() {
    ProgressIO.save(progress);
  }

  // ==================== 地图文件（回退加载） ====================

  /** 地图在战役内的相对路径：campaign/星域/maps/地图名.aes */
  private static String mapRelPath(String starName, String mapName) {
    return DIR + "/" + starName + "/maps/" + mapName + ".aes";
  }

  /**
   * 获取地图文件：数据目录有玩家副本就用它（玩过/改过），否则返回内置原始版。
   */
  public static Fi mapFile(String starName, String mapName) {
    Fi local = Core.settings.getDataDirectory().child(mapRelPath(starName, mapName));
    return local.exists() ? local : Core.files.internal(mapRelPath(starName, mapName));
  }

  /** 节点对应的地图文件（节点名 == 地图名）。 */
  public static Fi nodeMapFile(String starName, StarNode node) {
    return mapFile(starName, node.mapName());
  }

  /** 该地图是否已被玩家游玩/修改（数据目录存在副本）。 */
  public static boolean isPlayed(String starName, String mapName) {
    return Core.settings.getDataDirectory().child(mapRelPath(starName, mapName)).exists();
    
  }

  /**
   * 获取地图保存路径（数据目录），并确保目录存在。
   * 保存"玩过且被修改"的地图时调用。
   */
  public static Fi mapSaveFile(String starName, String mapName) {
    Fi file = Core.settings.getDataDirectory().child(mapRelPath(starName, mapName));
    file.parent().mkdirs();
    return file;
  }
}
