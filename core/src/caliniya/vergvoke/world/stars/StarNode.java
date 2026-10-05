package caliniya.vergvoke.world.stars;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.QuadTree.*;
import arc.math.geom.Rect;
import arc.util.io.Writes;
import caliniya.vergvoke.base.game.ContentType;
import arc.struct.Ar;
import caliniya.vergvoke.base.type.CType;

/**
 * 宇宙中的一个星系节点。
 *
 * <p>继承 {@link ContentType}：节点拥有
 *
 * <ul>
 *   <li>internalName —— 命名空间（"StarNode.xxx"），未来翻译用
 *   <li>name —— 原始名称
 *   <li>localizedName —— 本地化名称
 * </ul>
 *
 * <p>约定：节点原始名称 == 它对应地图的内部名（maps/地图名.aes），
 * 节点与地图通过名称关联，见 {@link #mapName()}。
 */
public class StarNode extends ContentType implements QuadTreeObject {

  public float x, y;

  /** 绘制尺寸（像素） */
  public float size = 64f;

  public static TextureRegion main;

  public Ar<StarNode> nei;

  {
    main = Core.atlas.find("starNode");
  }

  /** 创建战役/设计节点：注册进内容表，获得内容 ID（short，1~32767）。 */
  public StarNode(float x, float y, String name) {
    super(name, CType.StarNode);
    this.x = x;
    this.y = y;
    this.nei = new Ar<>();
  }

  /** 创建临时/程序生成节点（不注册内容表，内容 ID 为 0，星图内 ID 由 StarMap 分配）。 */
  private StarNode(float x, float y, String name, boolean register) {
    super(name, CType.StarNode, register);
    this.x = x;
    this.y = y;
    this.nei = new Ar<>();
  }

  public static StarNode createUnregistered(float x, float y, String name) {
    return new StarNode(x, y, name, false);
  }

  /** 该节点对应的地图内部名（约定：与节点原始名称相同）。 */
  public String mapName() {
    return name;
  }

  public void add(StarNode... neig) {
    nei.add(neig);
  }

  /** 与指定节点的距离平方（比距离更常用，避免开方） */
  public float dst2(StarNode other) {
    return Mathf.dst2(x, y, other.x, other.y);
  }

  /** 与指定节点的距离 */
  public float dst(StarNode other) {
    return Mathf.dst(x, y, other.x, other.y);
  }

  public void draw() {
    Draw.rect(main, x, y, size, size);
  }

  /** 序列化自身（邻居由 StarMap 读取后统一重建，故这里只写邻居 id 列表）。 */
  public void write(Writes w) {
    w.i(id);
    w.f(x);
    w.f(y);
    w.f(size);
    w.str(name);
    w.s(nei.size);
    for (StarNode n : nei) {
      w.i(n.id);
    }
  }

  @Override
  public void hitbox(Rect out) {
    float half = size / 2f;
    out.set(x - half, y - half, size, size);
  }
}
