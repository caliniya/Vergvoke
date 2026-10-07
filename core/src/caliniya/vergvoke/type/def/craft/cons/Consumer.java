package caliniya.vergvoke.type.def.craft.cons;

import caliniya.vergvoke.type.module.Module;

/// 消耗端:配方"吃什么"的一端。bind 运行时模块,cons() 结算扣料。
/// canCons() 供 Recipe.canCraft 做全端校验(缺料时整体不动,防扣了料产不出)。
@SuppressWarnings("unchecked")
public class Consumer<T extends Consumer<?, ?>, E extends Module> {

	public E module;

	public T bind(E module) {
		this.module = module;
		return (T) this;
	}

	/** 结算前检查:默认可消耗,子类按堆栈数量覆写 */
	public boolean canCons() {
		return true;
	}

	public void cons() {
	}

	/** 深拷贝(content 共享模板 → 实体私有副本;运行时模块由 Recipe.bind 另行绑定) */
	public Consumer<T, E> copy() {
		return new Consumer<>();
	}
}
