package caliniya.vergvoke.game.data;

import arc.func.*;
import arc.math.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.type.*;

/** 阵营数据 —— 提供针对特定阵营的实体查询和操作。 所有数据都存储在全局 WorldData 中，本类只负责按阵营过滤。 */
public class TeamData {
    public final TeamTypes team;

    public TeamData(TeamTypes team) {
        this.team = team;
    }

    // 查找此阵营中指定半径的实体
    public void find(float x, float y, float r, Cons<Entity> con) {
        WorldData.units.intersect(
                x - r,
                y - r,
                r * 2,
                r * 2,
                u -> {
                    if (Mathf.dst2(u.x, u.y, x, y) <= r * r) {
                        con.get(u);
                    }
                });
        WorldData.buildings.intersect(
                x - r,
                y - r,
                r * 2,
                r * 2,
                b -> {
                    if (Mathf.dst2(b.x, b.y, x, y) <= r * r) {
                        con.get(b);
                    }
                });
    }
}
