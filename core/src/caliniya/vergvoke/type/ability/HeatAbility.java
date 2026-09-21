package caliniya.vergvoke.type.ability;

import arc.util.io.Reads;
import arc.util.io.Writes;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.core.meta.stat.*;

/**
 * 过热能力：负责b>锁定</b>。热量由外部热源（武器/能力/模组）通过 {@code Entity.addHeat} 添加； 达到储热上限 → 锁定单位，热量归零后恢复。
 *
 * <p>这代表的是过热，散热由实体自行处理
 *
 * <p>没附加此能力的单位完全无过热机制（能力系统：附加即生效）。
 */
@SuppressWarnings("unused")
public class HeatAbility extends Ability {

  /** 最大储热上限（达到即过热锁定）。 */
  public float heatMax = 100f;

  /** 散热速度（每秒降低的热量）。 */
  public float heatSpeed = 20f;

  /** 当前热量。 */
  public float heat;

  @Override
  public Ability onCreate(Entity e) {
    e.heatSpeed = heatSpeed;
    e.heatable = true;
    e.heatMax = heatMax;
    return super.onCreate(e);
  }

  public HeatAbility() {
    super("heat");
  }

  public HeatAbility(float heatMax) {
    super("heat");
    this.heatMax = heatMax;
  }

  // 散热操作和解锁操作在{@code Entity.sync} 中进行，这里只同步以及进行锁定
  @Override
  public void update(Entity e, float dt) {
    heat = e.heat;
    if (heat >= heatMax) {
      e.locked = true;
    }
  }

  /** 当前热量比例（0~1），用于热量条显示。 */
  public float percent() {
    return heatMax <= 0f ? 0f : heat / heatMax;
  }

  // 类型信息不需要序列化，热量值会在单位中进行序列化
  @Override
  public void write(Entity e, Writes w) {
    super.write(w);
    w.f(e.heat);
  }

  @Override
  public void read(Entity e, Reads r) {
    super.read(r);
    heat = r.f();
    e.locked = heat >= heatMax;
  }

  @Override
  public void stats(StatStack stack) {
    stack.add(
        StatData.with(localizedName, StatType.function)
            .add(StatData.with(description).setLevel(1))
            .add(StatData.with(Stat.heatMax, heatMax))
            .add(StatData.with(Stat.heatSpeed, heatSpeed)));
  }

  @Override
  public void statAbility(StatStack stat) {
    stat.get(this, localizedName)
        .get(Stat.heat, heat).setLevel(2).live = () -> heat;
  }
}
