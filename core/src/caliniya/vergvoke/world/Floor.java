package caliniya.vergvoke.world;

import arc.*;
import arc.graphics.g2d.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.type.*;

public class Floor extends ContentType {

    public TextureRegion region;

    public Floor(String name) {
        super(name, CType.Floor);
    }

    @Override
    public void load() {
        region = Core.atlas.find(name);
    }
}
