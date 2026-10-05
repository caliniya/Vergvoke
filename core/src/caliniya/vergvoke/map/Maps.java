package caliniya.vergvoke.map;

import arc.Core;
import arc.files.Fi;
import arc.struct.Ar;
import caliniya.vergvoke.io.GameIO;

public class Maps {
    // 所有的地图列表
    public static Ar<Map> maps = new Ar<>();

    /** 扫描 saves 目录并加载元数据 */
    public static void load() {
        maps.clear();
        
        Fi mapDir = Core.settings.getDataDirectory().child("/map");
        if (!mapDir.exists()) mapDir.mkdirs();

        // 遍历所有文件
        for (Fi file : mapDir.list()) {
            if (file.extension().equals("aes")) {
                // 只读取元数据()
                Map map = GameIO.readMeta(file);
                if (map != null) {
                    maps.add(map);
                }
            }
        }
        
        maps.sort();
    }
    
    /** 添加一个新地图(例如刚保存后)，不用重新扫描全部 */
    public static void add(Fi file) {
        Map map = GameIO.readMeta(file);
        if(map != null) {
            maps.add(map);
            maps.sort();
        }
    }
}