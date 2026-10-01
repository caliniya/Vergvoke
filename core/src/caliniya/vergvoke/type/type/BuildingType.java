package caliniya.vergvoke.type.type;

import arc.util.pooling.Pools;

import caliniya.vergvoke.base.api.EntityType;
import caliniya.vergvoke.base.ecs.Building;
import caliniya.vergvoke.base.ecs.EntityArs;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.base.type.TeamTypes;
import caliniya.vergvoke.game.Entities;
import caliniya.vergvoke.game.data.WorldData;
import caliniya.vergvoke.type.module.ItemModule;
import caliniya.vergvoke.type.module.LiquidModule;
import caliniya.vergvoke.type.module.PowerModule;
import caliniya.vergvoke.world.Block;
import caliniya.vergvoke.world.blocks.defence.Turret;

/**
 * 建筑的类型载体（对齐 UnitType 的模式）：实体类型链接走基类 {@code Entity.type}，
 * 工厂在类型上（{@link #create}），实体侧不再有 block 引用字段。
 *
 * <p>过渡桥：Block 的配置/贴图还没迁进 type，本类先包一层 {@link #block}；
 * 等 Block 实现 EntityType（或配置直接搬进本类）后这层拆掉。
 * 方块通过 {@link Block#buildingType} 反向持有本类型。
 */
public class BuildingType implements EntityType {

    /** 本类型对应的方块（过渡桥，配置迁移完成前由它供给 size/psize/health/形状）。 */
    public final Block block;

    public BuildingType(Block block) {
        this.block = block;
    }

    /** 建筑像素边长（顺手把 Block.psize 刷到最新，省得忘记同步）。 */
    public float psize() {
        block.psize = block.size * WorldData.TILE_SIZE;
        return block.psize;
    }

    /** 类型级工厂：按 tx/ty/angle 放置（@Init/@Reset 缺位，派生初始化先在这里手动做）。 */
    public Building create(TeamTypes team, int tx, int ty, int angle) {
        float psize = psize();

        Building b = Pools.obtain(Building.class, Building::new);
        b.type = this;

        // BlockComp：占位
        b.tx = tx;
        b.ty = ty;
        b.angle = angle % 4;
        b.tileSize = block.size;
        b.shapeOffsets = block.shapeOffsets != null
                ? Block.getRotatedOffsets(b.angle, block.shapeOffsets)
                : null;

        // 派生坐标 / 战斗基础
        b.x = tx * WorldData.TILE_SIZE + psize / 2f;
        b.y = ty * WorldData.TILE_SIZE + psize / 2f;
        b.size = psize;
        b.team = team;
        b.teamData = team != null ? team.data() : null;
        b.maxHealth = block.health;
        if (b.health <= 0f) {
            b.health = block.health;
        }
        b.id = Entities.assignID();

        // 容量模块：对象池复用时不重建，保留原有存货
        if (b.item == null && block.capacity > 0) {
            b.item = new ItemModule(block.capacity);
            b.item.setFilter(block.allowItem);
        }
        if (b.liquid == null && block.liquidCapacity > 0) {
            b.liquid = new LiquidModule(block.liquidCapacity);
        }
        if (b.power == null && block.powerCapacity > 0) {
            b.power = new PowerModule(block.powerCapacity);
        }

        // TurretComp：配置拷贝（仅炮塔类建筑）；索敌半径走 TargetComp.range
        if (block instanceof Turret t) {
            b.reloadTime = t.reloadTime;
            b.rotateSpeed = t.rotateSpeed;
            b.bullet = t.bulletType;
            b.range = t.range;
        }
        b.self = b;

        // 运行时状态：对象池复用时这些残留会串剧本，逐帧复位
        b.reload = 0f;
        b.rotation = 0f;
        b.target = null;
        b.angleToTarget = 0f;
        b.retargetTimer = 0f;
        b.filter = null;

        EntityArs.Building.add(b);
        return b;
    }

    /**
     * 读档恢复完毕后再算一遍派生数据：占位形状 / 中心坐标 / 血上限 / 阵营数据。
     *
     * <p>组件的 {@code @Read} 只能恢复原始字段——派生数据要用 block 配置，而 {@code @Import}
     * 借不到基类那个泛型 {@code type} 字段，所以这一步留在类型侧，由给档路径显式调用。
     */
    public void rebuild(Building b) {
        float psize = psize();

        b.angle = b.angle % 4;
        b.tileSize = block.size;
        b.shapeOffsets = block.shapeOffsets != null
                ? Block.getRotatedOffsets(b.angle, block.shapeOffsets)
                : null;
        b.x = b.tx * WorldData.TILE_SIZE + psize / 2f;
        b.y = b.ty * WorldData.TILE_SIZE + psize / 2f;
        b.size = psize;
        b.maxHealth = block.health;
        b.teamData = b.team != null ? b.team.data() : null;
        b.self = b;
    }

    // --- EntityType 接口 ---

    /** 建筑按瓦片放置，不走按世界坐标创建；真要采样居中位置用 {@link #create} 的 tx/ty。 */
    @Override
    public Entity<?, ?> create(TeamTypes team, float x, float y) {
        throw new UnsupportedOperationException("建筑按 tx/ty/angle 放置，走 create(team, tx, ty, angle)");
    }

    /** 类型级每帧钩子：委托 {@link Block#update(Building, float)}（消灭后 EntityType.update 不再有人调）。 */
    @Override
    public void update(Entity<?, ?> entity, float dt) {
        if (entity instanceof Building b) {
            block.update(b, dt);
        }
    }

    /** 类型级绘制：委托 {@link Block#draw(Building)}。 */
    @Override
    public void draw(Entity<?, ?> entity) {
        if (entity instanceof Building b) {
            block.draw(b);
        }
    }

    /**
     * 摧毁：先卸载瓦片与导航标记，再注销容器与 ID，最后回池。
     *
     * <p>顺序不能反过来——回池后的对象会被下一个建造复用，瓦片里留着它就是悬垂引用。
     * 判死与 {@code World.setBuilding} 的覆盖都可能重复触发这里，故用 {@code type == null}
     * 作"已回池"标志挡二次销毁（{@code reset()} 会把它置空）。
     */
    @Override
    public void remove(Entity<?, ?> entity) {
        if (!(entity instanceof Building b))
            return;
        if (b.type == null)
            return;

        if (WorldData.world != null) {
            WorldData.world.removeBuilding(b);
        } else {
            EntityArs.Building.remove(b);
        }
        b.id = Entities.freeID(b.id);
        Pools.free(b);
    }
}
