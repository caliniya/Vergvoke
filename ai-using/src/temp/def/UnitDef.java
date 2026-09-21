package caliniya.vergvoke.type.def;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.type.def.comps.*;
import caliniya.vergvoke.type.type.UnitType;

@Entity(comps = { StateComp.class, MoveComp.class, RenderComp.class, CombatComp.class, HitComp.class }, name = "Unit", type = UnitType.class)
public class UnitDef {
}
