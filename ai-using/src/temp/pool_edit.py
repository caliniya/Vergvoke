import io
p = 'core/src/caliniya/vergvoke/game/data/RouteData.java'
s = io.open(p, encoding='utf-8').read()

# 1) imports：IntFloatMap
s = s.replace('''import arc.struct.IntMap;
import arc.struct.IntQueue;''',
'''import arc.struct.IntFloatMap;
import arc.struct.IntMap;
import arc.struct.IntQueue;''')

# 2) Scratch：加队列/gScore/ANode 池
s = s.replace('''  // 寻路线程局部 scratch：closed/node 代际戳替代每次全量清理，live 列表保证节点全量归还池
  private static class Scratch {
    int gen;
    int[] closedGen, nodeGen;
    Node[] nodeIndex;
    final Ar<Node> live = new Ar<>(false, 256);
  }''',
'''  // 寻路线程局部 scratch：closed/node 代际戳替代每次全量清理，live 列表保证节点全量归还池；
  // 队列 / gScore / ANode 也一并复用（HPA* 查询路径的分配热点）
  private static class Scratch {
    int gen;
    int[] closedGen, nodeGen;
    Node[] nodeIndex;
    final Ar<Node> live = new Ar<>(false, 256);
    final PQueue<Node> openList = new PQueue<>();
    final PQueue<ANode> openANodes = new PQueue<>();
    final IntFloatMap gScore = new IntFloatMap();
    final Ar<ANode> aLive = new Ar<>(false, 64);
  }''')

# 3) ANode 注释
s = s.replace('''  /** 门口抽象图 A* 节点。 */
  private static class ANode implements Comparable<ANode> {''',
'''  /** 门口抽象图 A* 节点（池化：obtain 时全量赋值，无残留语义）。 */
  private static class ANode implements Comparable<ANode> {''')

# 4) findPathDirect：复用队列
s = s.replace('''    int[] prevBounds = SEARCH_BOUNDS.get();
    SEARCH_BOUNDS.set(bounds);
    try {
      PQueue<Node> openList = new PQueue<>();

      Node startNode = Pools.obtain(Node.class, Node::new).set(sx, sy, null, 0, dist(sx, sy, tx, ty));''',
'''    int[] prevBounds = SEARCH_BOUNDS.get();
    SEARCH_BOUNDS.set(bounds);
    try {
      PQueue<Node> openList = sc.openList;
      openList.clear();

      Node startNode = Pools.obtain(Node.class, Node::new).set(sx, sy, null, 0, dist(sx, sy, tx, ty));''')

# 5) 编排器
s = s.replace('''      // --- 门口抽象图 A*（多源：起点连接全部入队）---
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
      }''',
'''      // --- 门口抽象图 A*（多源：起点连接全部入队）---
      Scratch sc = SCRATCH.get();
      IntFloatMap gScore = sc.gScore;
      gScore.clear();
      PQueue<ANode> open = sc.openANodes;
      open.clear();
      for (int i = 0; i < startConns.size; i++) {
        Conn c = startConns.get(i);
        Entrance e = layer.entrances.get(c.key);
        ANode n = obtainANode(sc, c.key, c.cost, c.cost + dist(e.ax, e.ay, tx, ty), null, null, false, c, cs);
        gScore.put(c.key, c.cost);
        open.add(n);
      }''')

s = s.replace('''        ChunkNav navA = ensureClean(layer, chunkA);
        ChunkNav navB = ensureClean(layer, chunkB);
        expand(layer, navA, cur, cur.viaChunk == chunkA ? 0f : 1f, gScore, open, best, tx, ty, unitSize);
        expand(layer, navB, cur, cur.viaChunk == chunkB ? 0f : 1f, gScore, open, best, tx, ty, unitSize);
      }

      if (bestNode == null) return null;''',
'''        ChunkNav navA = ensureClean(layer, chunkA);
        ChunkNav navB = ensureClean(layer, chunkB);
        expand(layer, sc, navA, cur, cur.viaChunk == chunkA ? 0f : 1f, gScore, open, best, tx, ty, unitSize);
        expand(layer, sc, navB, cur, cur.viaChunk == chunkB ? 0f : 1f, gScore, open, best, tx, ty, unitSize);
      }

      if (bestNode == null) {
        freeANodes(sc);
        return null;
      }''')

s = s.replace('''      // 终点连接存的是 goal→entry，拼回来要倒序
      for (int j = bestGoal.path.size - 1; j >= 0; j--) result.add(bestGoal.path.get(j));

      return smoothPath(result, layer, unitSize);
    } finally {
      updateLock.readLock().unlock();
    }
  }''',
'''      // 终点连接存的是 goal→entry，拼回来要倒序
      addSeg(result, bestGoal.path, true);

      Ar<Point2> out = smoothPath(result, layer, unitSize);
      freeANodes(sc); // 拼接完成后抽象节点才可归还（bestNode 链还在 aLive 里）
      return out;
    } finally {
      updateLock.readLock().unlock();
    }
  }

  /** 池化获取抽象节点并全量赋值（所有字段覆盖，池内无残留语义），同时登记进 live 待归还。 */
  private static ANode obtainANode(
      Scratch sc,
      int key,
      float g,
      float f,
      ANode parent,
      AbstractEdge edge,
      boolean reversed,
      Conn start,
      int viaChunk) {
    ANode n = Pools.obtain(ANode.class, ANode::new);
    n.key = key;
    n.g = g;
    n.f = f;
    n.parent = parent;
    n.edge = edge;
    n.reversed = reversed;
    n.start = start;
    n.viaChunk = viaChunk;
    sc.aLive.add(n);
    return n;
  }

  private static void freeANodes(Scratch sc) {
    for (int i = 0; i < sc.aLive.size; i++) {
      Pools.free(sc.aLive.get(i));
    }
    sc.aLive.clear();
  }''')

# 6) expand：签名 + gScore 原生 float + obtainANode
s = s.replace('''  private static void expand(
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
  }''',
'''  private static void expand(
      NavLayer layer,
      Scratch sc,
      ChunkNav nav,
      ANode cur,
      float crossCost,
      IntFloatMap gScore,
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
      if (gScore.get(next, Float.POSITIVE_INFINITY) <= g) continue;

      Entrance ne = layer.entrances.get(next);
      ANode n =
              obtainANode(
                      sc,
                      next,
                      g,
                      g + dist(ne.ax, ne.ay, tx, ty),
                      cur,
                      edge,
                      reversed,
                      null,
                      nav.chunkIndex); // viaChunk：跨界代价记账，下一跳换 chunk 时补 1 步
      gScore.put(next, g);
      open.add(n);
    }
  }''')

# 拼接的终点段也要走 addSeg（前一轮漏了的话这里统一）
s = s.replace('''      for (int j = bestGoal.path.size - 1; j >= 0; j--) result.add(bestGoal.path.get(j));

      return smoothPath(result, layer, unitSize);''',
'''      addSeg(result, bestGoal.path, true);

      Ar<Point2> out = smoothPath(result, layer, unitSize);
      freeANodes(sc);
      return out;''')

assert s.count('obtainANode') >= 4, '编排器替换失败'
assert 'IntFloatMap gScore = sc.gScore' in s, 'gScore 替换失败'
assert 'IntMap<Float>' not in s, '仍有装箱 gScore'
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('all replacements ok')
