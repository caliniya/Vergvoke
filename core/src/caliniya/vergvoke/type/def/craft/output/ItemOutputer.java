package caliniya.vergvoke.type.def.craft.output;

import caliniya.vergvoke.type.*;
import caliniya.vergvoke.type.module.*;

public class ItemOutputer extends Outputer<ItemOutputer, ItemModule> {

	/** 每次结算产出的堆栈(类型 + 数量) */
	public Item output;

	public ItemOutputer output(Item stack) {
		this.output = stack;
		return this;
	}

	@Override
	public ItemOutputer bind(ItemModule module) {
		this.module = module;
		return this;
	}

	@Override
	public boolean canOutput() {
		return module != null && output != null && !output.isEmpty()
				&& module.accepts(output.type)
				&& module.capacity - module.getAmount(output.type) >= output.amount;
	}

	@Override
	public void output() {
		module.addItem(output);
	}

	@Override
	public ItemOutputer copy() {
		return new ItemOutputer().output(output);
	}
}
