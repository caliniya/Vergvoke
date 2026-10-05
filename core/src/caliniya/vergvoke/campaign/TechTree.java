package caliniya.vergvoke.campaign;

import arc.util.io.Reads;
import arc.util.io.Writes;
import caliniya.vergvoke.base.api.TechNodeContent;
import arc.struct.Ar;

/**
 * 科技树。
 *
 * <p>采用手动构建：科技树只有一个根节点（{@link #root(String)} / {@link #root(TechNodeContent)}，重复调用会抛异常）， 之后只能通过
 * {@link #addChild(TechNode, String)}（策略节点）或 {@link #addChild(TechNode,
 * TechNodeContent)}（关联内容的节点）往下挂子节点。
 *
 * <p>解锁规则见 {@link TechNode#available()}：父节点已研究 + 自身前置满足。 进度（unlocked/researched）是运行时状态，通过
 * write/read 序列化。
 */
public class TechTree {

  public final Ar<TechNode> nodes = new Ar<>();

  /** 唯一根节点（null = 尚未设置）。 */
  public TechNode root;

  public TechTree() {}

  /** 设置唯一根节点（纯策略节点）。重复调用会抛出异常。 */
  public TechNode root(String name) {
    if (root != null) {
      throw new IllegalStateException("TechTree already has a root: " + root.name);
    }
    root = new TechNode(name);
    nodes.add(root);
    return root;
  }

  /** 设置唯一根节点（关联内容）。重复调用会抛出异常。 */
  public TechNode root(TechNodeContent content) {
    if (root != null) {
      throw new IllegalStateException("TechTree already has a root: " + root.name);
    }
    root = new TechNode(content);
    nodes.add(root);
    return root;
  }

  /** 向指定节点添加一个策略子节点（只指定名字）。 */
  public TechNode addChild(TechNode parent, String name) {
    TechNode n = parent.child(name);
    nodes.add(n);
    return n;
  }

  /** 向指定节点添加一个关联内容的子节点。 */
  public TechNode addChild(TechNode parent, TechNodeContent content) {
    TechNode n = new TechNode(content);
    n.parent = parent;
    parent.children.add(n);
    nodes.add(n);
    return n;
  }

  /** 按名称查找节点，不存在返回 null。 */
  public TechNode get(String name) {
    for (TechNode n : nodes) {
      if (n.name.equals(name)) return n;
    }
    return null;
  }

  /** 刷新解锁状态：从唯一根节点开始沿树向下计算。 */
  public void updateUnlocks() {
    if (root != null) refresh(root);
    // 兜底：未挂到树上的孤立节点
    for (TechNode n : nodes) {
      n.unlocked = n.available();
    }
  }

  private void refresh(TechNode n) {
    n.unlocked = n.available();
    for (TechNode c : n.children) {
      refresh(c);
    }
  }

  /** 研究一个节点；节点不存在或未解锁时返回 false。 */
  public boolean research(String name) {
    TechNode n = get(name);
    if (n == null) return false;
    boolean ok = n.research();
    updateUnlocks();
    return ok;
  }

  /** 序列化进度：节点数 + [名称, 研究状态] × n。结构由代码定义，这里只存进度。 */
  public void write(Writes w) {
    w.i(nodes.size);
    for (TechNode n : nodes) {
      w.str(n.name);
      w.bool(n.researched);
    }
  }

  /** 读入存档进度：按名称恢复研究状态（节点需已由代码构建）。 */
  public void read(Reads r) {
    int count = r.i();
    for (int i = 0; i < count; i++) {
      String name = r.str();
      boolean researched = r.bool();
      TechNode n = get(name);
      if (n != null) n.researched = researched;
    }
    updateUnlocks();
  }
}
