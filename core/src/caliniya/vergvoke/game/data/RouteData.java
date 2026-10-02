package caliniya.vergvoke.game.data;

import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.struct.IntMap;
import arc.struct.IntQueue;
import arc.struct.PQueue;
import caliniya.vergvoke.base.ecs.Building;
import caliniya.vergvoke.base.tool.Ar;
import caliniya.vergvoke.world.*;
import arc.util.pooling.Pools;
import caliniya.vergvoke.type.*;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RouteData {

  // 最大支持的跨越能力等级
  public static final int MAX_CAPABILITY = 2;

  // 局部更新时，重算距离场的最大范围
  private static final int UPDATE_RANGE = 24;
  private static final int MAX_DIST_VAL = 9999;
  // 瓦片数不超过它时，导航更新直接全量重算距离场（精确）；大图才用有界泛洪（近似）
  private static final int FULL_RECALC_MAX_TILES = 300_000;
  /** 对角步附加代价 (√2 - 1)，octile 距离用。 */
  private static final float DIAG_EXTRA = (float) (Math.sqrt(2) - 1);

  // --- HPA*：chunk 抽象层 ---
  /** chunk 边长（格）。 */
  public static final int CHUNK = 10;
  /** 导航变更对寻路的波及半径（有界泛洪范围 + 腐蚀级联），脏 chunk 标记用。 */
  private static final int NAV_DIRTY_REACH = UPDATE_RANGE + MAX_CAPABILITY + 2;

  private static int W, H;
  private static int chunksX, chunksY;
  public static NavLayer[] layers;

  /** 寻路（读）与导航更新（写）分离：多路寻路可并发，不被一次局部导航更新阻塞。 */
  public static final ReadWriteLock updateLock = new ReentrantReadWriteLock();
  /** chunk 边缓存重建互斥：读锁内多路 findPath 可能同时触到同一个脏 chunk。 */
  private static final Object rebuildLock = new Object();

  private RouteData() {}

  /** 内部类：导航层数据 */
  public static class NavLayer {
    public boolean[] baseSolidMap; // 基础障碍物 (是否是墙)
    public int[] clearanceMap; // 距离场（到最近障碍的步数，实心 = 0；可通行 iff clearance > unitSize）

    // --- HPA* 抽象层（惰性构建：chunkNav 标脏，findPath 触到才重算）---
    public IntMap<Entrance> entrances; // 门口，key = ay * W + ax（A 侧瓦片）
    public ChunkNav[] chunkNav; // chunksX * chunksY

    public NavLayer(int size) {
      baseSolidMap = new boolean[size];
      clearanceMap = new int[size];
      entrances = new IntMap<>();
      chunkNav = new ChunkNav[chunksX * chunksY];
    }
  }

  /** 门口：相邻 chunk 边界两侧的通行瓦片对（A = 左/上 chunk，B = 右/下 chunk）。 */
  private static class Entrance {
    int ax, ay, bx, by;
  }

  /** 抽象边：同一 chunk 内两个门口之间的平滑路径缓存（无向；unitSize=0 搜索 + 最小 clearance 注解）。 */
  private static class AbstractEdge {
    int from, to; // 门口 key
    float cost; // 入口瓦片间路径代价（octile 长度）
    int minClear; // 路径上的最小 clearance（体积注解：可用 iff minClear > unitSize）
    Ar<Point2> path; // 入口瓦片间的格路径（已经是平滑后的稀疏拐点）
  }

  /** 每个 chunk 的抽象边缓存 + 边界门口的本侧入口。重建时整体换新对象（读者持有的旧引用保持有效）。 */
  private static class ChunkNav {
    final int chunkIndex;
    final Ar<AbstractEdge> edges = new Ar<>();
    final Ar<int[]> crossings = new Ar<>(); // {nodeKey, entryX, entryY}
    boolean dirty = true;

    ChunkNav(int chunkIndex) {
      this.chunkIndex = chunkIndex;
    }
  }

  /** 起点/终点到本 chunk 边界门口的连接。 */
  private static class Conn {
    int key;
    float cost;
    Ar<Point2> path; // 从单位位置到入口瓦片（已平滑）
  }

  /** 门口抽象图 A* 节点。 */
  private static class ANode implements Comparable<ANode> {
    int key;
    float g, f;
    ANode parent;
    AbstractEdge edge; // 从 parent 走到本节点用的抽象边
    boolean reversed; // 边是否反向使用（拼接时 path 要倒序）
    Conn start; // 根节点持有的起点连接
    int viaChunk; // 本节点经由哪个 chunk 到达（门口两侧入口的切换要记 1 步代价）

    @Override
    public int compareTo(ANode o) {
      return Float.compare(f, o.f);
    }
  }

  // ==================== 初始化 ====================

  /** 初始化全图数据 */
  public static void init() {
    World world = WorldData.world;
    W = world.W;
    H = world.H;
    int size = W * H;

    chunksX = (W + CHUNK - 1) / CHUNK;
    chunksY = (H + CHUNK - 1) / CHUNK;

    layers = new NavLayer[MAX_CAPABILITY + 1];

    // 1. 初始化 Layer 0
    layers[0] = new NavLayer(size);
    for (int i = 0; i < size; i++) {
      layers[0].baseSolidMap[i] = world.isSolid(i);
    }

    // 2. 初始化腐蚀层 (Layer 1 ~ MAX)
    for (int cap = 1; cap <= MAX_CAPABILITY; cap++) {
      layers[cap] = new NavLayer(size);
      erodeMapFull(layers[cap - 1].baseSolidMap, layers[cap].baseSolidMap);
    }

    // 3. 计算距离场
    for (int cap = 0; cap <= MAX_CAPABILITY; cap++) {
      calcClearanceFull(layers[cap]);
    }
  }

  // ==================== 导航更新（写锁；只允许 WorldData 门面调用）====================

  /** 动态更新某个方块的状态 自动处理级联腐蚀和局部距离场重算 */
  public static void updateBlock(int x, int y, boolean isSolid) {
    updateLock.writeLock().lock();
    try {
      if (!isValid(x, y)) return;

      // 1. 更新 Layer 0 基础数据
      NavLayer l0 = layers[0];
      int index = coordToIndex(x, y);
      if (l0.baseSolidMap[index] == isSolid) return; // 状态没变
      l0.baseSolidMap[index] = isSolid;

      // 更新 Layer 0 的局部区域
      refreshClearance(l0, x, y, x, y);

      // 2. 级联更新腐蚀层 (Layer 1 ~ MAX)
      for (int cap = 1; cap <= MAX_CAPABILITY; cap++) {
        NavLayer prev = layers[cap - 1];
        NavLayer curr = layers[cap];

        int range = cap;
        int minX = Math.max(0, x - range);
        int maxX = Math.min(W - 1, x + range);
        int minY = Math.max(0, y - range);
        int maxY = Math.min(H - 1, y + range);

        boolean changed = false;
        for (int ry = minY; ry <= maxY; ry++) {
          for (int rx = minX; rx <= maxX; rx++) {
            boolean oldState = curr.baseSolidMap[coordToIndex(rx, ry)];
            boolean newState = calcErosionAt(prev.baseSolidMap, rx, ry);
            if (oldState != newState) {
              curr.baseSolidMap[coordToIndex(rx, ry)] = newState;
              changed = true;
            }
          }
        }

        if (changed) {
          refreshClearance(curr, minX, minY, maxX, maxY);
        }
      }

      markNavDirty(x, y, x, y);
    } finally {
      updateLock.writeLock().unlock();
    }
  }

  /**
   * 拆建筑清占：获取 (bx,by) 处的建筑，将其占据的所有坐标批量标记为空（通行）。
   * 调用方（WorldData 门面）必须保证建筑还在 world 里——本方法靠 getBuilding 找它拿占位形状。
   */
  public static void updateBlock(int bx, int by) {
    updateLock.writeLock().lock();
    try {
      Building build = WorldData.world.getBuilding(bx, by);
      if (build == null) return;

      // 批量清除所有占位瓦片 + 记包围盒（一次性泛洪，别逐格刷）
      int[] bbox = {W, H, -1, -1};
      boolean[] anyCleared = new boolean[1];
      build.getOccupiedCoords(
          (tx, ty) -> {
            if (!isValid(tx, ty)) return;
            int idx = coordToIndex(tx, ty);
            if (layers[0].baseSolidMap[idx]) {
              layers[0].baseSolidMap[idx] = false;
              anyCleared[0] = true;
              if (tx < bbox[0]) bbox[0] = tx;
              if (ty < bbox[1]) bbox[1] = ty;
              if (tx > bbox[2]) bbox[2] = tx;
              if (ty > bbox[3]) bbox[3] = ty;
            }
          });
      if (!anyCleared[0]) return;
      int minX = bbox[0], minY = bbox[1], maxX = bbox[2], maxY = bbox[3];

      refreshClearance(layers[0], minX, minY, maxX, maxY);

      // 级联更新腐蚀层：以建筑包围盒 + 腐蚀范围为界
      for (int cap = 1; cap <= MAX_CAPABILITY; cap++) {
        NavLayer prev = layers[cap - 1];
        NavLayer curr = layers[cap];
        int range = cap;
        int uminX = Math.max(0, minX - range);
        int umaxX = Math.min(W - 1, maxX + range);
        int uminY = Math.max(0, minY - range);
        int umaxY = Math.min(H - 1, maxY + range);

        boolean changed = false;
        for (int ry = uminY; ry <= umaxY; ry++) {
          for (int rx = uminX; rx <= umaxX; rx++) {
            boolean oldState = curr.baseSolidMap[coordToIndex(rx, ry)];
            boolean newState = calcErosionAt(prev.baseSolidMap, rx, ry);
            if (oldState != newState) {
              curr.baseSolidMap[coordToIndex(rx, ry)] = newState;
              changed = true;
            }
          }
        }
        if (changed) {
          refreshClearance(curr, uminX, uminY, umaxX, umaxY);
        }
      }

      markNavDirty(minX, minY, maxX, maxY);
    } finally {
      updateLock.writeLock().unlock();
    }
  }

  /**
   * 放置建筑方块：将 Block 在 (x,y) 处占据的所有坐标标记为实心。
   */
  public static void updateBlock(int x, int y, Block block) {
    updateLock.writeLock().lock();
    try {
      if (!isValid(x, y) || block == null) return;

      NavLayer l0 = layers[0];
      int[] minX = {W}, maxX = {0}, minY = {H}, maxY = {0};

      // 遍历方块占据的所有坐标，批量标记实心
      if (block.shapeOffsets != null) {
        for (int i = 0; i < block.shapeOffsets.length; i += 2) {
          int tx = x + block.shapeOffsets[i];
          int ty = y + block.shapeOffsets[i + 1];
          if (isValid(tx, ty)) {
            int idx = coordToIndex(tx, ty);
            l0.baseSolidMap[idx] = true;
            if (tx < minX[0]) minX[0] = tx;
            if (tx > maxX[0]) maxX[0] = tx;
            if (ty < minY[0]) minY[0] = ty;
            if (ty > maxY[0]) maxY[0] = ty;
          }
        }
      } else {
        int s = block.size;
        for (int dy = 0; dy < s; dy++) {
          for (int dx = 0; dx < s; dx++) {
            int tx = x + dx;
            int ty = y + dy;
            if (isValid(tx, ty)) {
              l0.baseSolidMap[coordToIndex(tx, ty)] = true;
            }
          }
        }
        minX[0] = Math.max(0, x);
        maxX[0] = Math.min(W - 1, x + s - 1);
        minY[0] = Math.max(0, y);
        maxY[0] = Math.min(H - 1, y + s - 1);
      }

      // 一次性更新距离场 (包围盒范围)
      refreshClearance(l0, minX[0], minY[0], maxX[0], maxY[0]);

      // 级联腐蚀：以包围盒 + 腐蚀范围
      for (int cap = 1; cap <= MAX_CAPABILITY; cap++) {
        NavLayer prev = layers[cap - 1];
        NavLayer curr = layers[cap];
        int range = cap;
        int uminX = Math.max(0, minX[0] - range);
        int umaxX = Math.min(W - 1, maxX[0] + range);
        int uminY = Math.max(0, minY[0] - range);
        int umaxY = Math.min(H - 1, maxY[0] + range);

        boolean changed = false;
        for (int ry = uminY; ry <= umaxY; ry++) {
          for (int rx = uminX; rx <= umaxX; rx++) {
            boolean oldState = curr.baseSolidMap[coordToIndex(rx, ry)];
            boolean newState = calcErosionAt(prev.baseSolidMap, rx, ry);
            if (oldState != newState) {
              curr.baseSolidMap[coordToIndex(rx, ry)] = newState;
              changed = true;
            }
          }
        }
        if (changed) {
          refreshClearance(curr, uminX, uminY, umaxX, umaxY);
        }
      }

      markNavDirty(minX[0], minY[0], maxX[0], maxY[0]);
    } finally {
      updateLock.writeLock().unlock();
    }
  }

  // ==================== HPA*：脏标记与 chunk 抽象层 ====================

  /**
   * 导航图变化后按波及范围标记脏 chunk（含泛洪/腐蚀外扩）。惰性重建：
   * findPath 触到哪个 chunk 才重算哪个，放置建筑的代价被摊到后续查询里。
   */
  private static void markNavDirty(int minX, int minY, int maxX, int maxY) {
    if (layers == null) return;
    int cMinX = Math.max(0, minX - NAV_DIRTY_REACH) / CHUNK;
    int cMinY = Math.max(0, minY - NAV_DIRTY_REACH) / CHUNK;
    int cMaxX = Math.min(chunksX - 1, (maxX + NAV_DIRTY_REACH) / CHUNK);
    int cMaxY = Math.min(chunksY - 1, (maxY + NAV_DIRTY_REACH) / CHUNK);
    for (NavLayer layer : layers) {
      for (int cy = cMinY; cy <= cMaxY; cy++) {
        for (int cx = cMinX; cx <= cMaxX; cx++) {
          layer.chunkNav[chunkIdx(cx, cy)].dirty = true;
        }
      }
    }
  }

  private static int chunkIdx(int cx, int cy) {
    return cy * chunksX + cx;
  }

  private static int chunkOfTile(int x, int y) {
    int cx = Math.min(chunksX - 1, x / CHUNK);
    int cy = Math.min(chunksY - 1, y / CHUNK);
    return cy * chunksX + cx;
  }

  private static int[] chunkBounds(int cx, int cy) {
    return new int[] {
      cx * CHUNK, cy * CHUNK,
      Math.min(W, (cx + 1) * CHUNK) - 1,
      Math.min(H, (cy + 1) * CHUNK) - 1
    };
  }

  /**
   * chunk 的门口/边缓存脏了就重建：扫四条边界找门口，再门口两两预计算 chunk 内路径。
   *
   * <p>重建出全新 ChunkNav 后原子换槽——并发读者手里持有的旧引用保持有效，不会被原地清掉。
   * 多路读者同时触雷由 {@link #rebuildLock} 串行化，后到者见到已换新直接返回。
   */
  private static ChunkNav ensureClean(NavLayer layer, int cIdx) {
    ChunkNav nav = layer.chunkNav[cIdx];
    if (!nav.dirty) return nav;
    synchronized (rebuildLock) {
      nav = layer.chunkNav[cIdx];
      if (!nav.dirty) return nav;

      ChunkNav fresh = new ChunkNav(cIdx);
      int cx = cIdx % chunksX;
      int cy = cIdx / chunksX;
      collectBorder(layer, cx, cy, 1, 0, fresh);
      collectBorder(layer, cx, cy, -1, 0, fresh);
      collectBorder(layer, cx, cy, 0, 1, fresh);
      collectBorder(layer, cx, cy, 0, -1, fresh);

      // 门口两两连边（unitSize=0 搜索最宽通道，minClear 注解交给查询按体积过滤）
      int[] bounds = chunkBounds(cx, cy);
      for (int i = 0; i < fresh.crossings.size; i++) {
        int[] a = fresh.crossings.get(i);
        for (int j = i + 1; j < fresh.crossings.size; j++) {
          int[] b = fresh.crossings.get(j);
          Ar<Point2> p = findPathDirect(layer, a[1], a[2], b[1], b[2], 0, bounds);
          if (p == null || p.size < 2) continue;
          AbstractEdge edge = new AbstractEdge();
          edge.from = a[0];
          edge.to = b[0];
          edge.cost = pathCost(p);
          edge.minClear = minClearance(layer, p);
          edge.path = p;
          fresh.edges.add(edge);
        }
      }
      fresh.dirty = false;
      layer.chunkNav[cIdx] = fresh;
      return fresh;
    }
  }

  /**
   * 扫描 chunk (cx,cy) 与 (cx+dx,cy+dy) 的公共边界：
   * 每段连续可通行取中点一个门口（登记进 entrances，并记下本 chunk 侧的入口瓦片）。
   * 段数超过 2 时只保留首尾两段（钉死碎墙图的边数量上限，首尾门口仍覆盖两侧绕行）。
   */
  private static void collectBorder(NavLayer layer, int cx, int cy, int dx, int dy, ChunkNav nav) {
    int nx = cx + dx, ny = cy + dy;
    if (nx < 0 || ny < 0 || nx >= chunksX || ny >= chunksY) return;

    if (dx != 0) {
      // 垂直边界：A 侧 = 左 chunk，B 侧 = 右 chunk
      int leftCx = Math.min(cx, nx);
      int ax = Math.min(W - 1, leftCx * CHUNK + CHUNK - 1);
      int bx = ax + 1;
      if (bx >= W) return;
      int y0 = cy * CHUNK;
      int y1 = Math.min(H, y0 + CHUNK) - 1;

      Ar<Integer> mids = new Ar<>(); // 每段连续可通行的中点
      int runStart = -1;
      for (int y = y0; y <= y1 + 1; y++) {
        boolean ok = y <= y1 && isPassable(layer, ax, y, 0) && isPassable(layer, bx, y, 0);
        if (ok && runStart < 0) runStart = y;
        if (!ok && runStart >= 0) {
          mids.add((runStart + y - 1) / 2);
          runStart = -1;
        }
      }

      for (int r = 0; r < mids.size; r++) {
        if (mids.size > 2 && r > 0 && r < mids.size - 1) continue; // 密度上限：只留首尾段
        int mid = mids.get(r);
        Entrance e = new Entrance();
        e.ax = ax;
        e.ay = mid;
        e.bx = bx;
        e.by = mid;
        int key = coordToIndex(e.ax, e.ay);
        layer.entrances.put(key, e);
        // 本 chunk 侧入口：C 在左 → a 侧；C 在右 → b 侧
        nav.crossings.add(cx == leftCx ? new int[] {key, e.ax, e.ay} : new int[] {key, e.bx, e.by});
      }
    } else {
      // 水平边界：A 侧 = 上 chunk，B 侧 = 下 chunk
      int topCy = Math.min(cy, ny);
      int ay = Math.min(H - 1, topCy * CHUNK + CHUNK - 1);
      int by = ay + 1;
      if (by >= H) return;
      int x0 = cx * CHUNK;
      int x1 = Math.min(W, x0 + CHUNK) - 1;

      Ar<Integer> mids = new Ar<>();
      int runStart = -1;
      for (int x = x0; x <= x1 + 1; x++) {
        boolean ok = x <= x1 && isPassable(layer, x, ay, 0) && isPassable(layer, x, by, 0);
        if (ok && runStart < 0) runStart = x;
        if (!ok && runStart >= 0) {
          mids.add((runStart + x - 1) / 2);
          runStart = -1;
        }
      }

      for (int r = 0; r < mids.size; r++) {
        if (mids.size > 2 && r > 0 && r < mids.size - 1) continue;
        int mid = mids.get(r);
        Entrance e = new Entrance();
        e.ax = mid;
        e.ay = ay;
        e.bx = mid;
        e.by = by;
        int key = coordToIndex(e.ax, e.ay);
        layer.entrances.put(key, e);
        nav.crossings.add(cy == topCy ? new int[] {key, e.ax, e.ay} : new int[] {key, e.bx, e.by});
      }
    }
  }

  private static int minClearance(NavLayer layer, Ar<Point2> path) {
    int min = MAX_DIST_VAL;
    for (int i = 0; i < path.size; i++) {
      Point2 p = path.get(i);
      min = Math.min(min, layer.clearanceMap[coordToIndex(p.x, p.y)]);
    }
    return min;
  }

  /** 路径代价 = 相邻瓦片 octile 距离之和（与 JPS 的 g 口径一致）。 */
  private static float pathCost(Ar<Point2> path) {
    float cost = 0f;
    for (int i = 1; i < path.size; i++) {
      Point2 a = path.get(i - 1);
      Point2 b = path.get(i);
      cost += dist((int) a.x, (int) a.y, (int) b.x, (int) b.y);
    }
    return cost;
  }

  // ==================== 距离场 ====================

  /**
   * 刷新距离场：小图全量重算（精确）；大图在变更范围外扩 {@link #UPDATE_RANGE} 内有界泛洪（近似）。
   */
  private static void refreshClearance(NavLayer layer, int minX, int minY, int maxX, int maxY) {
    if (W * H <= FULL_RECALC_MAX_TILES) {
      calcClearanceFull(layer);
    } else {
      updateRegion(layer, minX, minY, maxX, maxY);
    }
  }

  /**
   * 局部区域重算距离场 (Bounded BFS)。
   *
   * <p>已知近似：边界格子保留旧值当种子，放墙后距离可能被低估（过度封锁）、
   * 拆墙后可能被高估（保守绕路）。大图可接受；小图走 {@link #refreshClearance} 的全量重算。
   */
  private static void updateRegion(NavLayer layer, int minX, int minY, int maxX, int maxY) {
    int uMinX = Math.max(0, minX - UPDATE_RANGE);
    int uMaxX = Math.min(W - 1, maxX + UPDATE_RANGE);
    int uMinY = Math.max(0, minY - UPDATE_RANGE);
    int uMaxY = Math.min(H - 1, maxY + UPDATE_RANGE);

    IntQueue queue = floodQueue;
    queue.clear();

    for (int y = uMinY; y <= uMaxY; y++) {
      for (int x = uMinX; x <= uMaxX; x++) {
        int idx = coordToIndex(x, y);

        if (layer.baseSolidMap[idx]) {
          layer.clearanceMap[idx] = 0;
          queue.addLast(idx);
        } else {
          boolean isBorder = (x == uMinX || x == uMaxX || y == uMinY || y == uMaxY);
          if (isBorder) {
            if (layer.clearanceMap[idx] < MAX_DIST_VAL) {
              queue.addLast(idx);
            }
          } else {
            layer.clearanceMap[idx] = MAX_DIST_VAL;
          }
        }
      }
    }

    while (!queue.isEmpty()) {
      int curr = queue.removeFirst();
      int cVal = layer.clearanceMap[curr];

      if (cVal >= UPDATE_RANGE + 5) continue;

      int cx = curr % W;
      int cy = curr / W;

      if (cx > uMinX) checkAndPropagate(layer, curr - 1, cVal, queue);
      if (cx < uMaxX) checkAndPropagate(layer, curr + 1, cVal, queue);
      if (cy > uMinY) checkAndPropagate(layer, curr - W, cVal, queue);
      if (cy < uMaxY) checkAndPropagate(layer, curr + W, cVal, queue);
    }
  }

  /** 写锁内单写者，泛洪队列复用。 */
  private static final IntQueue floodQueue = new IntQueue();

  private static void checkAndPropagate(
      NavLayer layer, int neighborIdx, int currentVal, IntQueue queue) {
    if (layer.clearanceMap[neighborIdx] > currentVal + 1) {
      layer.clearanceMap[neighborIdx] = currentVal + 1;
      queue.addLast(neighborIdx);
    }
  }

  /** 计算单点的腐蚀状态 */
  private static boolean calcErosionAt(boolean[] srcMap, int x, int y) {
    int idx = coordToIndex(x, y);
    if (!srcMap[idx]) return false;

    if (isValid(x + 1, y) && !srcMap[coordToIndex(x + 1, y)]) return false;
    if (isValid(x - 1, y) && !srcMap[coordToIndex(x - 1, y)]) return false;
    if (isValid(x, y + 1) && !srcMap[coordToIndex(x, y + 1)]) return false;
    if (isValid(x, y - 1) && !srcMap[coordToIndex(x, y - 1)]) return false;

    return true;
  }

  private static void erodeMapFull(boolean[] src, boolean[] dst) {
    for (int i = 0; i < W * H; i++) {
      dst[i] = calcErosionAt(src, i % W, i / W);
    }
  }

  private static void calcClearanceFull(NavLayer layer) {
    for (int i = 0; i < W * H; i++)
      layer.clearanceMap[i] = layer.baseSolidMap[i] ? 0 : MAX_DIST_VAL;

    for (int y = 0; y < H; y++) {
      for (int x = 0; x < W; x++) {
        if (layer.baseSolidMap[coordToIndex(x, y)]) continue;
        int v = layer.clearanceMap[coordToIndex(x, y)];
        if (isValid(x - 1, y)) v = Math.min(v, layer.clearanceMap[coordToIndex(x - 1, y)] + 1);
        if (isValid(x, y - 1)) v = Math.min(v, layer.clearanceMap[coordToIndex(x, y - 1)] + 1);
        layer.clearanceMap[coordToIndex(x, y)] = v;
      }
    }
    for (int y = H - 1; y >= 0; y--) {
      for (int x = W - 1; x >= 0; x--) {
        if (layer.baseSolidMap[coordToIndex(x, y)]) continue;
        int v = layer.clearanceMap[coordToIndex(x, y)];
        if (isValid(x + 1, y)) v = Math.min(v, layer.clearanceMap[coordToIndex(x + 1, y)] + 1);
        if (isValid(x, y + 1)) v = Math.min(v, layer.clearanceMap[coordToIndex(x, y + 1)] + 1);
        layer.clearanceMap[coordToIndex(x, y)] = v;
      }
    }
  }

  public static boolean isValid(int x, int y) {
    return x >= 0 && x < W && y >= 0 && y < H;
  }

  public static int coordToIndex(int x, int y) {
    return y * W + x;
  }

  // 调试用 Getter
  public static boolean[] getDebugSolidMap() {
    return layers != null ? layers[0].baseSolidMap : null;
  }

  public static int[] getDebugClearanceMap() {
    return layers != null ? layers[0].clearanceMap : null;
  }

  // ==================== 查询（读锁）====================

  /**
   * 获取路径（HPA*）：
   * 同 chunk 直接局部 JPS；跨 chunk 先把起点/终点连到本 chunk 的边界门口，
   * 再在门口抽象图上跑 A*（边缓存的 chunk 内路径按 minClear 体积注解过滤），拼接后平滑。
   *
   * @param unitSize 单位半径 (0=1x1, 1=3x3...)
   * @param capability 跨越能力 (0=普通, 1=机甲...)
   */
  public static Ar<Point2> findPath(int sx, int sy, int tx, int ty, int unitSize, int capability) {
    updateLock.readLock().lock();
    try {
      capability = Mathf.clamp(capability, 0, MAX_CAPABILITY);
      NavLayer layer = layers[capability];

      if (!isPassable(layer, tx, ty, unitSize)) return null;

      int cs = chunkOfTile(sx, sy);
      int cg = chunkOfTile(tx, ty);

      // 同 chunk：直接局部 JPS
      if (cs == cg) {
        return findPathDirect(layer, sx, sy, tx, ty, unitSize, null);
      }

      // 起点/终点各自连到本 chunk 的边界门口
      Ar<Conn> startConns = connectFrom(layer, sx, sy, cs, unitSize);
      Ar<Conn> goalConns = connectFrom(layer, tx, ty, cg, unitSize);
      if (startConns.size == 0 || goalConns.size == 0) return null;

      // --- 门口抽象图 A*（多源：起点连接全部入队）---
      IntMap<Float> gScore = new IntMap<>();
      PQueue<ANode> open = new PQueue<>();
      for (int i = 0; i < startConns.size; i++) {
        Conn c = startConns.get(i);
        Entrance e = layer.entrances.get(c.key);
        ANode n = new ANode();
        n.key = c.key;
        n.g = c.cost;
        n.f = c.cost + dist(e.ax, e.ay, tx, ty);
        n.start = c;
        n.viaChunk = cs;
        gScore.put(c.key, c.cost);
        open.add(n);
      }

      float best = Float.MAX_VALUE;
      ANode bestNode = null;
      Conn bestGoal = null;

      while (!open.empty()) {
        ANode cur = open.poll();
        if (cur.g >= best) continue;

        // 到达终点 chunk 的门口：整链代价 = 抽象 g + 跨界(如需) + 门口→终点连接
        Conn gc = findConn(goalConns, cur.key);
        if (gc != null) {
          float cross = cur.viaChunk == cg ? 0f : 1f;
          float total = cur.g + cross + gc.cost;
          if (total < best) {
            best = total;
            bestNode = cur;
            bestGoal = gc;
          }
        }

        Entrance e = layer.entrances.get(cur.key);
        if (e == null) continue;
        // 门口两侧 chunk 的边缓存（惰性重建）；换侧通行记 1 步跨界代价
        int chunkA = chunkOfTile(e.ax, e.ay);
        int chunkB = chunkOfTile(e.bx, e.by);
        ChunkNav navA = ensureClean(layer, chunkA);
        ChunkNav navB = ensureClean(layer, chunkB);
        expand(layer, navA, cur, cur.viaChunk == chunkA ? 0f : 1f, gScore, open, best, tx, ty, unitSize);
        expand(layer, navB, cur, cur.viaChunk == chunkB ? 0f : 1f, gScore, open, best, tx, ty, unitSize);
      }

      if (bestNode == null) return null;

      // --- 拼接：起点段 + 抽象边段 + 终点段，最后平滑 ---
      Ar<ANode> chain = new Ar<>();
      for (ANode n = bestNode; n != null; n = n.parent) chain.add(n);
      chain.reverse();

      Ar<Point2> result = new Ar<>();
      result.addAll(chain.first().start.path);
      for (int i = 1; i < chain.size; i++) {
        ANode n = chain.get(i);
        if (!n.reversed) {
          result.addAll(n.edge.path);
        } else {
          for (int j = n.edge.path.size - 1; j >= 0; j--) result.add(n.edge.path.get(j));
        }
      }
      // 终点连接存的是 goal→entry，拼回来要倒序
      for (int j = bestGoal.path.size - 1; j >= 0; j--) result.add(bestGoal.path.get(j));

      return smoothPath(result, layer, unitSize);
    } finally {
      updateLock.readLock().unlock();
    }
  }

  /** 从单位位置连接到所在 chunk 的全部边界门口。 */
  private static Ar<Conn> connectFrom(NavLayer layer, int x, int y, int cIdx, int unitSize) {
    ChunkNav nav = ensureClean(layer, cIdx);
    Ar<Conn> conns = new Ar<>();
    for (int i = 0; i < nav.crossings.size; i++) {
      int[] c = nav.crossings.get(i);
      Ar<Point2> p = findPathDirect(layer, x, y, c[1], c[2], unitSize, null);
      if (p == null) continue;
      Conn conn = new Conn();
      conn.key = c[0];
      conn.cost = pathCost(p);
      conn.path = p;
      conns.add(conn);
    }
    return conns;
  }

  private static Conn findConn(Ar<Conn> conns, int key) {
    for (int i = 0; i < conns.size; i++) {
      if (conns.get(i).key == key) return conns.get(i);
    }
    return null;
  }

  /** 在 chunk 的边缓存上扩展抽象搜索（minClear 体积注解过滤 + best 剪枝）。 */
  private static void expand(
      NavLayer layer,
      ChunkNav nav,
      ANode cur,
      float crossCost,
      IntMap<Float> gScore,
      PQueue<ANode> open,
      float best,
      int tx,
      int ty,
      int unitSize) {
    for (int i = 0; i < nav.edges.size; i++) {
      AbstractEdge edge = nav.edges.get(i);
      if (edge.minClear <= unitSize) continue; // 这条内部通道对当前体积放不下

      int next;
      boolean reversed;
      if (edge.from == cur.key) {
        next = edge.to;
        reversed = false;
      } else if (edge.to == cur.key) {
        next = edge.from;
        reversed = true;
      } else {
        continue;
      }

      float g = cur.g + crossCost + edge.cost;
      if (g >= best) continue;
      Float old = gScore.get(next);
      if (old != null && old <= g) continue;

      Entrance ne = layer.entrances.get(next);
      ANode n = new ANode();
      n.key = next;
      n.g = g;
      n.f = g + dist(ne.ax, ne.ay, tx, ty);
      n.parent = cur;
      n.edge = edge;
      n.reversed = reversed;
      n.viaChunk = nav.chunkIndex; // 跨界代价记账：下一跳换 chunk 时 expand 会补 1 步
      gScore.put(next, g);
      open.add(n);
    }
  }

  // ==================== 局部 JPS（原 findPath 主体）====================

  // 寻路线程局部 scratch：closed/node 代际戳替代每次全量清理，live 列表保证节点全量归还池
  private static class Scratch {
    int gen;
    int[] closedGen, nodeGen;
    Node[] nodeIndex;
    final Ar<Node> live = new Ar<>(false, 256);
  }

  private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
  /** 局部搜索的边界限制（线程局部）。null = 不限。 */
  private static final ThreadLocal<int[]> SEARCH_BOUNDS = new ThreadLocal<>();

  /**
   * 局部 JPS：同 chunk 直连、门口连接、chunk 内边预计算共用。
   * @param bounds 搜索边界 {minX, minY, maxX, maxY}，null = 不限（chunk 内预计算时传 chunk 范围）
   */
  private static Ar<Point2> findPathDirect(
      NavLayer layer, int sx, int sy, int tx, int ty, int unitSize, int[] bounds) {
    Scratch sc = SCRATCH.get();
    sc.gen++;
    if (sc.gen < 0) { // int 溢出兜底（约 21 亿次寻路后才发生）：代际戳清零重来
      sc.gen = 1;
      sc.closedGen = null;
      sc.nodeGen = null;
    }
    int gen = sc.gen;
    if (sc.closedGen == null || sc.closedGen.length < W * H) {
      sc.closedGen = new int[W * H];
      sc.nodeGen = new int[W * H];
      sc.nodeIndex = new Node[W * H];
    }

    int[] prevBounds = SEARCH_BOUNDS.get();
    SEARCH_BOUNDS.set(bounds);
    try {
      PQueue<Node> openList = new PQueue<>();

      Node startNode = Pools.obtain(Node.class, Node::new).set(sx, sy, null, 0, dist(sx, sy, tx, ty));
      sc.live.add(startNode);
      int sIdx = coordToIndex(sx, sy);
      sc.nodeIndex[sIdx] = startNode;
      sc.nodeGen[sIdx] = gen;
      openList.add(startNode);

      while (!openList.empty()) {
        Node current = openList.poll();
        int cIndex = coordToIndex(current.x, current.y);

        if (sc.closedGen[cIndex] == gen) continue;
        if (sc.nodeGen[cIndex] == gen && sc.nodeIndex[cIndex] != current) continue;

        if (current.x == tx && current.y == ty) {
          Ar<Point2> result = smoothPath(reconstructPath(current), layer, unitSize);
          // 归还本局所有节点到对象池（含被更优节点替换掉的那些——不漏还）
          for (int i = 0; i < sc.live.size; i++) {
            Pools.free(sc.live.get(i));
          }
          sc.live.clear();
          return result;
        }

        sc.closedGen[cIndex] = gen;

        identifySuccessors(layer, current, tx, ty, openList, sc, gen, unitSize);
      }

      for (int i = 0; i < sc.live.size; i++) {
        Pools.free(sc.live.get(i));
      }
      sc.live.clear();
      return null;
    } finally {
      SEARCH_BOUNDS.set(prevBounds);
    }
  }

  /** JPS: 识别并添加后继节点 */
  private static void identifySuccessors(
      NavLayer layer,
      Node current,
      int tx,
      int ty,
      PQueue<Node> openList,
      Scratch sc,
      int gen,
      int unitSize) {

    int[] dirs = getPrunedNeighbors(layer, current, unitSize);

    for (int i = 0; i < dirs.length; i += 2) {
      int dx = dirs[i];
      int dy = dirs[i + 1];

      int jp = jump(layer, current.x, current.y, dx, dy, tx, ty, unitSize);

      if (jp != -1) {
        int jx = jp % W;
        int jy = jp / W;
        int index = jp;

        if (sc.closedGen[index] == gen) continue;

        float g = current.g + dist(current.x, current.y, jx, jy);
        Node existingNode = sc.nodeGen[index] == gen ? sc.nodeIndex[index] : null;

        if (existingNode == null || g < existingNode.g) {
          Node newNode = Pools.obtain(Node.class, Node::new).set(jx, jy, current, g, dist(jx, jy, tx, ty));
          sc.live.add(newNode);
          sc.nodeIndex[index] = newNode;
          sc.nodeGen[index] = gen;
          openList.add(newNode);
        }
      }
    }
  }

  /**
   * JPS: 迭代式跳跃检测。
   * 使用 while 循环沿方向扫描，彻底消除递归导致的栈溢出风险。
   * @return 跳点瓦片 index，-1 = 该方向无跳点
   */
  private static int jump(
      NavLayer layer, int startX, int startY, int dx, int dy, int tx, int ty, int unitSize) {

    int cx = startX;
    int cy = startY;

    while (true) {
      int nx = cx + dx;
      int ny = cy + dy;

      // 越界或不可通行 → 该方向无跳点
      if (!isPassable(layer, nx, ny, unitSize)) return -1;
      // 到达终点
      if (nx == tx && ny == ty) return coordToIndex(nx, ny);

      if (dx != 0 && dy != 0) {
        // --- 对角线移动 ---
        // 强制邻居检测
        if ((!isPassable(layer, nx - dx, ny, unitSize) && isPassable(layer, nx - dx, ny + dy, unitSize))
            || (!isPassable(layer, nx, ny - dy, unitSize)
                && isPassable(layer, nx + dx, ny - dy, unitSize))) {
          return coordToIndex(nx, ny);
        }
        // 正交分量检测：用独立的迭代扫描代替递归
        if (scanOrtho(layer, nx, ny, dx, 0, tx, ty, unitSize) != -1
            || scanOrtho(layer, nx, ny, 0, dy, tx, ty, unitSize) != -1) {
          return coordToIndex(nx, ny);
        }
      } else {
        // --- 直线移动 ---
        if (orthoForced(layer, nx, ny, dx, dy, unitSize)) {
          return coordToIndex(nx, ny);
        }
      }

      // 继续沿当前方向扫描
      cx = nx;
      cy = ny;
    }
  }

  /**
   * 从 (startX, startY) 沿正交方向 (dx,dy) 迭代扫描，
   * 找到跳点则返回其 index，否则返回 -1。
   * 仅用于对角线跳点检测中的正交分量扫描。
   */
  private static int scanOrtho(
      NavLayer layer, int startX, int startY, int dx, int dy, int tx, int ty, int unitSize) {

    int cx = startX;
    int cy = startY;

    while (true) {
      int nx = cx + dx;
      int ny = cy + dy;

      if (!isPassable(layer, nx, ny, unitSize)) return -1;
      if (nx == tx && ny == ty) return coordToIndex(nx, ny);

      // 仅正交方向的强制邻居检测
      if (orthoForced(layer, nx, ny, dx, dy, unitSize)) {
        return coordToIndex(nx, ny);
      }

      cx = nx;
      cy = ny;
    }
  }

  /** 正交扫描中 (nx,ny) 处是否有强制邻居（jump 直线分支与 scanOrtho 共用，消灭三处重复）。 */
  private static boolean orthoForced(
      NavLayer layer, int nx, int ny, int dx, int dy, int unitSize) {
    if (dx != 0) { // 水平
      return (!isPassable(layer, nx, ny - 1, unitSize) && isPassable(layer, nx + dx, ny - 1, unitSize))
          || (!isPassable(layer, nx, ny + 1, unitSize)
              && isPassable(layer, nx + dx, ny + 1, unitSize));
    }
    // 垂直
    return (!isPassable(layer, nx - 1, ny, unitSize) && isPassable(layer, nx - 1, ny + dy, unitSize))
        || (!isPassable(layer, nx + 1, ny, unitSize)
            && isPassable(layer, nx + 1, ny + dy, unitSize));
  }

  /** JPS: 获取剪枝后的搜索方向 */
  private static int[] getPrunedNeighbors(NavLayer layer, Node node, int unitSize) {
    if (node.parent == null) {
      return new int[] {0, 1, 0, -1, -1, 0, 1, 0, 1, 1, 1, -1, -1, 1, -1, -1};
    }

    int dx = Integer.compare(node.x - node.parent.x, 0);
    int dy = Integer.compare(node.y - node.parent.y, 0);

    if (dx != 0 && dy == 0) { // 水平
      if (isPassable(layer, node.x + dx, node.y, unitSize)) {
        boolean forcedUp =
            !isPassable(layer, node.x, node.y - 1, unitSize)
                && isPassable(layer, node.x + dx, node.y - 1, unitSize);
        boolean forcedDown =
            !isPassable(layer, node.x, node.y + 1, unitSize)
                && isPassable(layer, node.x + dx, node.y + 1, unitSize);

        if (forcedUp && forcedDown) return new int[] {dx, 0, dx, -1, dx, 1};
        if (forcedUp) return new int[] {dx, 0, dx, -1};
        if (forcedDown) return new int[] {dx, 0, dx, 1};
        return new int[] {dx, 0};
      }
    } else if (dx == 0 && dy != 0) { // 垂直
      if (isPassable(layer, node.x, node.y + dy, unitSize)) {
        boolean forcedLeft =
            !isPassable(layer, node.x - 1, node.y, unitSize)
                && isPassable(layer, node.x - 1, node.y + dy, unitSize);
        boolean forcedRight =
            !isPassable(layer, node.x + 1, node.y, unitSize)
                && isPassable(layer, node.x + 1, node.y + dy, unitSize);

        if (forcedLeft && forcedRight) return new int[] {0, dy, -1, dy, 1, dy};
        if (forcedLeft) return new int[] {0, dy, -1, dy};
        if (forcedRight) return new int[] {0, dy, 1, dy};
        return new int[] {0, dy};
      }
    } else { // 对角线
      boolean nextPass = isPassable(layer, node.x + dx, node.y + dy, unitSize);
      boolean hPass = isPassable(layer, node.x + dx, node.y, unitSize);
      boolean vPass = isPassable(layer, node.x, node.y + dy, unitSize);

      if (nextPass && (hPass || vPass)) {
        boolean forcedLeft =
            !isPassable(layer, node.x - dx, node.y, unitSize)
                && isPassable(layer, node.x - dx, node.y + dy, unitSize);
        boolean forcedTop =
            !isPassable(layer, node.x, node.y - dy, unitSize)
                && isPassable(layer, node.x + dx, node.y - dy, unitSize);

        int[] temp = new int[10];
        int c = 0;
        temp[c++] = dx;
        temp[c++] = dy;
        temp[c++] = dx;
        temp[c++] = 0;
        temp[c++] = 0;
        temp[c++] = dy;
        if (forcedLeft) {
          temp[c++] = -dx;
          temp[c++] = dy;
        }
        if (forcedTop) {
          temp[c++] = dx;
          temp[c++] = -dy;
        }

        int[] res = new int[c];
        System.arraycopy(temp, 0, res, 0, c);
        return res;
      }
    }
    return new int[] {};
  }

  // --- 路径平滑与射线检测 ---

  private static Ar<Point2> smoothPath(Ar<Point2> path, NavLayer layer, int unitSize) {
    if (path.size <= 2) return path;

    Ar<Point2> smoothed = new Ar<>();
    smoothed.add(path.get(0));

    int inputIndex = 0;
    while (inputIndex < path.size - 1) {
      int nextIndex = inputIndex + 1;
      for (int i = path.size - 1; i > inputIndex + 1; i--) {
        Point2 start = path.get(inputIndex);
        Point2 end = path.get(i);
        if (lineCast(layer, (int) start.x, (int) start.y, (int) end.x, (int) end.y, unitSize)) {
          nextIndex = i;
          break;
        }
      }
      smoothed.add(path.get(nextIndex));
      inputIndex = nextIndex;
    }
    return smoothed;
  }

  private static boolean lineCast(NavLayer layer, int x0, int y0, int x1, int y1, int unitSize) {
    int dx = Math.abs(x1 - x0);
    int dy = Math.abs(y1 - y0);
    int sx = x0 < x1 ? 1 : -1;
    int sy = y0 < y1 ? 1 : -1;
    int err = dx - dy;
    int cx = x0;
    int cy = y0;

    while (true) {
      if (!isPassable(layer, cx, cy, unitSize)) return false;
      if (cx == x1 && cy == y1) break;
      int e2 = 2 * err;
      if (e2 > -dy) {
        err -= dy;
        cx += sx;
      }
      if (e2 < dx) {
        err += dx;
        cy += sy;
      }
    }
    return true;
  }

  // --- 基础辅助方法 ---

  /**
   * 瓦片 (x,y) 对半径 unitSize 的单位是否可通行：clearance 严格大于半径。
   * sizeMaps 预计算已删除——这一个比较就是当时的全部语义。
   */
  public static boolean isPassable(NavLayer layer, int x, int y, int unitSize) {
    if (!isValid(x, y)) return false;
    // 局部搜索边界（findPathDirect 设置，线程局部）
    int[] b = SEARCH_BOUNDS.get();
    if (b != null && (x < b[0] || x > b[2] || y < b[1] || y > b[3])) return false;
    return layer.clearanceMap[coordToIndex(x, y)] > unitSize;
  }

  /**
   * octile 距离：8 方向网格移动的真实长度（对角 √2）。
   * g 与 h 同口径，启发有方向性，搜索剪枝更强、路径更直。
   */
  private static float dist(int x1, int y1, int x2, int y2) {
    int dx = Math.abs(x1 - x2);
    int dy = Math.abs(y1 - y2);
    return Math.max(dx, dy) + DIAG_EXTRA * Math.min(dx, dy);
  }

  private static Ar<Point2> reconstructPath(Node current) {
    Ar<Point2> p = new Ar<>();
    while (current != null) {
      p.add(new Point2(current.x, current.y));
      current = current.parent;
    }
    p.reverse();
    return p;
  }

  // A* 节点类 (对象池复用，减少 GC)
  private static class Node implements Comparable<Node> {
    int x, y;
    Node parent;
    float g, h;

    public Node() {}

    /** 从池中取出后设置字段，链式调用 */
    public Node set(int x, int y, Node parent, float g, float h) {
      this.x = x;
      this.y = y;
      this.parent = parent;
      this.g = g;
      this.h = h;
      return this;
    }

    /** 归还池时重置 */
    public void reset() {
      x = 0;
      y = 0;
      parent = null;
      g = 0;
      h = 0;
    }

    @Override
    public int compareTo(Node o) {
      return Float.compare(g + h, o.g + o.h);
    }
  }
}
