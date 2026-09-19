package caliniya.vergvoke.type.def.comps;

import caliniya.vergvoke.annotation.Annotations.*;

/**
 * 位置组件：实体在世界里的坐标（像素，世界坐标）。
 *
 * <p>纯数据：每帧的位移由 {@link MoveComp} 的更新负责，这里只存坐标。
 * 别的组件要读写坐标时用 {@code @Import} 借这两个字段，实体里不会出现两份 x / y。
 */
@Component(name = "Pos", index = 4, proc = "main")
public class PosComp {

    /** 世界坐标 X（@Save 后随实体存档序列化） */
    @Save(index = 1)
    public float x = 0f;

    /** 世界坐标 Y */
    @Save(index = 2)
    public float y = 0f;

}
