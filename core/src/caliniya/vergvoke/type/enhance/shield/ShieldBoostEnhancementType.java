package caliniya.vergvoke.type.enhance.shield;

import caliniya.vergvoke.base.type.DamageType;
import caliniya.vergvoke.type.Enhancement;
import caliniya.vergvoke.type.ability.Ability;
import caliniya.vergvoke.type.ability.ShieldAbility;
import caliniya.vergvoke.type.ability.ShieldFieldAbility;
import caliniya.vergvoke.type.enhance.EnhancementType;

/** 护盾强化插件类型：开启时提高护盾最大强度与动能抗性，关闭恢复（行为定义在类型里，实体只存数据）。 */
public class ShieldBoostEnhancementType extends EnhancementType {

  /** 开启时增加的最大护盾强度。 */
  public float maxStrengthBonus = 1f;

  /** 开启时增加的动能抗性（0~1）。 */
  public float kineticResistBonus = 0.3f;

  public ShieldBoostEnhancementType() {
    super("shieldboost");
  }

  public ShieldBoostEnhancementType(boolean register) {
    super("shieldboost", register);
  }

  @Override
  public void rebind(Enhancement e) {
    Ability raw = e.entity.getAbility(ShieldAbility.class);
    if (raw == null) {
      raw = e.entity.getAbility(ShieldFieldAbility.class);
    }
    e.ability = raw;
  }

  @Override
  public void onEnable(Enhancement e) {
    if (e.ability == null) return;
    if (e.ability instanceof ShieldAbility s) {
      e.vars.put("savedStrength", s.maxStrength);
      e.vars.put("savedKineticResist", s.resist(DamageType.Kinetic));
      s.maxStrength += maxStrengthBonus;
      s.resist[DamageType.Kinetic.ordinal()] += kineticResistBonus;
    } else if (e.ability instanceof ShieldFieldAbility s) {
      e.vars.put("savedStrength", s.maxStrength);
      e.vars.put("savedKineticResist", s.resist(DamageType.Kinetic));
      s.maxStrength += maxStrengthBonus;
      s.resist[DamageType.Kinetic.ordinal()] += kineticResistBonus;
    }
  }

  @Override
  public void onDisable(Enhancement e) {
    if (e.ability == null) return;
    if (e.ability instanceof ShieldAbility s) {
      s.maxStrength = (float) e.vars.get("savedStrength", 0f);
      s.resist[DamageType.Kinetic.ordinal()] = (float) e.vars.get("savedKineticResist", 0f);
    } else if (e.ability instanceof ShieldFieldAbility s) {
      s.maxStrength = (float) e.vars.get("savedStrength", 0f);
      s.resist[DamageType.Kinetic.ordinal()] = (float) e.vars.get("savedKineticResist", 0f);
    }
  }
}
