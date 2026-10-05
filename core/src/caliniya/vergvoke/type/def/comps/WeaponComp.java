package caliniya.vergvoke.type.def.comps;

import arc.math.Mathf;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.base.game.Entity;
import arc.struct.Ar;
import caliniya.vergvoke.type.Weapon;

/**
 * 战斗：武器组、索敌目标、开火开关。
 *
 * <p>
 * 单位级 {@link #target} 由 TargetComp.update 随主线程实体更新维护；
 * 武器级锁敌在 {@link #updateWeapons} 开头做，随后冷却 / 转向 / 开火——全在主线程（索敌线程已退役）。
 */
@Component(name = "Weapon", index = 5, proc = "main")
public class WeaponComp {

    public Ar<Weapon> weapons;

    @Import
    public Entity<?, ?> target;

    public boolean canShoot = true;

    /** 主线程驱动武器：索敌 → 冷却 / 转向 / 开火。 */
    public void updateWeapons(float delta) {
        if (weapons == null) {
            return;
        }
        // 武器级锁敌：旋转武器失效/超射程时重搜；固定武器直接用单位级目标
        for (int i = 0; i < weapons.size; i++) {
            Weapon w = weapons.get(i);
            if (w == null) {
                continue;
            }
            float wx = w.owner.x + w.type.x;
            float wy = w.owner.y + w.type.y;
            if (w.rotate) {
                // 与 WeaponType.findTarget 一致：射程算到目标边缘（见 Entity.dst2Surface）
                if (w.target == null
                        || w.target.health <= 0
                        || w.target.dst2Surface(wx, wy) > w.type.range * w.type.range) {
                    w.type.findTarget(w, wx, wy);
                }
            } else {
                w.target = target;
            }
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
