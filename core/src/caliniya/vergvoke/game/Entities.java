package caliniya.vergvoke.game;

import arc.func.Boolf;
import arc.func.Cons;
import arc.math.Mathf;
import arc.struct.IntQueue;
import caliniya.vergvoke.base.ecs.EntityArs;
import caliniya.vergvoke.base.ecs.Unit;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.base.tool.Ar;
import caliniya.vergvoke.base.type.TeamTypes;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.type.*;

public class Entities {

    // --- ID 管理系统 ---
    private static int lastEntityID = 0;
    private static final IntQueue freeIDs = new IntQueue();

    /** 分配一个唯一的实体ID。 优先重用已回收的ID，否则生成新ID。 */
    public static int assignID() {
        if (freeIDs.size > 0) {
            return freeIDs.removeFirst();
        }
        return ++lastEntityID;
    }

    /** 回收一个实体ID，供后续实体重用。 同时直接返回-1便于调用 */
    public static int freeID(int id) {
        if (id > 0 && id <= lastEntityID) {
            freeIDs.addLast(id);
        }


        return -1;
    }

    /**
     * 注册一个从存档读取的指定ID，将其标记为「已占用」。
     *
     * @param id 从存档读取的实体ID（应为正数） 返回所注册的ID，最低为0
     */
    public static int checkoutID(int id) {
        if (id <= 0)
            return 0;

        if (id > lastEntityID) {
            // 中间空缺的ID全部回收，供后续 assignID 重用
            for (int i = lastEntityID + 1; i < id; i++) {
                freeIDs.addLast(i);
            }
            lastEntityID = id;
        } else {
            // id 落在已分配区间：若它正躺在空闲队列里，取出以防被重复分配
            freeIDs.removeValue(id);
        }
        return id;
    }

    /** 重置ID系统。 */
    public static void clearIDs() {
        lastEntityID = 0;
        freeIDs.clear();
    }

    // --- 待销毁队列 ---
    // 伤害结算可能在 BulletProcess 线程发生，但「注销容器 → 回收 ID → 回池」只能主线程做，
    // 否则后台线程把对象 free 掉之后，主线程还会拿着同一个引用再操作一次。
    private static final Ar<Entity> pendingKills = new Ar<>(false, 64);
    private static final Object KILL_LOCK = new Object();

    /** 登记一个已判死的实体，等主线程 {@code GameProcess} 统一销毁。任意线程可调。 */
    public static void markDead(Entity e) {
        if (e == null)
            return;
        synchronized (KILL_LOCK) {
            if (!pendingKills.contains(e)) {
                pendingKills.add(e);
            }
        }
    }

    /** 取出本帧登记的待死实体（同时清空队列）。由主线程调用。 */
    public static void drainDead(Ar<Entity> out) {
        synchronized (KILL_LOCK) {
            out.add(pendingKills);
            pendingKills.clear();
        }
    }

    /** 丢弃全部待销毁登记（换局 / 清档时用，防止跨局残留把已回池的对象再杀一次）。 */
    public static void clearDead() {
        synchronized (KILL_LOCK) {
            pendingKills.clear();
        }
    }

    // 处理 Unit
    public static void add(Unit... entities) {
        if (entities == null || entities.length == 0)
            return;
        EntityArs.Unit.add(entities);
    }

    /** 注销实体 */
    public static void remove(Unit... units) {
        if (units == null || units.length == 0)
            return;

        EntityArs.Unit.remove(units);
    }

    /**
     * 在指定范围内查找所有敌人实体。
     *
     * <p>射程判定按到中心的距离（子弹命中这类"真要碰到"的场合用它）。
     */
    public static void nearbyEnemies(
            TeamTypes sourceTeam, float x, float y, float r, Cons<Entity> consumer) {
        nearbyEnemies(sourceTeam, x, y, r, false, consumer);
    }

    /**
     * 在指定范围内查找所有敌人实体。
     *
     * @param byEdge true = 射程算到目标<b>边缘</b>（索敌用：大建筑的边伸进射程就算够得着）；
     *               false = 只算到中心（子弹命中用，否则子弹会在离建筑老远的地方就炸）
     */
    public static void nearbyEnemies(
            TeamTypes sourceTeam, float x, float y, float r, boolean byEdge, Cons<Entity> consumer) {
        float r2 = r * r;
        EntityArs.Unit.intersect(
                x - r,
                y - r,
                r * 2,
                r * 2,
                u -> {
                    if (u.team != sourceTeam && inRange(u, x, y, r2, byEdge)) {
                        consumer.get(u);
                    }
                });
        EntityArs.Building.intersect(
                x - r,
                y - r,
                r * 2,
                r * 2,
                b -> {
                    if (b.team != sourceTeam && inRange(b, x, y, r2, byEdge)) {
                        consumer.get(b);
                    }
                });
    }

