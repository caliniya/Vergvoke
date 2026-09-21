package caliniya.vergvoke.type.def.comps;

import arc.graphics.g2d.TextureRegion;
import caliniya.vergvoke.annotation.Annotations.*;

/** 渲染贴图：从 UnitType.load 拷到实例。 */
@Component(name = "Render", index = 4, proc = "main")
public class RenderComp {

    public TextureRegion region;
    public TextureRegion cell;
}
