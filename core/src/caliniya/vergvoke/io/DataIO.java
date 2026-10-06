package caliniya.vergvoke.io;

import java.io.*;

import arc.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.system.world.*;
import caliniya.vergvoke.type.type.*;
import caliniya.vergvoke.world.*;

// 负责和游戏数据交互
public class DataIO {

    /**
     * 单位/建筑结束标记：8 字节 0xAE，读取未知类型时跳过到此标记。
     */
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

    /**
     * 序列化完成后要写盘的目标文件
     */
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
        if (tags == null) {
            tags = new StringMap();
        }
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

    /**
     * 从内存数据恢复存档，加载并进入游戏后执行 {@code onEnter}（主线程）。
     */
    public static void load(byte[] bytes, Runnable onEnter) {
        if (!loaded) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            Reads r = new Reads(in);

            String magic = new String(r.b(4));
            if (!magic.equals(GameIO.MAGIC)) {
                throw new IOException("Invalid file format");
            }

            int ver = r.i();
            if (ver != GameIO.SAVE_VERSION) {
                throw new IOException(
                        "Save version mismatch: file v" + ver + ", expected v" + GameIO.SAVE_VERSION);
            }
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
                                    // 相机对准第一个恢复的单位，否则用户面对空地图找不到部队
                                    if (EntityArs.Unit.size() > 0) {
                                        Unit u0 = EntityArs.Unit.array.get(0);
                                        Core.camera.position.set(u0.x, u0.y);
                                    }
                                    if (onEnter != null) {
                                        onEnter.run();
                                    }
                                });
                    });

        } catch (Throwable e) {
            Log.err("Restore failed", e);
        }
    }

    /**
     * 读取存档：调色板 → 地图数据 → 单位 → 建筑 → 寻路初始化。 调用前 WorldData.world 必须已经通过 reBuildAll
     * 初始化。
     */
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
                ENVBlock block
                        = (blockId >= 0 && blockId < blockLookup.length) ? blockLookup[blockId] : null;
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
                // create 时坐标还是 (0,0)，read 恢复坐标/阵营后把四叉树节点挪到真实位置
                Unit u = type.create(TeamTypes.Abort, 0f, 0f);
                u.read(r);
                skipToEndMarker(r); // 校验结束标记
                EntityArs.Unit.move(u, u.x, u.y);
            } else {
                Log.warn("Unknown unit type in save: @, skipping...", typeName);
                skipToEndMarker(r);
            }
        }

        // 地图瓦片全部就位，重刷导航层（此前 RouteData.init 跑在空世界上，地图固体进不了导航图）
        WorldData.mapLoaded();

        // --- Buildings ---
        int buildingCount = r.i();
        for (int i = 0; i < buildingCount; i++) {
            String typeName = r.str();
            Block type = Contents.get(typeName, Block.class);
            if (type != null) {
                // 工厂只管构造（模块/self/运行时状态复位），read 把坐标阵营糊回来，
                // rebuild 重算派生数据，最后由门面注册瓦片/导航/容器
                Building b = type.buildingType.create(TeamTypes.Evoke, 0, 0, 0);
                b.read(r);
                skipToEndMarker(r); // 校验结束标记
                type.buildingType.rebuild(b); // 坐标/阵营到手后重算占位形状与中心点
                WorldData.placeBuilding(b);
            } else {
                Log.warn("Unknown block type in save: @, skipping...", typeName);
                skipToEndMarker(r);
            }
        }
    }

    /**
     * 从当前读取位置开始，一路跳过字节直到找到 END_MARKER（8 个 0xAE）。 调用前假设 Reader 正处于未知数据的开头，调用后
     * Reader 位于 END_MARKER 之后。
     */
    private static void skipToEndMarker(Reads r) {
        int matched = 0;
        while (matched < 8) {
            byte b = r.b();
            if (b == END_MARKER[matched]) {
                matched++;
            } else {
                matched = 0;
                if (b == END_MARKER[0]) {
                    matched = 1;
                }
            }
        }
    }
}
