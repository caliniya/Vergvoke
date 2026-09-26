package caliniya.vergvoke.type.def.comps;

import arc.func.Intc2;
import arc.math.geom.Rect;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.game.data.WorldData;

/**
 * 建筑组件：瓦片锚点 + 占位形状。
 *
 * <p>占位数据由放置侧填好：{@link #shapeOffsets} 是已按 {@link #angle} 旋转好的副本
 * （异形建筑非 null），方形建筑为 null，回落 {@link #tileSize}。
 *
 * <p>{@link #getOccupiedCoords} / {@link #occupies} 与 Building 的同名方法逻辑一致；
 * {@code contains / hitbox} 用 {@code @OverrideEntity} 覆写基类——基类默认按中心
 * 外接圆 / 外接方形判定，建筑改为按真实占位瓦片判定（异形建筑不再误判）。
 */
@Component(index = 3, name = "Block")
public class BlockComp {

    /** 瓦片锚点（左下角格坐标）。 */
    public int tx, ty;

    /** 朝向：0 上 / 1 右 / 2 下 / 3 左（放置与存档用，占位形状由放置侧旋转好）。 */
    public int angle;

    /** 方形建筑的边长（格）；{@link #shapeOffsets} 为 null 时生效。 */
    public int tileSize = 1;

    /** 旋转后的形状偏移副本 [dx0, dy0, dx1, dy1, ...]（相对锚点，格）；null = 方形。 */
    public int[] shapeOffsets;

    /** 建筑占据的所有瓦片坐标（世界格坐标）。 */
    public void getOccupiedCoords(Intc2 consumer) {
        if (shapeOffsets != null) {
            for (int i = 0; i < shapeOffsets.length; i += 2) {
                consumer.get(tx + shapeOffsets[i], ty + shapeOffsets[i + 1]);
            }
        } else {
            for (int dx = 0; dx < tileSize; dx++) {
                for (int dy = 0; dy < tileSize; dy++) {
                    consumer.get(tx + dx, ty + dy);
                }
            }
        }
    }

    /** 是否占据指定瓦片坐标。 */
    public boolean occupies(int worldX, int worldY) {
        if (shapeOffsets != null) {
            for (int i = 0; i < shapeOffsets.length; i += 2) {
                if (tx + shapeOffsets[i] == worldX && ty + shapeOffsets[i + 1] == worldY) {
                    return true;
                }
            }
            return false;
        }
        return worldX >= tx && worldX < tx + tileSize && worldY >= ty && worldY < ty + tileSize;
    }

    /** 覆写基类的外接圆判定：建筑按真实占位瓦片判定（世界像素坐标 → 瓦片 → occupies）。 */
    @OverrideEntity
    public boolean contains(float worldX, float worldY) {
        return occupies((int) (worldX / WorldData.TILE_SIZE), (int) (worldY / WorldData.TILE_SIZE));
    }

    /** 覆写基类的中心外接方形包围盒：建筑取所有占位瓦片的像素范围（四叉树插入与范围查询用）。 */
    @OverrideEntity
    public void hitbox(Rect out) {
        float ts = WorldData.TILE_SIZE;
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        getOccupiedCoords(
                (cx, cy) -> {
                    if (cx < b[0]) b[0] = cx;
                    if (cy < b[1]) b[1] = cy;
                    if (cx > b[2]) b[2] = cx;
                    if (cy > b[3]) b[3] = cy;
                });
        if (b[0] > b[2]) {
            // 没有任何占位瓦片（未初始化）：退回锚点处一个零尺寸盒，别用哨兵值污染四叉树
            out.set(tx * ts, ty * ts, 0f, 0f);
            return;
        }
        out.set(b[0] * ts, b[1] * ts, (b[2] - b[0] + 1) * ts, (b[3] - b[1] + 1) * ts);
    }
}
