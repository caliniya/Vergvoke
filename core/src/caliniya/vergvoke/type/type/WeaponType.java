package caliniya.vergvoke.type.type;

import arc.*;
import arc.graphics.g2d.*;
import caliniya.vergvoke.core.meta.ui.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.base.api.*;

public class WeaponType implements Cloneable, DrawType<Weapon> {

    public String name;
    public TextureRegion region;

    // 基础属性
    public float range = 400f;
    public float rotateSpeed = 5f;
    public float reload = 60f;
    public float x = 0f, y = 0f;
    public float shootX = 0f, shootY = 0f;

    /** 每发热量（向单位添加的热量；单位无过热机制时忽略）。 */
    public float heatPerShot = 2f;

    // 镜像控制
    public boolean mirror = true;
    public boolean flipSprite = false;
    public boolean alternate = true;
    public int otherSide = -1;

    // 标记是否为生成的镜像副本
    public boolean isMirror = false;

    public BulletType bullet;

    public boolean rotate = true;
    public float shootCone = 2f;

    public WeaponType(String name) {
        this.name = name;
    }

    public void load(String UnitName) {
        String textureName = UnitName + "-" + this.name;
        region = Core.atlas.find(textureName, "air");
        bullet.load();
    }

    // --- 目标查找 ---

    /** 默认查找目标实现，使用武器射程进行索敌。 特殊武器（如导弹、激光）可覆写此方法实现自定义索敌逻辑。 */
    public void findTarget(Weapon w, float wx, float wy) {
        w.target = null;
        // 因为lamba不可能会赋值出一个null，那么没有敌人的时候 下面这行代码实际上就会完全没有进行任何操作
        Entities.closestEnemy(w.owner.team, wx, wy, range, e -> w.target = e);
    }

    // --- 镜像 ---

    public void flip() {
        this.x *= -1;
        this.shootX *= -1;
        this.flipSprite = !this.flipSprite;
        this.isMirror = true;
    }

    public WeaponType copy() {
        try {
            return (WeaponType) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    public void draw(Weapon w) {
        float wRot = w.owner.rotation + w.rotation;
        Draw.rect(region, w.wx, w.wy, wRot);
    }

    public void drawDebug(Weapon w) {
        Draw.color(Pal.light);
        Lines.circle(w.wx, w.wy, range);
    }
}
