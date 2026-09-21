package caliniya.vergvoke.base.ecs;

import arc.graphics.g2d.TextureRegion;
import arc.math.geom.Point2;
import arc.util.io.Reads;
import arc.util.io.Writes;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.base.tool.Ar;
import caliniya.vergvoke.base.type.TeamTypes;
import caliniya.vergvoke.type.Weapon;
import caliniya.vergvoke.type.type.UnitType;
import java.lang.Override;
import caliniya.vergvoke.annotation.Annotations.*;
import arc.math.geom.*;
import caliniya.vergvoke.base.tool.*;

public class Unit extends Entity<UnitType> {
  public boolean isSelected;

  public float speed;

  public float speedt;

  public float speedX;

  public float speedY;

  public float targetX;

  public float targetY;

  public float arriveRange;

  public boolean moving;

  public boolean pathed;

  public boolean velocityDirty;

  public Ar<Point2> path;

  public int pathIndex;

  public TextureRegion region;

  public TextureRegion cell;

  public Ar<Weapon> weapons;

  public Entity<?> target;

  public boolean canShoot;

  public float hitHalfWidth;

  /**
   * 按 {@code @Entity.type = UnitType} 创建实例，并委托类型填充配置（自动生成）。
   */
  public static Unit create(UnitType type) {
    Unit e = new Unit();
    e.type = type;
    if (type != null) {
      type.create(e);
    }
    return e;
  }

  /**
   * 按类型创建并设置阵营与坐标（自动生成）。
   */
  public static Unit create(TeamTypes team, UnitType type, float x, float y) {
    Unit e = create(type);
    e.team = team;
    e.x = x;
    e.y = y;
    return e;
  }

  /**
   * 覆写基类 boolean contains(worldX, worldY)（来自组件 HitComp，@OverrideEntity，自动生成）。
   */
  @Override
  public boolean contains(float worldX, float worldY) {
    {
        float half = hitHalfWidth > 0.0F ? hitHalfWidth : (size > 0.0F ? size * 0.5F : 4.0F);
        float dx = worldX - x;
        float dy = worldY - y;
        return dx * dx + dy * dy <= half * half;
    }
  }

  /**
   * 来自组件 CombatComp#updateWeapons（自动生成，方法体字段已扁平到实体）。
   */
  public void updateWeapons(float delta) {
    {
        if (weapons == null) {
            return;
        }
        for (int i = 0; i < weapons.size; i++) {
            Weapon w = weapons.get(i);
            if (w != null) {
                w.update(delta, canShoot);
            }
        }
    }
  }

  /**
   * proc = "main"（或未指定）的组件更新，按 @Component.index 顺序直接注入（自动生成）。
   */
  @Override
  public void update(float delta) {
    // MoveComp#update
    {
        float range = arriveRange > 0.0F ? arriveRange : 2.0F;
        float ox = x;
        float oy = y;
        float dx = targetX - x;
        float dy = targetY - y;
        if (path == null && dx * dx + dy * dy < range * range) {
            x = targetX;
            y = targetY;
            speedX = 0.0F;
            speedY = 0.0F;
        } else {
            x += speedX * delta;
            y += speedY * delta;
        }
        moving = x != ox || y != oy;
    }
    // CombatComp#update
    {
    }
  }

  /**
   * 按 @Component.index 顺序把各组件的读写直接铺进来（基类 Entity 的 write(Writes) 还是抽象方法，所以这里不调用 super，自动生成）。
   */
  @Override
  public void write(Writes w) {
    // StateComp（@Component.index = 1，@Save 字段）
    w.bool(isSelected);
    // MoveComp（@Component.index = 2，@Save 字段）
    w.f(targetX);
    w.f(targetY);
  }

  /**
   * 按 @Component.index 顺序把各组件的读写直接铺进来（基类 Entity 的 read(Reads) 还是抽象方法，所以这里不调用 super，自动生成）。
   */
  @Override
  public void read(Reads r) {
    // StateComp（@Component.index = 1，@Save 字段）
    isSelected = r.bool();
    // MoveComp（@Component.index = 2，@Save 字段）
    targetX = r.f();
    targetY = r.f();
  }

  /**
   * TODO 基类尚未提供实现，先留空（自动生成）。
   */
  @Override
  public void draw() {
  }

  /**
   * TODO 基类尚未提供实现，先留空（自动生成）。
   */
  @Override
  public void remove() {
  }

  /**
   * TODO 基类尚未提供实现，先留空（自动生成）。
   */
  @Override
  public void kill() {
  }
}
