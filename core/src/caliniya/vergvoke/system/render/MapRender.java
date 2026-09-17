package caliniya.vergvoke.system.render;

import arc.Core;
import arc.Events;
import arc.graphics.Camera;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.GlyphLayout;
import arc.math.Mathf;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.type.EventType;
import caliniya.vergvoke.game.data.RouteData;
import caliniya.vergvoke.game.data.WorldData;
import caliniya.vergvoke.world.World;
import caliniya.vergvoke.base.shaders.*;

public class MapRender extends caliniya.vergvoke.system.System<MapRender> {
    public static final float TILE_SIZE = 32f;
    public static World world;
    public Camera camera = Core.camera;

    // 存储所有区块的二维数组
    private MapChunk[][] chunks;
    private int chunksW, chunksH;

    private SpaceShader spaceShader;

    public boolean debug = true;
    private final GlyphLayout layout = new GlyphLayout();

    @Override
    public MapRender init() {
        Events.run(EventType.events.Mapinit, () -> rebuildAll());
        Events.run(EventType.events.EnterUV, () -> paused = true);
        Events.run(EventType.events.ExitUV, () -> paused = false);
        // WorldData.initWorld();
        world = WorldData.world;
        this.index = 10;
        spaceShader = Shaders.spaceShader;
        initChunks();
        return super.init(false, false);
    }

    private void initChunks() {
        // 计算横向和纵向有多少个区块
        chunksW = Mathf.ceil((float) world.W / MapChunk.SIZE);
        chunksH = Mathf.ceil((float) world.H / MapChunk.SIZE);

        chunks = new MapChunk[chunksW][chunksH];

        for (int x = 0; x < chunksW; x++) {
            for (int y = 0; y < chunksH; y++) {
                chunks[x][y] = new MapChunk(x, y);
            }
        }
    }

    /** 重新开始渲染 */
    public void rebuildAll() {
        if (chunks != null) {
            for (int x = 0; x < chunksW; x++) {
                for (int y = 0; y < chunksH; y++) {
                    if (chunks[x][y] != null) {
                        chunks[x][y].dispose();
                    }
                }
            }
        }

        world = WorldData.world;

        initChunks();
    }

    /** 仅重新渲染 适用于地图尺寸没变，但想强制重绘所有画面的情况 */
    public void flagAllDirty() {
        if (chunks == null)
            return;

        for (int x = 0; x < chunksW; x++) {
            for (int y = 0; y < chunksH; y++) {
                if (chunks[x][y] != null) {
                    chunks[x][y].dirty = true;
                }
            }
        }
    }

    @Override
    public void update() {
        Draw.color();
        if (!inited || paused)
            return;
        if (chunks == null)
            return;

        // 计算摄像机视野范围内的 区块索引
        float viewLeft = camera.position.x - camera.width / 2f;
        float viewBottom = camera.position.y - camera.height / 2f;
        float viewRight = camera.position.x + camera.width / 2f;
        float viewTop = camera.position.y + camera.height / 2f;

        // 将像素坐标转换为区块索引
        int startX = (int) (viewLeft / MapChunk.PIXEL_SIZE);
        int startY = (int) (viewBottom / MapChunk.PIXEL_SIZE);
        int endX = (int) (viewRight / MapChunk.PIXEL_SIZE);
        int endY = (int) (viewTop / MapChunk.PIXEL_SIZE);

        // 限制在数组范围内
        startX = Mathf.clamp(startX, 0, chunksW - 1);
        startY = Mathf.clamp(startY, 0, chunksH - 1);
        endX = Mathf.clamp(endX, 0, chunksW - 1);
        endY = Mathf.clamp(endY, 0, chunksH - 1);

        if (world.space) {
            spaceShader.render();
        } else {
            Core.graphics.clear(Color.black);
        }

        // 只渲染视野内的区块
        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                if (chunks[x][y] != null) {
                    chunks[x][y].render();
                }
            }
        }
        drawDebugInfo(viewLeft, viewBottom, viewRight, viewTop);
    }

    private void drawDebugInfo(float viewLeft, float viewBottom, float viewRight, float viewTop) {
        int startX = Mathf.clamp((int) (viewLeft / TILE_SIZE), 0, world.W - 1);
        int startY = Mathf.clamp((int) (viewBottom / TILE_SIZE), 0, world.H - 1);
        int endX = Mathf.clamp((int) (viewRight / TILE_SIZE), 0, world.W - 1);
        int endY = Mathf.clamp((int) (viewTop / TILE_SIZE), 0, world.H - 1);

        boolean[] solidMap = RouteData.layers[0].baseSolidMap;
        int[] clearanceMap = RouteData.layers[0].clearanceMap;

        int worldW = WorldData.world.W; // 缓存世界宽度

        // 安全检查
        if (solidMap == null || clearanceMap == null)
            return;

        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {

                int index = y * worldW + x;

                // 越界检查 (防止视野超出地图边缘导致崩溃)
                if (index < 0 || index >= solidMap.length)
                    continue;

                float drawX = x * TILE_SIZE + TILE_SIZE / 2f;
                float drawY = y * TILE_SIZE + TILE_SIZE / 2f;

                // 绘制红色障碍块
                if (solidMap[index]) {
                    Draw.color(1f, 0f, 0f, 0.5f);
                    Fill.rect(drawX, drawY, TILE_SIZE, TILE_SIZE);
                }

                // 绘制距离场数值
                int clearance = clearanceMap[index];
                if (clearance == 0)
                    Draw.color(Color.red);
                else if (clearance < 3)
                    Draw.color(Color.yellow);
                else
                    Draw.color(Color.green);
            }
        }
        Draw.color(); // 重置颜色
    }

    public void flagUpdate(int worldGridX, int worldGridY) {
        int cx = worldGridX / MapChunk.SIZE;
        int cy = worldGridY / MapChunk.SIZE;

        if (cx >= 0 && cx < chunksW && cy >= 0 && cy < chunksH) {
            if (chunks[cx][cy] != null) {
                chunks[cx][cy].dirty = true;
            }
        }
    }

    @Override
    public void dispose() {
        if (chunks != null) {
            for (int x = 0; x < chunksW; x++) {
                for (int y = 0; y < chunksH; y++) {
                    if (chunks[x][y] != null)
                        chunks[x][y].dispose();
                }
            }
        }
        super.dispose();
    }
}
