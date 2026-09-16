package caliniya.vergvoke.type;

import arc.math.geom.*;
import arc.util.pooling.Pool.Poolable;
import arc.util.pooling.Pools;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.system.*;
import caliniya.vergvoke.system.world.*;
import caliniya.vergvoke.type.type.*;
import arc.math.geom.QuadTree.*;

public class Bullet implements Poolable, QuadTreeObject {
  public BulletType type;
  public Entity owner;
  public TeamTypes team; // 所属团队

  public float x, y;
  public float velX, velY;
  public float rotation;
  public float time = 0f;

  public int id;
  
  /** 是否已回收（volatile 线程间可见；防 double-free + 渲染跳过已回收子弹）。 */
  public volatile boolean recycled;

  /** 子弹对象池锁：创建（其他线程）与移除（BulletProcess 线程）跨线程操作 Pools，加锁防竞争。 */
  private static final Object poolLock = new Object();

  protected Bullet() {}

  /** 工厂方法 */
  public static Bullet create(
      BulletType type,
      Entity owner,
      float x,
      float y,
      float angle,
      float velocityX,
      float velocityY) {
    Bullet b;
    synchronized (poolLock) {
      b = Pools.obtain(Bullet.class, Bullet::new);
    }
    b.init(type, owner, x, y, angle, velocityX, velocityY);
    return b;
  }

  public void init(
      BulletType type,
      Entity owner,
      float x,
      float y,
      float angle,
      float velocityX,
      float velocityY) {
    this.type = type;
    this.owner = owner;
    this.team = (owner != null) ? owner.team : TeamTypes.Abort;

    this.x = x;
    this.y = y;
    this.rotation = angle;
    this.time = 0f;

    // 计算速度：子弹自身速度 + 发射者惯性
    float bulletSpeed = type.speed;
    float baseVx = (float) Math.cos(Math.toRadians(angle)) * bulletSpeed;
    float baseVy = (float) Math.sin(Math.toRadians(angle)) * bulletSpeed;

    this.velX = baseVx + velocityX;
    this.velY = baseVy + velocityY;

    // 自动添加到处理系统
    BulletProcess.it.addBullet(this);
  }

  @Override
  public void hitbox(Rect out) {
    out.set(x - type.size / 2f, y - type.size / 2f, type.size, type.size);
  }

  @Override
  public void reset() {
    // 邪修就是保留自身的类型信息，反正创建的时候总是会覆盖的
    // 原生对象池是不安全的，这很烦
    // type = null;
    recycled = false;
    owner = null;
    team = null;
    id = 0;
    x = 0;
    y = 0;
    velX = 0;
    velY = 0;
    time = 0;
  }

  public void remove() {
    if (recycled) return;
    recycled = true;
    synchronized (poolLock) {
      Pools.free(this);
    }
  }
}
