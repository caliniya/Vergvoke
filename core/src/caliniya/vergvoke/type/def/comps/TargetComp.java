package caliniya.vergvoke.type.def.comps;

import arc.func.Boolf;
import arc.math.Angles;
import arc.math.Mathf;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.base.game.Entity;
import caliniya.vergvoke.base.type.TeamTypes;
import caliniya.vergvoke.game.Entities;

/** 索敌组件：持有并维护单位级战斗目标。update 随主线程实体更新驱动（索敌线程已退役）。 */
@Component(index = 4, name = "target")
public class TargetComp {

    public Entity<?, ?> target;

    /** 指向目标的绝对角度（度），固定武器开火的 shootCone 判定用。 */
    public float angleToTarget;

    /** 索敌半径（像素）。 */
    public float range = 400f;

    /** 周期性重索敌间隔（tick）。目标失效时无论如何都会立即重搜；<=0 表示仅在失效时重搜。 */
    public float retargetInterval = 60f;

    /** 重索敌计时器。 */
    public float retargetTimer;

    /** 目标过滤器，null 表示不过滤。 */
    public Boolf<Entity<?, ?>> filter;

    // 与 Entity 基类同名字段：仅作源码占位（处理器去重后实体用基类那一份），update 方法体里直接用短名
    public float x;
    public float y;
    public TeamTypes team;

    @Updata
    public void update(float delta) {
        retargetTimer -= delta;
        if (!targetValid(x, y) || (retargetInterval > 0f && retargetTimer <= 0f)) {
            if (retargetInterval > 0f)
                retargetTimer = retargetInterval;
            findTarget(x, y, team);
        }
        updateAngle(x, y);
    }

    /** 目标是否仍然有效：存在、存活、且在索敌半径内。 */
    public boolean targetValid(float x, float y) {
        return target != null
                && target.health > 0
                && Mathf.dst2(x, y, target.x, target.y) <= range * range;
    }

    /**
     * 默认索敌：范围内最近的敌人（不同阵营即敌人），可带过滤器。
     * 特殊索敌策略（如优先脆皮、仅对空）可在调用侧提供 {@link #filter} 或整体替换本方法。
     */
    public void findTarget(float x, float y, TeamTypes team) {
        if (team == null)
            return;
        target = null;
        if (filter != null) {
            // 用适配 lambda 中转，避免 filter 的泛型实参（Entity<?,?>）与方法签名里的裸 Entity 不匹配
            Entities.closestEnemy(team, x, y, range, e -> filter.get(e), e -> target = e);
        } else {
            target = Entities.closestEnemy(team, x, y, range);
        }
    }

    /** 刷新指向目标的绝对角度（目标会移动，需每帧调用）。 */
    public void updateAngle(float x, float y) {
        if (target != null) {
            angleToTarget = Angles.angle(x, y, target.x, target.y);
        }
    }

}
