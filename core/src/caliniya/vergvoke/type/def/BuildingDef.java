package caliniya.vergvoke.type.def;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.type.def.comps.BlockComp;
import caliniya.vergvoke.type.def.comps.TargetComp;
import caliniya.vergvoke.type.def.comps.TurretComp;
import caliniya.vergvoke.type.type.BuildingType;

/**
 * 建筑实体定义：BlockComp 身份/占位 + TargetComp 索敌 + TurretComp 开火。
 * 类型链接走基类 Entity.type（{@link BuildingType}），工厂也在 BuildingType 上。
 */
@Entity(comps = { BlockComp.class, TargetComp.class, TurretComp.class }, name = "Building", type = BuildingType.class)
public class BuildingDef {

}
