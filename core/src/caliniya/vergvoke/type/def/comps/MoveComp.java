package caliniya.vergvoke.type.def.comps;

import arc.math.geom.*;
import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.base.tool.*;

/**
 * 移动组件：目标点 + 速度向量 + 每帧位移积分（"走"的那一半）。
 *
 * <p>
 * 分工：寻路（算路径、每帧给出速度向量）归寻路系统（现在的 UnitMath）；
 * 本组件只负责把速度向量积到坐标上、到达判定、以及给空间索引留一个"位置变了"的标记。
 *
 * <p>
 * x / y 用 {@code @Import} 借 {@link PosComp} 的坐标，实体里只保留一份位置。
 */
@Component(name = "Move", index = 2, proc = "main")
public class MoveComp {

    /** 当前移动速度（像素/帧，60TPS 基准；每帧位移按它算 */
    public float speed = 5f;

    /** 格每秒，抄 UnitType.speedt；像素/帧的 speed = speedt × TILE_SIZE ÷ 60 */
    public float speedt = 5f;

    /** 期望速度向量（像素/帧，寻路系统每帧写入；到点后清 0） */
    public float speedX, speedY;

    /** 运动朝向（度，由寻路系统按速度向量算出；原在 Entity 基类，拆组件后归这里）。 */
    public float angle;

    /** 到达判定的容差（像素，直线移动时离目标小于它就直接贴过去） */
    public float arriveRange = 2f;

    /** 本帧是否真的发生了位移 */
    public boolean moving;

    /** 是否已经按当前目标寻过路（指挥改目标后要置回 false） */
    public boolean pathed;

    /** 位置变了、需要把新坐标写回空间索引（外部系统处理完自己清掉） */
    public boolean velocityDirty = true;

    /** 移动目标点 */
    @Save(index = 1)
    public float targetX;

    @Save(index = 2)
    public float targetY;

    /** 当前坐标（与 Entity 基类同名字段，处理器去重后实体用基类那一份；@Save 用于存档恢复位置） */
    @Save(index = 3)
    public float x;

    @Save(index = 4)
    public float y;

    /** 当前路径节点（寻路系统写；null = 直线移动） */
    public Ar<Point2> path;

    /** 正在走的路径节点下标 */
    public int pathIndex = 0;

    @Updata
    public void update(float delta) {
        // 注：注解处理器只搬字段声明，字段的初始化表达式不会跟到实体里，
        // 所以 arriveRange 没被显式赋值时，这里按旧默认值 2f 兜底
        float range = arriveRange > 0f ? arriveRange : 2f;

        float ox = x;
        float oy = y;
        float dx = targetX - x;
        float dy = targetY - y;

        if (path == null && dx * dx + dy * dy < range * range) {
            // 直线移动且已在容差内：贴住目标点，避免在目标附近来回抖动
            x = targetX;
            y = targetY;
            speedX = 0f;
            speedY = 0f;
        } else {
            // 按速度向量积分位移（速度向量由寻路系统算好）
            // delta 由系统一路传下来（主循环只取一次 Time.delta）
            x += speedX * delta;
            y += speedY * delta;
        }

        moving = x != ox || y != oy;

        // 注：初始化表达式不会跟到实体里（velocityDirty 声明处的 true 会丢），
        // 移动过的单位必须在这里标脏，GameProcess 才会把新坐标写回四叉树——
        // 否则移动过的单位永远选不中（选中/索敌都走四叉树 intersect）
        if (moving) velocityDirty = true;

    }
}
