package caliniya.vergvoke.type.def;

import arc.util.pooling.Pools;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.base.ecs.Building;
import caliniya.vergvoke.base.ecs.EntityArs;
import caliniya.vergvoke.base.type.TeamTypes;
import caliniya.vergvoke.game.Entities;
import caliniya.vergvoke.game.data.WorldData;
import caliniya.vergvoke.type.def.comps.*;
import caliniya.vergvoke.type.type.BuildingType;
import caliniya.vergvoke.world.Block;
import caliniya.vergvoke.world.blocks.defence.Turret;

/**
 * 建筑实体定义（C+B 方案验证）：BlockComp 身份/占位 + TargetComp 索敌 + TurretComp 开火。
 * type 接线用占位的 {@link BuildingType}（Block 实现 EntityType 的方案未拍板）。
 */
@Entity(comps = {BlockComp.class, TargetComp.class, TurretComp.class}, name = "Building", type = BuildingType.class)
public class BuildingDef {

    /**
     * 演示工厂：填占位数据 + 从类型拷配置。
     * @Init/@Reset 缺位，派生初始化（坐标/血量/形状旋转）先在这里手动做；对象池复用同理。
     */
    public static Building create(Block block, int tx, int ty, int angle, TeamTypes team) {
        Building b = Pools.obtain(Building.class, Building::new);
        // BlockComp：身份 + 占位
        b.block = block;
        b.tx = tx;
        b.ty = ty;
        b.angle = angle;
        b.tileSize = block.size;
        if (block.shapeOffsets != null) {
            b.shapeOffsets = Block.getRotatedOffsets(angle, block.shapeOffsets);
        }
        // 派生坐标 / 战斗基础
        b.x = tx * WorldData.TILE_SIZE + block.psize / 2f;
        b.y = ty * WorldData.TILE_SIZE + block.psize / 2f;
        b.team = team;
        b.maxHealth = block.health;
        if (b.health <= 0f) {
            b.health = block.health;
        }
        b.id = Entities.assignID();
        // TurretComp：配置拷贝（仅炮塔类建筑）；索敌半径走 TargetComp.range
        if (block instanceof Turret t) {
            b.reloadTime = t.reloadTime;
            b.rotateSpeed = t.rotateSpeed;
            b.bullet = t.bulletType;
            b.range = t.range;
        }
        b.self = b;
        EntityArs.Building.add(b);
        return b;
    }
}
