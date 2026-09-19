package caliniya.vergvoke.world.stars;

import arc.func.*;
import arc.graphics.Camera;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.io.Reads;
import arc.util.io.Writes;
import caliniya.vergvoke.base.tool.ObjectSet;
import caliniya.vergvoke.base.type.CType;
import caliniya.vergvoke.game.Contents;

// 表示一片星域？
public class StarMap {

    /** 星域内部名（与 campaign 目录中的星域文件夹名一致）。 */
    public String name;

    public ObjectSet<StarRoad> roadSet;
    public ObjectSet<StarNode> nodeSet;

    public QuadTree<StarRoad> tree;
    public QuadTree<StarNode> nodeTree;

    public Rect tempR = new Rect();

    public float w, h;

    public StarMap(float w, float h) {
        roadSet = new ObjectSet<StarRoad>();
        nodeSet = new ObjectSet<>();
        this.w = w;
        this.h = h;
        tree = new QuadTree<>(new Rect(0, 0, w, h));
        nodeTree = new QuadTree<>(new Rect(0, 0, w, h));
    }

    public StarMap(String name, float w, float h) {
        this(w, h);
        this.name = name;
    }

    /** 按节点原始名称查找节点。 */
    public StarNode getNode(String name) {
        for (StarNode n : nodeSet) {
            if (n.name.equals(name))
                return n;
        }
        return null;
    }

    // 连接两点，路径在此过程中自动创建
    public void link(StarNode A, StarNode B) {
        A.add(B);
        B.add(A);
        StarRoad.with(
                A,
                B,
                road -> {
                    roadSet.add(road);
                    tree.insert(road);
                });
    }

    // 向图中添加节点
    public void addNode(StarNode node) {
        // 注册过的节点 id>0（内容 ID）直接用；未注册的临时节点才自动分配
        if (node.id <= 0) {
            node.id = nodeSet.size + 1;
        }
        nodeSet.add(node);
        nodeTree.insert(node);
    }

    public void addNode(StarNode... nodes) {
        for (StarNode n : nodes)
            addNode(n);
    }

    public void get(float x, float y, float w, float h, Cons<StarRoad> out) {
        tree.intersect(x, y, w, h, out);
    }

    public void get(Camera cam, Cons<StarRoad> out) {
        cam.bounds(tempR);
        tree.intersect(tempR, out);
    }

    // 节点查询
    public void getNode(float x, float y, float w, float h, Cons<StarNode> out) {
        nodeTree.intersect(x, y, w, h, out);
    }

    public void getNode(Camera cam, Cons<StarNode> out) {
        cam.bounds(tempR);
        nodeTree.intersect(tempR, out);
    }

    public void draw(Camera c) {
        get(
                c.position.x - (c.width / 2),
                c.position.y - (c.height / 2),
                c.width,
                c.height,
                road -> road.draw());
        getNode(c, node -> node.draw());
    }

    /** 序列化整片星域：尺寸 + 节点数 + 所有节点（含邻接表）。 */
    public void write(Writes w) {
        w.f(this.w);
        w.f(this.h);
        w.i(nodeSet.size);
        for (StarNode n : nodeSet) {
            n.write(w);
        }
    }

    /**
     * 从流中重建一片星域。读档流程： 1. 读尺寸，新建空 StarMap 2. 一遍完整读取所有节点（含邻接 id 表），保证流位置正确 3. 按内存中的邻接
     * id 建边：只由 id
     * 更小的一侧触发 link，防止同一条边重复创建
     *
     * <p>
     * 节点 ID 以存档为准（覆盖内容 ID），保证星图内邻接引用稳定。
     *
     * <p>
     * 注意：不能拆成"先读节点字段再读邻接"的两遍流式读取，那样会因跳过邻接数据 导致流位置错位。
     */
    public static StarMap read(Reads r) {
        float w = r.f();
        float h = r.f();
        StarMap map = new StarMap(w, h);

        int starCount = r.i();
        StarNode[] nodes = new StarNode[starCount];
        int[][] neiIds = new int[starCount][];
        IntMap<StarNode> byId = new IntMap<>();

        for (int i = 0; i < starCount; i++) {
            int id = r.i();
            float x = r.f();
            float y = r.f();
            float size = r.f();
            String name = r.str();
            int n = r.s();
            int[] ids = new int[n];
            for (int j = 0; j < n; j++) {
                ids[j] = r.i();
            }

            // 优先复用已注册的内容节点（保持本地化名/命名空间），否则新建注册
            StarNode node = Contents.get(CType.StarNode.name() + "." + name, StarNode.class);
            if (node == null) {
                node = new StarNode(x, y, name);
            } else {
                node.x = x;
                node.y = y;
                node.size = size;
            }
            node.id = id; // 以存档 ID 为准（星图内引用用，覆盖内容表分配的 ID）
            nodes[i] = node;
            neiIds[i] = ids;
            byId.put(id, node);
            map.addNode(node);
        }

        for (int i = 0; i < starCount; i++) {
            StarNode a = nodes[i];
            for (int nid : neiIds[i]) {
                if (nid > a.id) {
                    StarNode b = byId.get(nid);
                    if (b != null) {
                        map.link(a, b);
                    }
                }
            }
        }
        return map;
    }
}
