package caliniya.vergvoke.type.def.craft.cons;

import caliniya.vergvoke.type.*;
import caliniya.vergvoke.type.module.*;

public class ItemConsumer extends Consumer<ItemConsumer, ItemModule> {

	/** 每次结算消耗的堆栈(类型 + 数量) */
	public Item cons;

	public ItemConsumer cons(Item stack) {
		this.cons = stack;
		return this;
	}

	@Override
	public boolean canCons() {
		return module != null && cons != null && !cons.isEmpty()
				&& module.getAmount(cons.type) >= cons.amount;
	}

	@Override
	public void cons() {
		module.removeItem(cons);
	}

	@Override
	public ItemConsumer copy() {
		return new ItemConsumer().cons(cons);
	}
}
