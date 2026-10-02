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

public class RouteData {

  // 预计算的最大单位半径 (0, 1, 2, 3, 4)
  public static final int MAX_PRECALC_RADIUS = 4;
  // 最大支持的跨越能力等级
  public static final int MAX_CAPABILITY = 2;

  // 局部更新时，重算距离场的最大范围
  private static final int UPDATE_RANGE = 24;
  private static final int MAX_DIST_VAL = 9999;

  // --- HPA*：chunk 抽象层 ---
  /** chunk 边长（格）。 */
  public static final int CHUNK = 10;
  /** 导航变更对寻路的波及半径（有界泛洪范围 + 腐蚀级联），脏 chunk 标记用。 */
  private static final int NAV_DIRTY_REACH = UPDATE_RANGE + MAX_CAPABILITY + 2;

  private static int W, H;
  private static int chunksX, chunksY;
  public static NavLayer[] layers;

  // 用于增量更新的锁
  public static final Object updateLock = new Object();

  private RouteData() {}

  /** 内部类：导航层数据 */
  public static class NavLayer {
    public boolean[] baseSolidMap; // 基础障碍物 (是否是墙)
    public int[] clearanceMap; // 距离场
    public boolean[][] sizeMaps; // [Radius][Index] 体积阻挡缓存

    // --- HPA* 抽象层（惰性构建：chunkNav 标脏，findPath 触到才重算）---
    public IntMap<Entrance> entrances; // 门口，key = ay * W + ax（A 侧瓦片）
    public ChunkNav[] chunkNav; // chunksX * chunksY

    public NavLayer(int size) {
      baseSolidMap = new boolean[size];
      clearanceMap = new int[size];
      sizeMaps = new boolean[MAX_PRECALC_RADIUS + 1][size];
      entrances = new IntMap<>();
      chunkNav = new ChunkNav[chunksX * chunksY];
    }
  }

  /** 门口：相邻 chunk 边界两侧的通行瓦片对（A = 左/上 chunk，B = 右/下 chunk）。 */
  private static class Entrance {
    int ax, ay, bx, by;
  }

  /** 抽象边：同一 chunk 内两个门口之间的路径缓存（无向；unitSize=0 搜索 + 最小 clearance 注解）。 */
  private static class AbstractEdge {
    int from, to; // 门口 key
    float cost; // 入口瓦片间路径代价（= 格步数，与 JPS 的曼哈顿 g 一致）
    int minClear; // 路径上的最小 clearance（体积注解：可用 iff minClear > unitSize）
    Ar<Point2> path; // 入口瓦片间的格路径
  }

  /** 每个 chunk 的抽象边缓存 + 边界门口的本侧入口。 */
  private static class ChunkNav {
    final Ar<AbstractEdge> edges = new Ar<>();
    final Ar<int[]> crossings = new Ar<>(); // {nodeKey, entryX, entryY}
    boolean dirty = true;
  }

  /** 起点/终点到本 chunk 边界门口的连接。 */
  private static class Conn {
    int key;
    float cost;
    Ar<Point2> path; // 从单位位置到入口瓦片
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

  /** 局部搜索的边界限制（updateLock 内单线程，静态字段当参数传）。null = 不限。 */
  private static int[] searchBounds;

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

    // 3. 计算距离场和体积图
    for (int cap = 0; cap <= MAX_CAPABILITY; cap++) {
      calcClearanceFull(layers[cap]);
      updateSizeMapsFull(layers[cap]);
    }
  }

  /** 动态更新某个方块的状态 自动处理级联腐蚀和局部距离场重算 */
  public static void updateBlock(int x, int y, boolean isSolid) {
    synchronized (updateLock) {
      if (!isValid(x, y)) return;

      // 1. 更新 Layer 0 基础数据
      NavLayer l0 = layers[0];
      int index = coordToIndex(x, y);
      if (l0.baseSolidMap[index] == isSolid) return; // 状态没变
      l0.baseSolidMap[index] = isSolid;

      // 更新 Layer 0 的局部区域
      updateRegion(l0, x, y, x, y);

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
          updateRegion(curr, minX, minY, maxX, maxY);
        }
      }

