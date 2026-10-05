package caliniya.vergvoke.campaign;

import caliniya.vergvoke.base.api.TechNodeContent;
import arc.struct.Ar;

/**
 * 科技树节点。
 *
 * <p>解锁条件：同时满足「父节点已解锁」和「自身定义的前置全部已研究」。 结构（父子关系）由代码在构建科技树时显式指定， 节点可关联一个 {@link
 * TechNodeContent}（可空，纯策略节点无需内容）。
 */
public class TechNode {

  /** 节点名（有内容时取 content.techName()）。 */
  public final String name;

  /** 关联的内容（null = 纯策略节点，没有对应解锁内容）。 */
  public final TechNodeContent content;

  /** 树结构：父节点（null = 根节点）。 */
  public TechNode parent;

  /** 树结构：子节点。 */
  public final Ar<TechNode> children = new Ar<>();

  /** 自身定义的前置节点（全部研究后，配合父节点解锁 → 本节点解锁）。 */
  public final Ar<TechNode> requirements = new Ar<>();

  /** 反向引用：依赖本节点的前置节点（便于遍历/展示）。 */
  public final Ar<TechNode> dependents = new Ar<>();

  /** 是否已解锁。 */
  public boolean unlocked;

  /** 是否已研究（进度）。 */
  public boolean researched;

  /** 纯策略节点（无关联内容）。 */
  public TechNode(String name) {
    this(name, null);
  }

  /** 由内容创建节点，名称取 content.techName()。 */
  public TechNode(TechNodeContent content) {
    this(content.techName(), content);
  }

  public TechNode(String name, TechNodeContent content) {
    this.name = name;
    this.content = content;
  }

  /** 设置自身额外前置节点，并建立反向引用。 */
  public TechNode requires(TechNode... nodes) {
    for (TechNode n : nodes) {
      requirements.add(n);
      n.dependents.add(this);
    }
    return this;
  }

  /** 在自身下插入子节点并返回，子节点父指针自动设置。 */
  public TechNode child(String name) {
    TechNode n = new TechNode(name);
    n.parent = this;
    children.add(n);
    return n;
  }

  /**
   * 解锁条件：父节点已研究（或为根节点），且自身前置全部已研究。
   *
   * <p>语义：unlocked = 现在可以研究（可点研究按钮）；researched = 已研究完成。 根节点无父节点，一开始就解锁；研究父节点后其子节点才解锁。
   */
  public boolean available() {
    if (parent != null && !parent.researched) return false;
    for (TechNode r : requirements) {
      if (!r.researched) return false;
    }
    return true;
  }

  /** 尝试研究：需要已解锁，成功则标记已研究并回调 content.onResearch()。 */
  public boolean research() {
    if (!unlocked) return false;
    researched = true;
    if (content != null) content.onResearch();
    return true;
  }

  @Override
  public String toString() {
    return name;
  }
}
