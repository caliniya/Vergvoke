package caliniya.vergvoke.type.def.craft.output;

import caliniya.vergvoke.type.module.Module;

/** 产出器负责生成物品,其他的不管 */
@SuppressWarnings("unchecked")
public class Outputer<T extends Outputer<?, ?>, I extends Module> {

	public I module;

	public T bind(I module) {
		this.module = module;
		return (T) this;
	}

	/** 结算前检查:默认可产出,子类按容量/过滤覆写 */
	public boolean canOutput() {
		return true;
	}

	public void output() {
	}

	/** 深拷贝(content 共享模板 → 实体私有副本;运行时模块由 Recipe.bind 另行绑定) */
	public Outputer<T, I> copy() {
		return new Outputer<>();
	}
}
