package caliniya.vergvoke.system.world;

import arc.math.geom.Rect;
import arc.util.Log;
import caliniya.vergvoke.type.ability.api.*;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.base.tool.Ar;
import caliniya.vergvoke.base.game.EntityAr;
import caliniya.vergvoke.game.Entities;
import caliniya.vergvoke.game.data.WorldData;
import caliniya.vergvoke.type.Bullet;

/** 子弹处理系统。 管理子弹生命周期、移动、碰撞及渲染数据同步，后台线程运行，双缓冲保证线程安全。 */
public class BulletProcess extends caliniya.vergvoke.system.System<BulletProcess> {

    /** 全局实例（由 Data.loadSystems() 创建；跨线程访问用）。 */
    public static BulletProcess it;

    /**
     * 双缓冲专用锁。 逻辑线程交换 WorldData.bullets 与 renderBuffer 引用时，
     * 必须用此固定锁对象，避免锁在不同实例上导致互斥失效。
     */
    public final Object BULLET_LOCK = new Object();

    // ==================== ID 生成系统 ====================

    /** 子弹ID计数器（从1开始，0表示无效ID） 使用 volatile 保证多线程可见性 */
    private static volatile int nextBulletId = 1;

    /** 空闲ID池（用于回收复用） 存储被销毁子弹的ID，供新子弹复用 */
    private final Ar<Integer> freeIds = new Ar<>(false, 256);

    /**
     * 为子弹分配唯一ID（线程安全）
     *
     * @return 新的唯一ID
     */
    private synchronized int allocateBulletId() {
        // 优先从空闲池取
        if (!freeIds.isEmpty()) {
            return freeIds.remove(freeIds.size - 1);
        }
        // 没有空闲ID，分配新ID
        return nextBulletId++;
    }

    /**
     * 回收子弹ID（供复用）
     *
     * @param id 需要回收的ID
     */
    private synchronized void recycleBulletId(int id) {
        if (id > 0) {
            freeIds.add(id);
        }
    }

    // ==================== 数据存储 ====================

    /** 待处理子弹队列（线程安全） */
    private final EntityAr<Bullet> pendingBullets = new EntityAr<>(b -> b.id);

    /** 活跃子弹列表（逻辑线程专用） */
    public final EntityAr<Bullet> activeBullets = new EntityAr<>(b -> b.id);

    /** 渲染缓冲区（与 WorldData.bullets 交换） */
    private EntityAr<Bullet> renderBuffer = new EntityAr<>(b -> b.id);

    /** 待删除列表（避免遍历时修改） */
    private final Ar<Bullet> toRemove = new Ar<>(false, 256);

    /** 本帧刚被击杀的实体（由 BulletProcess 写入，GameProcess 读出） */
    private final Ar<Entity> freshKills = new Ar<>(false, 64);

    private final Object KILL_LOCK = new Object();

    @Override
    public BulletProcess init() {
        // 内部子弹四叉树必须 resize 到世界大小，否则 intersect 查不到子弹
        if (WorldData.world != null) {
            activeBullets.resize(
                    0f, 0f, WorldData.world.W * WorldData.TILE_SIZE, WorldData.world.H * WorldData.TILE_SIZE);
        }
        return super.init(true);
    }

    /** 世界尺寸变化时重新设置内部子弹四叉树范围（由 WorldData 调用）。 */
    public void resizeTree(float w, float h) {
        activeBullets.resize(0f, 0f, w, h);
    }

    /** 添加子弹（线程安全） 会自动为子弹分配唯一ID */
    public void addBullet(Bullet b) {
        if (b == null)
            return;

        // 为子弹分配ID（如果还没有）
        if (b.id <= 0) {
            b.id = allocateBulletId();
        }

        synchronized (pendingBullets) {
            pendingBullets.add(b);
        }
    }

    /** 批量添加子弹 */
    public void addBullets(Bullet... bullets) {
        if (bullets == null || bullets.length == 0)
            return;

        for (Bullet b : bullets) {
            if (b != null && b.id <= 0) {
                b.id = allocateBulletId();
            }
        }

        synchronized (pendingBullets) {
            pendingBullets.add(bullets);
        }
    }

    /** 清除所有子弹并重置 */
    public void clearAll() {
        // 回收所有子弹ID
        synchronized (pendingBullets) {
            pendingBullets.each(
                    b -> {
                        if (b != null) {
                            b.remove();
                            recycleBulletId(b.id);
                        }
                    });
            pendingBullets.clear();
        }

        // 回收活跃子弹的ID
        activeBullets.each(
                b -> {
                    if (b != null) {
                        b.remove();
                        recycleBulletId(b.id);
                    }
                });
        activeBullets.clear();
        renderBuffer.clear();

        synchronized (BULLET_LOCK) {
            // 回收 WorldData.bullets 中的子弹ID
            WorldData.bullets.each(
                    b -> {
                        if (b != null)
                            recycleBulletId(b.id);
                    });
            WorldData.bullets.clear();
        }
    }

