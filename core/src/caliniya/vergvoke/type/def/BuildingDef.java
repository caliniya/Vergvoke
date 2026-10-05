package caliniya.vergvoke.type.def;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.type.def.comps.BlockComp;
import caliniya.vergvoke.type.def.comps.TargetComp;
import caliniya.vergvoke.type.def.comps.TurretComp;
import caliniya.vergvoke.type.type.BuildingType;


@Entity(comps = { BlockComp.class, TargetComp.class, TurretComp.class }, name = "Building", type = BuildingType.class)
public class BuildingDef {
}