package caliniya.vergvoke.type.type;

import arc.util.pooling.Pools;

import caliniya.vergvoke.base.api.EntityType;
import caliniya.vergvoke.base.ecs.Building;
import caliniya.vergvoke.base.ecs.EntityArs;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.base.type.TeamTypes;
import caliniya.vergvoke.game.Entities;
import caliniya.vergvoke.game.data.WorldData;
import caliniya.vergvoke.world.Block;
import caliniya.vergvoke.world.blocks.defence.Turret;

/**
 * 建筑的类型载体（对齐 UnitType 的模式）：实体类型链接走基类 {@code Entity.type}，
 * 工厂在类型上（{@link #create}），实体侧不再有 block 引用字段。
 *
 * <p>过渡桥：Block 的配置/贴图还没迁进 type，本类先包一层 {@link #block}；
 * 等 Block 实现 EntityType（或配置直接搬进本类）后这层拆掉。
 */
public class BuildingType implements EntityType {

    /** 本类型对应的方块（过渡桥，配置迁移完成前由它供给 size/psize/health/形状）。 */
    public final Block block;

    public BuildingType(Block block) {
        this.block = block;
    }

    /** 类型级工厂：按 tx/ty/angle 放置（@Init/@Reset 缺位，派生初始化先在这里手动做）。 */
    public Building create(TeamTypes team, int tx, int ty, int angle) {
        Building b = Pools.obtain(Building.class, Building::new);
        b.type = this;
        // BlockComp：占位
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

    // --- EntityType 接口：建筑不走按坐标创建，保留占位 ---

    @Override
    public Entity<?, ?> create(TeamTypes team, float x, float y) {
        throw new UnsupportedOperationException("建筑按 tx/ty/angle 放置，走 create(team, tx, ty, angle)");
    }

    @Override
    public void update(Entity<?, ?> entity, float dt) {
    }

    @Override
    public void draw(Entity<?, ?> entity) {
    }

    @Override
    public void remove(Entity<?, ?> entity) {
        entity.remove();
    }
}
