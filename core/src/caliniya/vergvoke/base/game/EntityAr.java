package caliniya.vergvoke.base.game;

import arc.func.*;
import arc.math.geom.*;
import arc.math.geom.QuadTree.*;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import caliniya.vergvoke.base.tool.Ar;

/**
 * 实体组 —— 管理同一类型实体的集合。
 *
 * <p>
 * 功能:
 *
 * <ul>
 * <li><b>平铺数组迭代</b> — O(n) 遍历所有实体，无序数组支持 O(1) 删除
 * <li><b>四叉树空间查询</b> — O(log n + k) 矩形查询，自动维护空间索引
 * <li><b>ID 映射</b> — O(1) 按 ID 查找实体，使用 ConcurrentHashMap 保证线程安全
 * </ul>
 *
 * <p>
 *
 * @param <T> 实体类型，必须实现 {@link QuadTreeObject} 以提供碰撞盒(这样子弹也可以被加入到实体组中，尽管它并不属于实体)
 */
public class EntityAr<T extends QuadTreeObject> implements Iterable<T> {

    private static final int DEFAULT_CAPACITY = 32;

    /** 平铺数组 —— 无序，支持 O(1) 尾删 */
    public Ar<T> array;

    /** 四叉树 —— 用于空间查询 */
    private QuadTree<T> tree;

    /** ID → 实体的映射，使用 ConcurrentHashMap 保证线程安全 */
    private final ConcurrentHashMap<Integer, T> idMap;

    /** 从实体提取 ID 的函数 */
    private final Intf<T> idGetter;

    /** 标记正在批量清理，防止递归 remove */
    private boolean clearing;

    /** 当前迭代索引，用于 remove 时修正 */
    private int iteratorIndex;

    /** 读写锁 —— 允许多个读操作并发，写操作互斥 */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    // ==================== 构造 ====================

    /**
     * @param idGetter 从实体提取 ID 的函数
     */
    public EntityAr(Intf<T> idGetter) {
        if (idGetter == null)
            throw new IllegalArgumentException("idGetter must not be null");

        this.idGetter = idGetter;
        this.array = new Ar<>(false, DEFAULT_CAPACITY);
        this.tree = new QuadTree<>(new Rect(0, 0, 0, 0));
        this.idMap = new ConcurrentHashMap<>();
    }

    /** 默认构造器，使用实体自身的 ID 获取函数（假设实体实现了 getID 方法） */
    public EntityAr() {
        this(
                e -> {
                    if (e instanceof Entity) {
                        return ((Entity) e).id;
                    }
                    throw new RuntimeException(
                            "Entity does not have getId() method, please use EntityAr(Intf<T> idGetter) constructor");
                });
    }

