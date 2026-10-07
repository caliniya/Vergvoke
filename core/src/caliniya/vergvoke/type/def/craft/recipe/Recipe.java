package caliniya.vergvoke.type.def.craft.recipe;

import arc.struct.Ar;

import caliniya.vergvoke.type.def.craft.cons.*;
import caliniya.vergvoke.type.def.craft.output.*;
import caliniya.vergvoke.type.module.*;

/**
 * 配方:消耗端 + 产出端 + 工时。
 *
 * <p>content 侧声明的对象是<b>共享只读模板</b>;create 时 {@link #copy()} 深拷贝进实体的
 * {@link RecipeStack},再 {@link #bind} 运行时模块。两阶段执行:
 * {@link #canCraft()} 全端校验通过才 {@link #craft()} 结算——缺料/满仓都整体不动,
 * 防止"扣了料产不出"。
 */
public class Recipe {

	/** 工时(delta 累积单位,60 = 1 秒)。 */
	public float time = 60f;

	public Ar<Consumer<?, ?>> consumers = new Ar<>();
	public Ar<Outputer<?, ?>> outputers = new Ar<>();

	public Recipe time(float t) {
		this.time = t;
		return this;
	}

	public Recipe cons(Consumer<?, ?> c) {
		consumers.add(c);
		return this;
	}

	public Recipe output(Outputer<?, ?> o) {
		outputers.add(o);
		return this;
	}

	/** 全端校验:任一端不满足就整体不动。 */
	public boolean canCraft() {
		for (Consumer<?, ?> c : consumers) {
			if (!c.canCons()) return false;
		}
		for (Outputer<?, ?> o : outputers) {
			if (!o.canOutput()) return false;
		}
		return true;
	}

	/** 结算:先全部扣料,再全部产出(调用前必须 canCraft() 通过)。 */
	public void craft() {
		for (Consumer<?, ?> c : consumers) {
			c.cons();
		}
		for (Outputer<?, ?> o : outputers) {
			o.output();
		}
	}

	/** 深拷贝:端对象一并复制(运行时模块由 {@link #bind} 另行绑定)。 */
	public Recipe copy() {
		Recipe r = new Recipe().time(time);
		for (Consumer<?, ?> c : consumers) {
			r.consumers.add(c.copy());
		}
		for (Outputer<?, ?> o : outputers) {
			r.outputers.add(o.copy());
		}
		return r;
	}

	/** 绑定实体的运行时模块(建筑没有的模块传 null,端的 canX 检查会自钳)。新增端类型时在这里补分支。 */
	public void bind(ItemModule item, LiquidModule liquid, PowerModule power) {
		for (Consumer<?, ?> c : consumers) {
			if (c instanceof ItemConsumer ic) {
				ic.bind(item);
			}
		}
		for (Outputer<?, ?> o : outputers) {
			if (o instanceof ItemOutputer io) {
				io.bind(item);
			}
		}
	}
}
