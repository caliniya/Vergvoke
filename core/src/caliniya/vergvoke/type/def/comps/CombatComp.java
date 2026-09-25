package caliniya.vergvoke.type.def.comps;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.base.tool.Ar;
import caliniya.vergvoke.type.Weapon;

/**
 * 战斗：武器组、索敌目标、开火开关。
 *
 * <p>
 * 索敌在 EntityProces 线程写 {@link #target}；开火在主线程经 {@link #updateWeapons}。
 */
@Component(name = "Combat", index = 5, proc = "main")
public class CombatComp {

    public Ar<Weapon> weapons;

    @Import
    public Entity<?, ?> target;

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
