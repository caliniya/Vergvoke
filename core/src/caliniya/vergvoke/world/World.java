package caliniya.vergvoke.world;

import caliniya.vergvoke.game.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.game.data.*;

public class World {
    public boolean space;
    public int W, H;

    public boolean test = true;

    public WorldChunk[] chunks;

    public int chunksW, chunksH;

    public World(int W, int H, boolean space) {
        this.W = W;
        this.H = H;
        this.space = space;

        this.chunksW = (W + WorldChunk.MASK) >> WorldChunk.SHIFT;
        this.chunksH = (H + WorldChunk.MASK) >> WorldChunk.SHIFT;
        this.chunks = new WorldChunk[chunksW * chunksH];
    }

    public void init() {
        for (int i = 0; i < chunks.length; i++) {
            chunks[i] = null;
        }
    }

    // --- 区块管理辅助方法 ---

    private int getChunkIndex(int cx, int cy) {
        return cy * chunksW + cx;
    }

    private WorldChunk getChunk(int x, int y) {
        int cx = x >> WorldChunk.SHIFT;
        int cy = y >> WorldChunk.SHIFT;
        int idx = cy * chunksW + cx;
        if (idx < 0 || idx >= chunks.length)
            return null;
        return chunks[idx];
    }

    private WorldChunk getOrCreateChunk(int x, int y) {
        int cx = x >> WorldChunk.SHIFT;
        int cy = y >> WorldChunk.SHIFT;
        int idx = cy * chunksW + cx;
        if (idx < 0 || idx >= chunks.length)
            return null;
        if (chunks[idx] == null)
            chunks[idx] = new WorldChunk();
        return chunks[idx];
    }

    // --- 建筑逻辑 ---

    public Building getBuilding(int x, int y) {
        if (!isValidCoord(x, y))
            return null;
        WorldChunk chunk = getChunk(x, y);
        if (chunk == null)
            return null;
        return chunk.getBuilding(x & WorldChunk.MASK, y & WorldChunk.MASK);
    }

    public boolean hasBuilding(int x, int y) {
        return getBuilding(x, y) != null;
    }

    public Building setBuilding(int x, int y, Block block, TeamTypes team) {
        if (!isValidCoord(x, y) || block == null)
            return null;

        Building newBuild = block.create(x, y, team);

        newBuild.getOccupiedCoords(
                (tx, ty) -> {
                    if (isValidCoord(tx, ty)) {
                        Building existing = getBuilding(tx, ty);
                        if (existing != null && existing != newBuild) {
                            removeBuilding(existing.tx, existing.ty);
                        }
                        WorldChunk chunk = getOrCreateChunk(tx, ty);
                        chunk.setBuilding(tx & WorldChunk.MASK, ty & WorldChunk.MASK, newBuild);
                        RouteData.updateBlock(tx, ty, newBuild.block.solid);
                    }
                });
        Entities.add(newBuild);
        return newBuild;
    }

    public void setBuilding(Building b) {
        if (!isValidCoord(b.tx, b.ty) || b.block == null)
            return;
        b.getOccupiedCoords(
                (tx, ty) -> {
                    if (isValidCoord(tx, ty)) {
                        Building existing = getBuilding(tx, ty);
                        if (existing != null && existing != b) {
                            removeBuilding(existing.tx, existing.ty);
                        }
                        WorldChunk chunk = getOrCreateChunk(tx, ty);
                        chunk.setBuilding(tx & WorldChunk.MASK, ty & WorldChunk.MASK, b);
                        RouteData.updateBlock(tx, ty, b.block.solid);
                    }
                });
        Entities.add(b);
    }

    public void removeBuilding(int x, int y) {
        Building build = getBuilding(x, y);
        if (build == null)
            return;

        // 通知导航数据：先取消实心标记（必须在清除区块前调用）
        if (build.block.solid) {
            RouteData.updateBlock(x, y);
        }

        build.getOccupiedCoords(
                (tx, ty) -> {
                    if (isValidCoord(tx, ty)) {
                        WorldChunk chunk = getChunk(tx, ty);
                        if (chunk != null
                                && chunk.getBuilding(tx & WorldChunk.MASK, ty & WorldChunk.MASK) == build) {
                            chunk.setBuilding(tx & WorldChunk.MASK, ty & WorldChunk.MASK, null);
                        }
                    }
                });

        Entities.remove(build);
    }

