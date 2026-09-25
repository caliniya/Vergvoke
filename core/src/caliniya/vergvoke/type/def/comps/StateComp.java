package caliniya.vergvoke.type.def.comps;

import arc.util.io.Reads;
import arc.util.io.Writes;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.base.type.TeamTypes;

@Component(name = "State", index = 1, proc = "main")
public class StateComp {

    /** 是否被玩家选中。 */
    public boolean isSelected;

    /** 阵营（与 Entity 基类同名字段，处理器去重后实体用基类那一份；方法式序列化用于存档恢复阵营）。 */
    public TeamTypes team;

    /** 组件里有 @Save 字段就不能再用 @Write/@Read，所以选中位 + 阵营一起走方法式序列化。 */
    @Write
    public void write(Writes w) {
        w.bool(isSelected);
        w.b((byte) (team == null ? -1 : team.ordinal()));
    }

    @Read
    public void read(Reads r) {
        isSelected = r.bool();
        int ord = r.b();
        TeamTypes[] values = TeamTypes.values();
        team = (ord >= 0 && ord < values.length) ? values[ord] : null;
    }
}
