package caliniya.vergvoke.type.def.comps;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.base.tool.*;
import caliniya.vergvoke.type.*;
import caliniya.vergvoke.type.type.*;

@Component(index = 6, name = "Turret")
public class TurretComp {
    public Weapon weapon;
    public Ar<BulletType> bullets;
}