    @Override
    public void update(float delta) {
        // 合并待处理子弹
        synchronized (pendingBullets) {
            // 注意：这里传入的是数组，需要确保加锁安全
            pendingBullets.each(
                    b -> {
                        if (b != null) {
                            activeBullets.add(b);
                        }
                    });
            pendingBullets.clear();
        }
        toRemove.clear();

        // 1. 移动所有子弹（写锁遍历：内部 activeBullets.move 需要写锁，避免读锁内升级写锁死锁）
        activeBullets.eachWrited(
                b -> {
                    b.time += delta;
                    if (b.time >= b.type.lifetime) {
                        // b.type.despawn(b);
                        if (!toRemove.contains(b))
                            toRemove.add(b);
                        return;
                    }

                    float nextX = b.x + b.velX * delta;
                    float nextY = b.y + b.velY * delta;
                    b.x = nextX;
                    b.y = nextY;
                    activeBullets.move(b, nextX, nextY);
                });

        // 2. 力场拦截（用移动后的新位置，拦截进入力场的子弹）
        interceptBullets();

        // 3. 命中检测（只对未被拦截/未过期的剩余子弹）
        activeBullets.each(
                b -> {
                    if (toRemove.contains(b))
                        return; // 已拦截/已过期
                    Entities.closestEnemy(
                            b.team,
                            b.x,
                            b.y,
                            b.type.size,
                            e -> {
                                float prevHealth = e.health;
                                b.type.hit(b, e);
                                if (!toRemove.contains(b))
                                    toRemove.add(b);
                                if (prevHealth > 0 && e.health <= 0) {
                                    synchronized (KILL_LOCK) {
                                        freshKills.add(e);
                                    }
                                }
                            });
                });

        // 批量删除：先从 EntityAr 注销 + 回收 ID（暂不归还对象池，
        // 避免渲染线程在缓冲交换前读到已 free 的子弹——free 后坐标被 reset 成 0,0 会闪出幽灵子弹）
        toRemove.each(
                b -> {
                    activeBullets.remove(b);
                    recycleBulletId(b.id);
                });

        // 更新渲染缓冲区并交换
        renderBuffer.clear();
        // 批量添加所有活跃子弹到渲染缓冲区
        activeBullets.each(renderBuffer::add);

        synchronized (BULLET_LOCK) {
            EntityAr<Bullet> temp = WorldData.bullets;
            WorldData.bullets = renderBuffer;
            renderBuffer = temp;
        }

        // 交换完成后再归还对象池（渲染已切换到不含这些子弹的新缓冲）
        toRemove.each(b -> b.remove());
        toRemove.clear();
    }

    /**
     * 力场拦截：遍历力场实体注册表， 用 AABB 粗筛（四叉树）+ 正多边形精判拦截进入力场的子弹。
     *
     * <p>
     * 被拦截的子弹进入 toRemove，由批量删除统一回收。 无效的力场实体（已死亡/能力关闭）从注册表延迟清理。
     */
    /** 力场拦截复用的 AABB（后台线程专用，避免与其他线程的 Tmp 竞争）。 */
    private final Rect aabbRect = new Rect();

    private void interceptBullets() {
        synchronized (ForceField.force) {
            Ar<ForceField> list = ForceField.force;
            Ar<ForceField> toCleanup = null;

            for (int i = 0; i < list.size; i++) {
                ForceField field = list.get(i);
                if (field == null) {
                    if (toCleanup == null)
                        toCleanup = new Ar<>(false, 4);
                    toCleanup.add(field);
                    continue;
                }

                field.hitbox(aabbRect);
                activeBullets.intersect(
                        aabbRect.x,
                        aabbRect.y,
                        aabbRect.width,
                        aabbRect.height,
                        b -> {
                            if (toRemove.contains(b))
                                return; // 已被命中/拦截
                            if (field.contains(b.x, b.y)) {
                                if (field.onBullet(b)) {
                                    toRemove.add(b);
                                }
                            }
                        });
            }

            if (toCleanup != null) {
                for (ForceField f : toCleanup)
                    list.remove(f, true);
            }
        }
    }

    /** 清空新鲜死亡队列，返回本帧新增的死亡实体列表（由 GameProcess 调用） */
    public void drainFreshKills(Ar<Entity> out) {
        synchronized (KILL_LOCK) {
            out.add(freshKills);
            freshKills.clear();
        }
    }

    /** 获取当前子弹ID池状态 */
    public void debug() {
        Log.info(
                "Bullets: active="
                        + activeBullets.size()
                        + ", pending="
                        + pendingBullets.size()
                        + ", render="
                        + renderBuffer.size()
                        + ", nextId="
                        + nextBulletId
                        + ", freeIds="
                        + freeIds.size);
    }
}
