package caliniya.vergvoke.type.def.craft.cons;

import caliniya.vergvoke.type.module.Module;

//这表示一种用于消费的类型定义，可能用于表示某种资源或能力的消耗行为。
public class Consumer {

	public Module module;

	public Consumer bind(Module module) {
		this.module = module;
		return this;
	}

}