    /**
     * 向组中添加一个或多个实体（批量插入，减少锁竞争）
     *
     * @param entities 可变长度参数，可以传 0 个、1 个或多个实体
     */
    @SafeVarargs
    public final void add(T... entities) {
        if (entities == null || entities.length == 0)
            return;

        // 检查是否有 null
        for (int i = 0; i < entities.length; i++) {
            if (entities[i] == null) {
                throw new IllegalArgumentException("Cannot add null entity at index " + i);
            }
        }

        writeLock.lock();
        try {
            for (T entity : entities) {
                if (idMap.containsKey(idGetter.get(entity))) {
                    continue;
                }
                array.add(entity);
                tree.insert(entity);

                int id = idGetter.get(entity);
                if (id >= 0) {
                    idMap.put(id, entity);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    /** 从组中移除实体（同时从四叉树和 ID 映射注销） */
    @SafeVarargs
    public final void remove(T... entities) {
        if (clearing || entities == null || entities.length == 0)
            return;

        writeLock.lock();
        try {
            for (T entity : entities) {
                if (entity == null)
                    continue;

                int idx = array.indexOf(entity, true);
                if (idx == -1)
                    continue;

                array.remove(entity);
                tree.remove(entity);

                int id = idGetter.get(entity);
                if (id >= 0) {
                    idMap.remove(id);
                }

                // 修正正在进行的迭代索引
                if (iteratorIndex >= idx) {
                    iteratorIndex--;
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    /** 清空所有实体，并对每个实体调用 {@code entityRemoved} 回调 */
    public void clear(Cons<T> entityRemoved) {
        writeLock.lock();
        try {
            clearing = true;

            for (int i = array.size - 1; i >= 0; i--) {
                entityRemoved.get(array.items[i]);
            }
            array.clear();
            tree.clear();
            idMap.clear();

            clearing = false;
        } finally {
            writeLock.unlock();
        }
    }

    /** 清空所有实体（不调用回调） */
    public void clear() {
        writeLock.lock();
        try {
            clearing = true;
            array.clear();
            tree.clear();
            idMap.clear();
            clearing = false;
        } finally {
            writeLock.unlock();
        }
    }

    public void move(T entity, float newX, float newY) {
        // 总是重建四叉树节点（位置已由调用方更新），保证 intersect 查询可靠
        writeLock.lock();
        try {
            tree.remove(entity);
            tree.insert(entity);
        } finally {
            writeLock.unlock();
        }
    }

    // ==================== 查询 ====================

    public boolean isEmpty() {
        readLock.lock();
        try {
            return array.isEmpty();
        } finally {
            readLock.unlock();
        }
    }

    public int size() {
        readLock.lock();
        try {
            return array.size;
        } finally {
            readLock.unlock();
        }
    }

    // ==================== ID 映射 ====================

    /** 按 ID 查找实体，O(1)，使用 ConcurrentHashMap 保证线程安全 */
    public T getByID(int id) {
        return idMap.get(id);
    }

    /** 检查指定 ID 的实体是否存在 */
    public boolean containsID(int id) {
        return idMap.containsKey(id);
    }

    // ==================== 空间查询 ====================

    /** 重新设置四叉树的覆盖范围。当世界尺寸变化时调用。会重建四叉树并重新插入所有实体。 */
    public void resize(float x, float y, float w, float h) {
        writeLock.lock();
        try {
            QuadTree<T> newTree = new QuadTree<>(new Rect(x, y, w, h));
            for (int i = 0; i < array.size; i++) {
                T entity = array.items[i];
                newTree.insert(entity);
            }
            tree = newTree;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 矩形范围查询，通过回调消费每个命中的实体。
     *
     * <pre>{@code
     * group.intersect(x, y, w, h, entity -> {
     *     // 处理每个命中的 entity
     * });
     * }</pre>
     */
    public void intersect(float x, float y, float w, float h, Cons<T> out) {
        readLock.lock();
        try {
            if (isEmpty())
                return;
            tree.intersect(x, y, w, h, out);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 检查范围内是否存在任何实体。
     *
     * @return true 如果范围内至少有一个实体
     */
    public boolean any(float x, float y, float w, float h) {
        readLock.lock();
        try {
            if (isEmpty())
                return false;
            return tree.any(x, y, w, h);
        } finally {
            readLock.unlock();
        }
    }

    // ==================== 迭代 ====================

    /** 遍历所有实体 */
    public void each(Cons<? super T> cons) {
        readLock.lock();
        try {
            for (int i = 0; i < array.size; i++) {
                cons.get(array.get(i));
            }
        } finally {
            readLock.unlock();
        }
    }

    // 有写操作的遍历
    public void eachWrited(Cons<? super T> cons) {
        writeLock.lock();
        try {
            for (int i = 0; i < array.size; i++) {
                cons.get(array.get(i));
            }
        } finally {
            writeLock.unlock();
        }
    }

    /** 遍历满足谓词的实体 ，不能写 */
    public void each(Boolf<T> filter, Cons<? super T> cons) {
        readLock.lock();
        try {
            for (int i = 0; i < array.size; i++) {
                T item = array.items[i];
                if (filter.get(item)) {
                    cons.get(item);
                }
            }
        } finally {
            readLock.unlock();
        }
    }

    /** 这个方法并非线程安全，迭代器没有任何锁 也并非快照 */
    @Override
    public Iterator<T> iterator() {
        return array.iterator();
    }

    @Override
    public String toString() {
        readLock.lock();
        try {
            return "EntityGroup[size=" + array.size + ", idMapSize=" + idMap.size() + "]";
        } finally {
            readLock.unlock();
        }
    }
}
