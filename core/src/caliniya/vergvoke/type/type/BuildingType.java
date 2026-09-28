package caliniya.vergvoke.type.type;

import caliniya.vergvoke.base.api.EntityType;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.base.type.TeamTypes;

/**
 * 建筑类型接线的占位实现：{@code @Entity(type=...)} 需要一个 EntityType 实现者，
 * 而 Block 还是 ContentType（"Block 实现 EntityType" 的方案没拍板）。
 * 拍板后本类要么变成真类型载体，要么被替换。
 */
public class BuildingType implements EntityType {

    @Override
    public Entity<?, ?> create(TeamTypes team, float x, float y) {
        throw new UnsupportedOperationException("建筑按 tx/ty/angle 放置，走 BuildingDef.create 工厂");
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
