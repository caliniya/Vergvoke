package caliniya.vergvoke.type.def.comps;

import caliniya.vergvoke.annotation.Annotations.*;

/**
 * 示例：{@code @OverrideEntity} 覆写 {@code Entity.contains} 点命中。
 *
 * <p>组件方法体里用到的基类字段（x/y/size）需在组件上声明同名字段才能过编译；
 * 处理器发现与 {@code Entity} 同名时不会注入实体，实体侧用的是基类那一份。
 */
@Component(name = "HitDemo", index = 9, proc = "main")
public class HitComp {

    /** 组件专属：命中半宽（0 = 跟随 size）。 */
    public float hitHalfWidth;

    // --- 仅供组件源码编译；实体侧来自 Entity 基类，不重复注入 ---
    public float x, y, size;

    @OverrideEntity
    public boolean contains(float worldX, float worldY) {
        float half = hitHalfWidth > 0f ? hitHalfWidth : (size > 0f ? size * 0.5f : 4f);
        float dx = worldX - x;
        float dy = worldY - y;
        return dx * dx + dy * dy <= half * half;
    }
}
