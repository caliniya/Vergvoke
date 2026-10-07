package caliniya.vergvoke.type.def.comps;

import arc.util.io.Reads;
import arc.util.io.Writes;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.type.def.craft.recipe.Recipe;
import caliniya.vergvoke.type.def.craft.recipe.RecipeStack;

/**
 * 通用生产逻辑:持配方组({@link RecipeStack}),驱动"当前配方的进度推进 + 两端结算"。
 *
 * <p>配方在 Factory 上声明、BuildingType.create 深拷贝装配;非生产建筑 stack 为 null,
 * update 直接跳过。进度/当前配方下标随存档持久化(@Write,读档恢复到同一配方同一进度)。
 */
@Component(name = "Craft", index = 8, proc = "main")
public class CraftComp {

	/** 配方组(装配见 BuildingType.create;null = 非生产建筑)。 */
	public RecipeStack stack;

	@Updata
	public void update(float delta) {
		Recipe r = stack == null ? null : stack.current();
		if (r == null) {
			return;
		}

		stack.progress += delta;
		if (stack.progress < r.time) {
			return;
		}
		// 缺料 / 满仓:进度钳在 time,等条件满足,不丢产出
		if (!r.canCraft()) {
			return;
		}

		r.craft();
		stack.progress = 0f;
	}

	/** 存档写:当前配方下标 + 进度(配方本身是配置,由 type 重建)。 */
	@Write
	public void write(Writes w) {
		w.b((byte) (stack == null ? -1 : stack.current));
		w.f(stack == null ? 0f : stack.progress);
	}

	@Read
	public void read(Reads r) {
		int cur = r.b();
		float pr = r.f();
		if (stack != null) {
			stack.current = cur;
			stack.progress = pr;
		}
	}
}
