package caliniya.vergvoke.type.def;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.type.def.comps.*;
import caliniya.vergvoke.type.type.*;

@Entity(comps = {BlockComp.class, TargetComp.class, TurretComp.class, CraftComp.class}, name = "Building", type = BuildingType.class)
public class BuildingDef {
}
