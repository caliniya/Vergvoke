package caliniya.vergvoke.base.type;

import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.type.enhance.*;
import caliniya.vergvoke.type.type.*;
import caliniya.vergvoke.world.*;
import caliniya.vergvoke.world.stars.*;

public enum CType {
	Block(BuildingType.class),
	Floor(Floor.class),
	ENVBlock(ENVBlock.class),
	Unit(UnitType.class),
	Item(ItemType.class),
	Liquid(LiquidType.class),
	StarNode(StarNode.class),
	Enhance(EnhancementType.class);


	public final Class<? extends ContentType> type;

	CType(Class<? extends ContentType> contentClass) {
		this.type = contentClass;
	}
}
