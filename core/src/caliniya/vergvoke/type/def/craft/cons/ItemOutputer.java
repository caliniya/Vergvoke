package caliniya.vergvoke.type.def.craft.cons;

import caliniya.vergvoke.type.def.craft.output.*;
import caliniya.vergvoke.type.module.*;

public class ItemOutputer extends Outputer<ItemOutputer> {

	public ItemOutputer bind(ItemModule module) {
		this.module = module;
		return this;
	}

}