    /** 四叉树已按包围盒粗筛过，这里只做精确的射程判定。 */
    private static boolean inRange(Entity e, float x, float y, float r2, boolean byEdge) {
        return byEdge ? e.dst2Surface(x, y) <= r2 : Mathf.dst2(x, y, e.x, e.y) <= r2;
    }

    /** 查找最近的敌人实体 */
    public static Entity closestEnemy(TeamTypes sourceTeam, float x, float y, float radius) {
        return closestEnemy(sourceTeam, x, y, radius, false);
    }

    /** 查找最近的敌人实体（{@code byEdge} 见 {@link #nearbyEnemies}）。 */
    public static Entity closestEnemy(
            TeamTypes sourceTeam, float x, float y, float radius, boolean byEdge) {
        final Entity[] result = { null };
        final float[] minDst2 = { radius * radius };

        nearbyEnemies(
                sourceTeam,
                x,
                y,
                radius,
                byEdge,
                enemy -> {
                    float dst2 = byEdge
                            ? enemy.dst2Surface(x, y)
                            : Mathf.dst2(x, y, enemy.x, enemy.y);
                    if (dst2 < minDst2[0]) {
                        minDst2[0] = dst2;
                        result[0] = enemy;
                    }
                });

        return result[0];
    }

    /** 查找最近的敌人实体 */
    /** 这个方法不会获得null值，如果没有敌人的话 就不会执行任何操作，所以不建议用这一个进行赋值 */
    public static void closestEnemy(
            TeamTypes sourceTeam, float x, float y, float radius, Cons<Entity> con) {
        closestEnemy(sourceTeam, x, y, radius, false, con);
    }

    /** 查找最近的敌人实体（{@code byEdge} 见 {@link #nearbyEnemies}）。 */
    public static void closestEnemy(
            TeamTypes sourceTeam, float x, float y, float radius, boolean byEdge, Cons<Entity> con) {
        final Entity[] result = { null };
        final float[] minDst2 = { radius * radius };

        nearbyEnemies(
                sourceTeam,
                x,
                y,
                radius,
                byEdge,
                enemy -> {
                    float dst2 = byEdge
                            ? enemy.dst2Surface(x, y)
                            : Mathf.dst2(x, y, enemy.x, enemy.y);
                    if (dst2 < minDst2[0]) {
                        minDst2[0] = dst2;
                        result[0] = enemy;
                    }
                });
        if (result[0] != null) {
            con.get(result[0]);
        }
    }

    /**
     * 查找最近的敌人实体（带过滤 + 回调）
     *
     * @param filter   过滤条件，返回 true 才纳入考虑
     * @param consumer 最近实体通过此回调传出，不直接返回值
     */
    public static void closestEnemy(
            TeamTypes sourceTeam,
            float x,
            float y,
            float radius,
            Boolf<Entity> filter,
            Cons<Entity> consumer) {
        closestEnemy(sourceTeam, x, y, radius, filter, false, consumer);
    }

    /** 带过滤的最近敌人（{@code byEdge} 见 {@link #nearbyEnemies}）。 */
    public static void closestEnemy(
            TeamTypes sourceTeam,
            float x,
            float y,
            float radius,
            Boolf<Entity> filter,
            boolean byEdge,
            Cons<Entity> consumer) {
        final Object[] result = { null };
        final float[] minDst2 = { radius * radius };

        nearbyEnemies(
                sourceTeam,
                x,
                y,
                radius,
                byEdge,
                enemy -> {
                    if (!filter.get(enemy))
                        return;
                    float dst2 = byEdge
                            ? enemy.dst2Surface(x, y)
                            : Mathf.dst2(x, y, enemy.x, enemy.y);
                    if (dst2 < minDst2[0]) {
                        minDst2[0] = dst2;
                        result[0] = enemy;
                    }
                });

        if (result[0] != null) {
            consumer.get((Entity) result[0]);
        }
    }
}