      markNavDirty(x, y, x, y);
    }
  }

  /**
   * 放置建筑后更新：获取 (bx,by) 处的建筑，将其占据的所有坐标标记为空（通行）。
   * 用于建筑被移除或放置前清空占位。
   */
  public static void updateBlock(int bx, int by) {
    synchronized (updateLock) {
      Building build = WorldData.world.getBuilding(bx, by);
      if (build == null) return;

      // 遍历建筑占据的所有坐标，全部标记为空
      build.getOccupiedCoords(
          (tx, ty) -> {
            if (isValid(tx, ty)) {
              NavLayer l0 = layers[0];
              int idx = coordToIndex(tx, ty);
              if (l0.baseSolidMap[idx]) {
                l0.baseSolidMap[idx] = false;
                updateRegion(l0, tx, ty, tx, ty);
              }
            }
          });

      // 级联更新腐蚀层：以建筑包围盒为范围
      Block blk = build.type != null ? build.type.block : null;
      int s = blk != null ? blk.size : 1;
      int minX = Math.max(0, bx);
      int maxX = Math.min(W - 1, bx + s - 1);
      int minY = Math.max(0, by);
      int maxY = Math.min(H - 1, by + s - 1);

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
          updateRegion(curr, uminX, uminY, umaxX, umaxY);
        }
      }

      markNavDirty(minX, minY, maxX, maxY);
    }
  }

  /**
   * 放置建筑方块：将 Block 在 (x,y) 处占据的所有坐标标记为实心。
   */
  public static void updateBlock(int x, int y, Block block) {
    synchronized (updateLock) {
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
      updateRegion(l0, minX[0], minY[0], maxX[0], maxY[0]);

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
          updateRegion(curr, uminX, uminY, umaxX, umaxY);
        }
      }

      markNavDirty(minX[0], minY[0], maxX[0], maxY[0]);
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

  /** chunk 的门口/边缓存脏了就重建：扫四条边界找门口，再门口两两预计算 chunk 内路径。 */
  private static void ensureClean(NavLayer layer, int cIdx) {
    ChunkNav nav = layer.chunkNav[cIdx];
    if (!nav.dirty) return;
    nav.dirty = false;
    nav.edges.clear();
    nav.crossings.clear();

    int cx = cIdx % chunksX;
    int cy = cIdx / chunksX;
    collectBorder(layer, cx, cy, 1, 0, nav);
    collectBorder(layer, cx, cy, -1, 0, nav);
    collectBorder(layer, cx, cy, 0, 1, nav);
    collectBorder(layer, cx, cy, 0, -1, nav);

    // 门口两两连边（unitSize=0 搜索最宽通道，minClear 注解交给查询按体积过滤）
    int[] bounds = chunkBounds(cx, cy);
    for (int i = 0; i < nav.crossings.size; i++) {
      int[] a = nav.crossings.get(i);
      for (int j = i + 1; j < nav.crossings.size; j++) {
        int[] b = nav.crossings.get(j);
        Ar<Point2> p = findPathDirect(layer, a[1], a[2], b[1], b[2], 0, bounds);
        if (p == null || p.size < 2) continue;
        AbstractEdge edge = new AbstractEdge();
        edge.from = a[0];
        edge.to = b[0];
        edge.cost = p.size - 1;
        edge.minClear = minClearance(layer, p);
        edge.path = p;
        nav.edges.add(edge);
      }
    }
  }

  /**
   * 扫描 chunk (cx,cy) 与 (cx+dx,cy+dy) 的公共边界：
   * 每段连续可通行取中点一个门口（登记进 entrances，并记下本 chunk 侧的入口瓦片）。
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
      int runStart = -1;
      for (int y = y0; y <= y1 + 1; y++) {
        boolean ok = y <= y1 && isPassable(layer, ax, y, 0) && isPassable(layer, bx, y, 0);
        if (ok && runStart < 0) runStart = y;
        if (!ok && runStart >= 0) {
          int mid = (runStart + y - 1) / 2;
          Entrance e = new Entrance();
          e.ax = ax;
          e.ay = mid;
          e.bx = bx;
          e.by = mid;
          int key = coordToIndex(e.ax, e.ay);
          layer.entrances.put(key, e);
          nav.crossings.add(cx == leftCx ? new int[] {key, e.ax, e.ay} : new int[] {key, e.bx, e.by});
          runStart = -1;
        }
      }
    } else {
      // 水平边界：A 侧 = 上 chunk，B 侧 = 下 chunk
      int topCy = Math.min(cy, ny);
      int ay = Math.min(H - 1, topCy * CHUNK + CHUNK - 1);
      int by = ay + 1;
      if (by >= H) return;
      int x0 = cx * CHUNK;
      int x1 = Math.min(W, x0 + CHUNK) - 1;
      int runStart = -1;
      for (int x = x0; x <= x1 + 1; x++) {
        boolean ok = x <= x1 && isPassable(layer, x, ay, 0) && isPassable(layer, x, by, 0);
        if (ok && runStart < 0) runStart = x;
        if (!ok && runStart >= 0) {
          int mid = (runStart + x - 1) / 2;
          Entrance e = new Entrance();
          e.ax = mid;
          e.ay = ay;
          e.bx = mid;
          e.by = by;
          int key = coordToIndex(e.ax, e.ay);
          layer.entrances.put(key, e);
          nav.crossings.add(cy == topCy ? new int[] {key, e.ax, e.ay} : new int[] {key, e.bx, e.by});
          runStart = -1;
        }
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

  /**
   * 局部区域重算距离场 (Bounded BFS)
   */
  private static void updateRegion(NavLayer layer, int minX, int minY, int maxX, int maxY) {
    int uMinX = Math.max(0, minX - UPDATE_RANGE);
    int uMaxX = Math.min(W - 1, maxX + UPDATE_RANGE);
    int uMinY = Math.max(0, minY - UPDATE_RANGE);
    int uMaxY = Math.min(H - 1, maxY + UPDATE_RANGE);

    IntQueue queue = new IntQueue();

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

    for (int r = 0; r <= MAX_PRECALC_RADIUS; r++) {
      for (int y = uMinY; y <= uMaxY; y++) {
        for (int x = uMinX; x <= uMaxX; x++) {
          int i = coordToIndex(x, y);
          layer.sizeMaps[r][i] = layer.clearanceMap[i] <= r;
        }
      }
    }
  }

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

  private static void updateSizeMapsFull(NavLayer layer) {
    for (int r = 0; r <= MAX_PRECALC_RADIUS; r++) {
      for (int i = 0; i < W * H; i++) {
        layer.sizeMaps[r][i] = layer.clearanceMap[i] <= r;
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

  /**
   * 获取路径（HPA*）：
   * 同 chunk 直接局部 JPS；跨 chunk 先把起点/终点连到本 chunk 的边界门口，
   * 再在门口抽象图上跑 A*（边缓存的 chunk 内路径按 minClear 体积注解过滤），拼接后平滑。
   *
   * @param unitSize 单位半径 (0=1x1, 1=3x3...)
   * @param capability 跨越能力 (0=普通, 1=机甲...)
   */
  public static Ar<Point2> findPath(int sx, int sy, int tx, int ty, int unitSize, int capability) {
    synchronized (updateLock) {
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

        // 到达终点 chunk 的门口：整链代价 = 抽象 g + 门口→终点连接
        Conn gc = findConn(goalConns, cur.key);
        if (gc != null) {
          // 终点连接挂在终点 chunk 侧；从另一侧到达门口要补 1 步跨界
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
        // 门口两侧 chunk 的边缓存（惰性重建）；换侧通行要记 1 步跨界代价
        int chunkA = chunkOfTile(e.ax, e.ay);
        int chunkB = chunkOfTile(e.bx, e.by);
        ensureClean(layer, chunkA);
        ensureClean(layer, chunkB);
        expand(layer, chunkA, cur, cur.viaChunk == chunkA ? 0f : 1f, gScore, open, best, tx, ty, unitSize);
        expand(layer, chunkB, cur, cur.viaChunk == chunkB ? 0f : 1f, gScore, open, best, tx, ty, unitSize);
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
    }
  }

  /** 从单位位置连接到所在 chunk 的全部边界门口（任意线程约束：调用方持 updateLock）。 */
  private static Ar<Conn> connectFrom(NavLayer layer, int x, int y, int cIdx, int unitSize) {
    ensureClean(layer, cIdx);
    ChunkNav nav = layer.chunkNav[cIdx];
    Ar<Conn> conns = new Ar<>();
    for (int i = 0; i < nav.crossings.size; i++) {
      int[] c = nav.crossings.get(i);
      Ar<Point2> p = findPathDirect(layer, x, y, c[1], c[2], unitSize, null);
      if (p == null) continue;
      Conn conn = new Conn();
      conn.key = c[0];
      conn.cost = p.size - 1;
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
      int cIdx,
      ANode cur,
      float crossCost,
      IntMap<Float> gScore,
      PQueue<ANode> open,
      float best,
      int tx,
      int ty,
      int unitSize) {
    ChunkNav nav = layer.chunkNav[cIdx];
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
      n.viaChunk = cIdx;
      gScore.put(next, g);
      open.add(n);
    }
  }

  /**
   * 局部 JPS（原 findPath 主体）：同 chunk 直连、门口连接、chunk 内边预计算共用。
   * @param bounds 搜索边界 {minX, minY, maxX, maxY}，null = 不限（chunk 内预计算时传 chunk 范围）
   */
  private static Ar<Point2> findPathDirect(
      NavLayer layer, int sx, int sy, int tx, int ty, int unitSize, int[] bounds) {
    int[] prevBounds = searchBounds;
    searchBounds = bounds;
    try {
      PQueue<Node> openList = new PQueue<>();
      boolean[] closedMap = new boolean[W * H];
      Node[] nodeIndex = new Node[W * H];

      Node startNode = Pools.obtain(Node.class, Node::new).set(sx, sy, null, 0, dist(sx, sy, tx, ty));
      openList.add(startNode);
      nodeIndex[coordToIndex(sx, sy)] = startNode;

      while (!openList.empty()) {
        Node current = openList.poll();
        int cIndex = coordToIndex(current.x, current.y);

        if (closedMap[cIndex]) continue;
        if (nodeIndex[cIndex] != null && current != nodeIndex[cIndex]) continue;

        if (current.x == tx && current.y == ty) {
          Ar<Point2> result = smoothPath(reconstructPath(current), layer, unitSize);
          // 归还所有节点到对象池
          for (int i = 0; i < nodeIndex.length; i++) {
            if (nodeIndex[i] != null) {
              Pools.free(nodeIndex[i]);
            }
          }
          return result;
        }

        closedMap[cIndex] = true;

        identifySuccessors(layer, current, tx, ty, openList, closedMap, nodeIndex, unitSize);
      }

      // 归还所有节点到对象池
      for (int i = 0; i < nodeIndex.length; i++) {
        if (nodeIndex[i] != null) {
          Pools.free(nodeIndex[i]);
        }
      }
      return null;
    } finally {
      searchBounds = prevBounds;
    }
  }

  /** JPS: 识别并添加后继节点 */
  private static void identifySuccessors(
      NavLayer layer,
      Node current,
      int tx,
      int ty,
      PQueue<Node> openList,
      boolean[] closedMap,
      Node[] nodeIndex,
      int unitSize) {

    int[] dirs = getPrunedNeighbors(layer, current, unitSize);

    for (int i = 0; i < dirs.length; i += 2) {
      int dx = dirs[i];
      int dy = dirs[i + 1];

      Point2 jp = jump(layer, current.x, current.y, dx, dy, tx, ty, unitSize);

      if (jp != null) {
        int jx = (int) jp.x;
        int jy = (int) jp.y;
        int index = coordToIndex(jx, jy);

        if (closedMap[index]) continue;

        float g = current.g + dist(current.x, current.y, jx, jy);
        Node existingNode = nodeIndex[index];

        if (existingNode == null || g < existingNode.g) {
          Node newNode = Pools.obtain(Node.class, Node::new).set(jx, jy, current, g, dist(jx, jy, tx, ty));
          nodeIndex[index] = newNode;
          openList.add(newNode);
        }
      }
    }
  }

  /**
   * JPS: 迭代式跳跃检测。
   * 使用 while 循环沿方向扫描，彻底消除递归导致的栈溢出风险。
   */
  private static Point2 jump(
      NavLayer layer, int startX, int startY, int dx, int dy, int tx, int ty, int unitSize) {

    int cx = startX;
    int cy = startY;

    while (true) {
      int nx = cx + dx;
      int ny = cy + dy;

      // 越界或不可通行 → 该方向无跳点
      if (!isPassable(layer, nx, ny, unitSize)) return null;
      // 到达终点
      if (nx == tx && ny == ty) return new Point2(nx, ny);

      if (dx != 0 && dy != 0) {
        // --- 对角线移动 ---
        // 强制邻居检测
        if ((!isPassable(layer, nx - dx, ny, unitSize) && isPassable(layer, nx - dx, ny + dy, unitSize))
            || (!isPassable(layer, nx, ny - dy, unitSize) && isPassable(layer, nx + dx, ny - dy, unitSize))) {
          return new Point2(nx, ny);
        }
        // 正交分量检测：用独立的迭代扫描代替递归
        if (scanOrtho(layer, nx, ny, dx, 0, tx, ty, unitSize) != null
            || scanOrtho(layer, nx, ny, 0, dy, tx, ty, unitSize) != null) {
          return new Point2(nx, ny);
        }
      } else {
        // --- 直线移动 ---
        if (dx != 0) { // 水平
          if ((!isPassable(layer, nx, ny - 1, unitSize) && isPassable(layer, nx + dx, ny - 1, unitSize))
              || (!isPassable(layer, nx, ny + 1, unitSize) && isPassable(layer, nx + dx, ny + 1, unitSize))) {
            return new Point2(nx, ny);
          }
        } else { // 垂直
          if ((!isPassable(layer, nx - 1, ny, unitSize) && isPassable(layer, nx - 1, ny + dy, unitSize))
              || (!isPassable(layer, nx + 1, ny, unitSize) && isPassable(layer, nx + 1, ny + dy, unitSize))) {
            return new Point2(nx, ny);
          }
        }
      }

      // 继续沿当前方向扫描
      cx = nx;
      cy = ny;
    }
  }

  /**
   * 从 (startX, startY) 沿正交方向 (dx,dy) 迭代扫描，
   * 找到跳点则返回，否则返回 null。
   * 仅用于对角线跳点检测中的正交分量扫描。
   */
  private static Point2 scanOrtho(
      NavLayer layer, int startX, int startY, int dx, int dy, int tx, int ty, int unitSize) {

    int cx = startX;
    int cy = startY;

    while (true) {
      int nx = cx + dx;
      int ny = cy + dy;

      if (!isPassable(layer, nx, ny, unitSize)) return null;
      if (nx == tx && ny == ty) return new Point2(nx, ny);

      // 仅正交方向的强制邻居检测
      if (dx != 0) { // 水平
        if ((!isPassable(layer, nx, ny - 1, unitSize) && isPassable(layer, nx + dx, ny - 1, unitSize))
            || (!isPassable(layer, nx, ny + 1, unitSize) && isPassable(layer, nx + dx, ny + 1, unitSize))) {
          return new Point2(nx, ny);
        }
      } else { // 垂直
        if ((!isPassable(layer, nx - 1, ny, unitSize) && isPassable(layer, nx - 1, ny + dy, unitSize))
            || (!isPassable(layer, nx + 1, ny, unitSize) && isPassable(layer, nx + 1, ny + dy, unitSize))) {
          return new Point2(nx, ny);
        }
      }

      cx = nx;
      cy = ny;
    }
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

  public static boolean isPassable(NavLayer layer, int x, int y, int unitSize) {
    if (!isValid(x, y)) return false;
    // 局部搜索边界（findPathDirect 设置，updateLock 内单线程）
    int[] b = searchBounds;
    if (b != null && (x < b[0] || x > b[2] || y < b[1] || y > b[3])) return false;
    int index = coordToIndex(x, y);
    if (unitSize <= MAX_PRECALC_RADIUS) {
      return !layer.sizeMaps[unitSize][index];
    } else {
      return layer.clearanceMap[index] > unitSize;
    }
  }

  private static float dist(int x1, int y1, int x2, int y2) {
    return Math.abs(x1 - x2) + Math.abs(y1 - y2);
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
