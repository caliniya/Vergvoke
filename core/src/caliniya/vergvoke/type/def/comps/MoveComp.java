package caliniya.vergvoke.type.def.comps;

import caliniya.vergvoke.annotation.Annotations.*;

@Component(name = "Move", index = 2, proc = "main")
public class MoveComp {

    public float speed = 5f;

    public float speedt = 5f;

    @Import
    public float x;

    @Import
    public float y;

    @Updata
    public void update() {
    }

}