    public boolean isSolid(int x, int y) {
        if (!isValidCoord(x, y))
            return true;
        WorldChunk chunk = getChunk(x, y);
        if (chunk != null && chunk.getENVBlock(x & WorldChunk.MASK, y & WorldChunk.MASK) != 0) {
            return true;
        }
        Building b = getBuilding(x, y);
        if (b != null && b.block.solid) {
            return true;
        }
        return false;
    }

    // --- 环境方块 & 地板 (保持不变) ---

    public void setENVBlock(int x, int y, ENVBlock block) {
        if (!isValidCoord(x, y))
            return;
        int id = (block == null) ? 0 : block.id;
        if (id == 0) {
            WorldChunk chunk = getChunk(x, y);
            if (chunk == null)
                return;
            chunk.setENVBlock(x & WorldChunk.MASK, y & WorldChunk.MASK, 0);
            // 通知导航数据：移除环境方块
            RouteData.updateBlock(x, y, false);
        } else {
            WorldChunk chunk = getOrCreateChunk(x, y);
            chunk.setENVBlock(x & WorldChunk.MASK, y & WorldChunk.MASK, id);
            // 通知导航数据：放置环境方块
            RouteData.updateBlock(x, y, block.solid);
        }
    }

    public int getENVBlockId(int x, int y) {
        if (!isValidCoord(x, y))
            return 0;
        WorldChunk chunk = getChunk(x, y);
        if (chunk == null)
            return 0;
        return chunk.getENVBlock(x & WorldChunk.MASK, y & WorldChunk.MASK);
    }

    public ENVBlock getENVBlock(int x, int y) {
        int id = getENVBlockId(x, y);
        if (id == 0)
            return null;
        return Contents.getByID(CType.ENVBlock, id);
    }

    public void setFloor(int x, int y, Floor floor) {
        if (!isValidCoord(x, y))
            return;
        int id = (floor == null) ? 0 : floor.id;
        if (id == 0) {
            WorldChunk chunk = getChunk(x, y);
            if (chunk == null)
                return;
            chunk.setFloor(x & WorldChunk.MASK, y & WorldChunk.MASK, 0);
        } else {
            WorldChunk chunk = getOrCreateChunk(x, y);
            chunk.setFloor(x & WorldChunk.MASK, y & WorldChunk.MASK, id);
        }
    }

    public int getFloorId(int x, int y) {
        if (!isValidCoord(x, y))
            return 0;
        WorldChunk chunk = getChunk(x, y);
        if (chunk == null)
            return 0;
        return chunk.getFloor(x & WorldChunk.MASK, y & WorldChunk.MASK);
    }

    public Floor getFloor(int x, int y) {
        int id = getFloorId(x, y);
        if (id == 0)
            return null;
        return Contents.getByID(CType.Floor, id);
    }

    // 如果true表示在范围内
    public boolean isValidCoord(int x, int y) {
        return x >= 0 && x < W && y >= 0 && y < H;
    }

    // --- 索引相关方法 ---

    public void setENVBlock(int index, ENVBlock block) {
        setENVBlock(index % W, index / W, block);
    }

    public int getENVBlockId(int index) {
        return getENVBlockId(index % W, index / W);
    }

    public ENVBlock getENVBlock(int index) {
        return getENVBlock(index % W, index / W);
    }

    public void setFloor(int index, Floor floor) {
        setFloor(index % W, index / W, floor);
    }

    public int getFloorId(int index) {
        return getFloorId(index % W, index / W);
    }

    public Floor getFloor(int index) {
        return getFloor(index % W, index / W);
    }

    public boolean isSolid(int index) {
        return isSolid(index % W, index / W);
    }

    public int coordToIndex(int x, int y) {
        return y * W + x;
    }
}
