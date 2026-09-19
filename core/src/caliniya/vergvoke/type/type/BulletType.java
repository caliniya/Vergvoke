package caliniya.vergvoke.type.type;

import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.graphics.g2d.Draw;
import arc.Core;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.base.type.DamageType;
import caliniya.vergvoke.type.*;

public class BulletType {

    public float speed = 6f;
    public float damage = 50f;

    /** 伤害类型（默认动能）。 */
    public DamageType damageType = DamageType.Kinetic;

    /** 破甲：无视护甲的固定伤害减免（护甲容量照扣）。 */
    public boolean breakArmor;

    /** 穿甲：直接穿过护甲层攻击核心（必定不能穿盾，数值受限）。 */
    public boolean bypassArmor;

    /** 破盾：无视护盾的强度减伤（护盾容量照扣）。 */
    public boolean breakShield;

    /** 穿盾：直接穿过护盾层。 */
    public boolean bypassShield;

    /** 击退力度（命中时沿子弹方向给目标的冲量；0 = 不击退）。 */
    public float knock = 0f;

    public float lifetime = 600f;
    public float size = 60f;

    // 渲染相关
    public float drawSize = 1f; // 整体缩放比例
    public Color frontColor = Color.white; // 子弹前景色
    public Color backColor = Color.gray; // 子弹背景色

    public TextureRegion region;

    public BulletType() {
    }

    public void load() {
        this.region = Core.atlas.find("bullet");
    }

    /** 子弹更新逻辑 (每帧调用) */
    // 基础的子弹运动目前在子弹处理中进行，未来整合进这里
    public void update(Bullet b) {
    }

    /** 子弹绘制逻辑 */
    public void draw(Bullet b) {
        if (b.id <= 0)
            return;
        if (region == null)
            return;

        // 1. 绘制背层 (光晕)
        Draw.color(backColor);
        Draw.rect(region, b.x, b.y, size * 1.5f, size * 1.5f, b.rotation - 90);

        // 2. 绘制前层 (核心)
        Draw.color(frontColor);
        Draw.rect(region, b.x, b.y, size, size, b.rotation - 90);

        Draw.color(); // 重置
    }

    /** 命中单位时的回调 */
    public void hit(Bullet b, Entity target) {
        target.hit(b);
    }

    /** 命中墙壁/消失时的回调 */
    public void despawn(Bullet b) {
        // TODO: 播放消失特效
    }
}
