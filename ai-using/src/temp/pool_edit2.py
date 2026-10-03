import io
p = 'core/src/caliniya/vergvoke/game/data/RouteData.java'
s = io.open(p, encoding='utf-8').read()

def rep(old, new):
    global s
    assert old in s, '锚点未命中: ' + old[:60]
    s = s.replace(old, new, 1)

# R1 imports
rep('import arc.struct.IntMap;\nimport arc.struct.IntQueue;',
    'import arc.struct.IntFloatMap;\nimport arc.struct.IntMap;\nimport arc.struct.IntQueue;')

# R2 Scratch 字段
rep('''    private static class Scratch {
        int gen;
        int[] closedGen, nodeGen;
        Node[] nodeIndex;
        final Ar<Node> live = new Ar<>(false, 256);''',
'''    private static class Scratch {
        int gen;
        int[] closedGen, nodeGen;
        Node[] nodeIndex;
        final Ar<Node> live = new Ar<>(false, 256);
        final PQueue<Node> openList = new PQueue<>();
        final PQueue<ANode> openANodes = new PQueue<>();
        final IntFloatMap gScore = new IntFloatMap();
        final Ar<ANode> aLive = new Ar<>(false, 64);''')

# R3 findPathDirect 队列复用
rep('            PQueue<Node> openList = new PQueue<>();',
    '            PQueue<Node> openList = sc.openList;\n            openList.clear();')

# R4 编排器：gScore/open/根节点
rep('''            // --- 门口抽象图 A*（多源：起点连接全部入队）---
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
'''            // --- 门口抽象图 A*（多源：起点连接全部入队）---
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

# R5 expand 调用点 + bestNode null 归还
rep('''                ChunkNav navA = ensureClean(layer, chunkA);
                ChunkNav navB = ensureClean(layer, chunkB);
                expand(layer, navA, cur, cur.viaChunk == chunkA ? 0f : 1f, gScore, open, best, tx, ty, unitSize);
                expand(layer, navB, cur, cur.viaChunk == chunkB ? 0f : 1f, gScore, open, best, tx, ty, unitSize);
            }

            if (bestNode == null)
                return null;''',
'''                ChunkNav navA = ensureClean(layer, chunkA);
                ChunkNav navB = ensureClean(layer, chunkB);
                expand(layer, sc, navA, cur, cur.viaChunk == chunkA ? 0f : 1f, gScore, open, best, tx, ty, unitSize);
                expand(layer, sc, navB, cur, cur.viaChunk == chunkB ? 0f : 1f, gScore, open, best, tx, ty, unitSize);
            }

            if (bestNode == null) {
                freeANodes(sc);
                return null;
            }''')

# R6 拼接返回前归还
rep('''            // 终点连接存的是 goal→entry，拼回来要倒序
            addSeg(result, bestGoal.path, true);

            return smoothPath(result, layer, unitSize);''',
'''            // 终点连接存的是 goal→entry，拼回来要倒序
            addSeg(result, bestGoal.path, true);

            Ar<Point2> out = smoothPath(result, layer, unitSize);
            freeANodes(sc); // 拼接完成后抽象节点才可归还（bestNode 链还在 aLive 里）
            return out;''')

# R7 expand 签名
rep('''    private static void expand(
            NavLayer layer,
            ChunkNav nav,
            ANode cur,
            float crossCost,
            IntMap<Float> gScore,''',
'''    private static void expand(
            NavLayer layer,
            Scratch sc,
            ChunkNav nav,
            ANode cur,
            float crossCost,
            IntFloatMap gScore,''')

# R8 装箱判断改原生
rep('''            Float old = gScore.get(next);
            if (old != null && old <= g)
                continue;''',
'''            if (gScore.get(next, Float.POSITIVE_INFINITY) <= g)
                continue;''')

# R9 expand 内 ANode 池化
rep('''            Entrance ne = layer.entrances.get(next);
            ANode n = new ANode();
            n.key = next;
            n.g = g;
            n.f = g + dist(ne.ax, ne.ay, tx, ty);
            n.parent = cur;
            n.edge = edge;
            n.reversed = reversed;
            n.viaChunk = nav.chunkIndex; // 跨界代价记账：下一跳换 chunk 时 expand 会补 1 步
            gScore.put(next, g);
            open.add(n);''',
'''            Entrance ne = layer.entrances.get(next);
            ANode n = obtainANode(sc, next, g, g + dist(ne.ax, ne.ay, tx, ty), cur, edge, reversed, null,
                    nav.chunkIndex); // viaChunk：跨界代价记账，下一跳换 chunk 时补 1 步
            gScore.put(next, g);
            open.add(n);''')

# R10 obtainANode/freeANodes 方法
rep('''    // ==================== 局部 JPS（原 findPath 主体）====================''',
'''    /** 池化获取抽象节点并全量赋值（所有字段覆盖，池内无残留语义），同时登记进 live 待归还。 */
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
    }

    // ==================== 局部 JPS（原 findPath 主体）====================''')

assert 'IntMap<Float>' not in s, '仍有装箱 gScore'
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('all ok')
