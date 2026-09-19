package caliniya.vergvoke.world;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.util.*;
import arc.util.io.*;
import caliniya.vergvoke.base.api.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.type.type.*;
import caliniya.vergvoke.ui.*;

public class Block extends ContentType implements DrawType<Building>, TechNodeContent {

    // --- 基础属性 ---
    public float psize; // 大小，像素级
    public int size = 2; // 大小，单位格
    public boolean buildable = true; // 可以造
    public boolean solid = true; // 可以阻挡通行
    public float health = 100; // 顾名思义
    public int capacity = 100; // 物品容量，为0就是不能存
    public ItemType[] allowItem = Contents.items; // 能存的,默认啥都能存一百

    /** 液体容量（0 = 不能存液体）。 */
    public float liquidCapacity;

    /** 电力电池容量（0 = 不能存电力）。 */
    public float powerCapacity;

    public TextureRegion region; // 主贴图

    // --- 形状定义 ---
    // 相对于锚点(0,0)的偏移量数组：[dx1, dy1, dx2, dy2, ...]
    public int[] shapeOffsets = null;

    public Block(String name) {
        super(name, CType.Block);
    }

    @Override
    public TechNodeContent[] requirements() {
        return requirements; // ContentType 里的前置字段（默认 null）
    }

    public Building create() {
        psize = size * WorldData.TILE_SIZE;
        return Building.create(this);
    }

    public Building create(int tx, int ty, TeamTypes team) {
        psize = size * WorldData.TILE_SIZE;
        return Building.create(this, tx, ty, team);
    }

    public void load() {
        region = Core.atlas.find(name);
    }

    public void update(Building building, float dt) {
    }

    public void draw(Building b) {
        float rotation = b.angle * 90f;
        Draw.rect(region, b.x, b.y, rotation);
    }

    public void drawDebug(Building b) {
        Draw.color(Color.green);
        Lines.stroke(4f);
        // 绘制基于 size 的包围盒
        Lines.rect(b.x - b.block.psize / 2, b.y - b.block.psize / 2, b.block.psize, b.block.psize);

        // 3. 绘制占据的实际格子 (黄色细线)
        // 对于异形建筑，这比包围盒更准确
        if (b.shapeOffsets != null) {
            Draw.color(Color.cyan);
            Lines.stroke(1f);
            for (int i = 0; i < b.shapeOffsets.length; i += 2) {
                float tx = (b.tx + b.shapeOffsets[i]) * WorldData.TILE_SIZE;
                float ty = (b.ty + b.shapeOffsets[i + 1]) * WorldData.TILE_SIZE;
                Lines.rect(tx, ty, WorldData.TILE_SIZE, WorldData.TILE_SIZE);
            }
        }

        // 4. 绘制旋转角度 (青色文字)
        Fonts.def.draw(
                b.x + "   " + b.y, b.x + b.block.psize / 2f, b.y + b.block.psize + 10f, Align.center);
        Fonts.def.draw(
                Strings.format("" + b.health),
                b.x - b.block.size,
                b.y - b.block.size + b.block.size + 8f,
                Align.center);
        Draw.color(); // 重置颜色
    }

    public void write(Building b, Writes w) {
    }

    public void read(Building b, Reads r) {
    }

    // --- 目标查找 ---

    /** 查找目标实体。默认空实现，子类（如炮塔）可覆写。 */
    public Entity findTarget(Building b) {
        return null;
    }

    // --- 物品相关 ---

    public void allowAllItem(ItemType... types) {
        allowItem = types;
    }

    /**
     * 辅助方法：获取旋转后的形状偏移量 这用于确定建筑在当前角度下实际占据了哪些格子
     *
     * @param angle       建筑当前角度 (0-3)
     * @param baseOffsets 原始形状偏移 (通常是 block.shapeOffsets)
     * @return 旋转后的新偏移量数组
     */
    public static int[] getRotatedOffsets(int angle, int[] baseOffsets) {
        if (baseOffsets == null)
            return (int[]) null;

        int[] rotated = baseOffsets.clone();

        for (int i = 0; i < rotated.length; i += 2) {
            int x = baseOffsets[i];
            int y = baseOffsets[i + 1];

            switch (angle) {
                case 1:
                    rotated[i] = y;
                    rotated[i + 1] = -x;
                    break;
                case 2:
                    rotated[i] = -x;
                    rotated[i + 1] = -y;
                    break;
                case 3:
                    rotated[i] = -y;
                    rotated[i + 1] = x;
                    break;
                default:
                    rotated[i] = x;
                    rotated[i + 1] = y;
                    break;
            }
        }
        return rotated;
    }
}
