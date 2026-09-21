package caliniya.vergvoke.type.def.comps;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.base.tool.Ar;
import caliniya.vergvoke.type.Weapon;

/**
 * 战斗：武器组、索敌目标、开火开关。
 *
 * <p>索敌在 EntityProces 线程写 {@link #target}；开火在主线程经 {@link #updateWeapons}。
 */
@Component(name = "Combat", index = 5, proc = "main")
public class CombatComp {

    /** 挂载武器实例（由 UnitType.create 按 WeaponType 生成）。 */
    public Ar<Weapon> weapons;

    /** 当前锁定目标（固定武器跟随单位目标；炮塔武器自行锁敌）。 */
    public Entity<?> target;

    /** 是否允许开火（false = 瘫痪/停火时武器只跟位不射击）。 */
    public boolean canShoot = true;

    /** 主线程驱动武器：冷却 / 转向 / 开火。 */
    public void updateWeapons(float delta) {
        if (weapons == null) {
            return;
        }
        for (int i = 0; i < weapons.size; i++) {
            Weapon w = weapons.get(i);
            if (w != null) {
                w.update(delta, canShoot);
            }
        }
    }

    @Updata
    public void update(float delta) {
        // 武器逻辑由系统/实体侧显式调用 updateWeapons，这里不重复跑
    }
}
