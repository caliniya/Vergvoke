package caliniya.vergvoke.world;

import arc.*;
import arc.graphics.g2d.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.type.*;

public class ENVBlock extends ContentType {

    public TextureRegion region;

    public boolean solid = true;

    public ENVBlock(String name) {
        super(name, CType.ENVBlock);
    }

    @Override
    public void load() {
        region = Core.atlas.find(name);
    }
}
