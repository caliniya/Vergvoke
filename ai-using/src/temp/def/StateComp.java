package caliniya.vergvoke.type.def.comps;

import caliniya.vergvoke.annotation.Annotations.*;

/** 选择状态：指挥面板 / 选中圈。 */
@Component(name = "State", index = 1, proc = "main")
public class StateComp {

    /** 是否被玩家选中。 */
    @Save(index = 1)
    public boolean isSelected;
}
