package caliniya.vergvoke.type.type;

import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import caliniya.vergvoke.base.api.TechNodeContent;
import caliniya.vergvoke.base.game.ContentType;
import caliniya.vergvoke.base.type.CType;

/**
 * 液体类型：可流动/存储的连续资源（燃料、冷却剂、工质等）。
 *
 * <p>
 * 用途由消费方（发电厂/散热模块/推进模块等）决定，液体类型本身只提供通用物理与价值属性。
 */
public class LiquidType extends ContentType implements TechNodeContent {

    /** 图标。 */
    public TextureRegion icon;

    /** 显示颜色（管道/UI）。 */
    public Color color = Color.white;

    /** 粘度（0~1，影响管线/物流流速）。 */
    public float viscosity = 0.5f;

    /** 是否气态工质（太空常见）。 */
    public boolean gas;

    /** 能量密度（燃料价值：发电/加力推进）。 */
    public float energyDensity;

    /** 热容量（冷却价值：散热）。 */
    public float heatCapacity;

    public LiquidType(String name) {
        super(name, CType.Liquid);
    }

    @Override
    public TechNodeContent[] requirements() {
        return requirements;
    }

    public void load() {
        // icon = Core.atlas.find(name);
    }
}
