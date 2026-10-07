package caliniya.vergvoke.type.def.craft.recipe;

import arc.struct.Ar;

/**
 * 配方组:一个建筑可选的多条配方 + 当前工作的那条 + 进度。
 *
 * <p>recipes 由 BuildingType.create 从 Block 的共享模板深拷贝而来;
 * current 为 -1 表示停工。切换配方时进度清零——不同配方的进度不通用。
 */
public class RecipeStack {

	/** 可选配方(实体私有副本,与 content 模板隔离)。 */
	public Ar<Recipe> recipes = new Ar<>();

	/** 当前工作的配方下标(-1 = 停工)。 */
	public int current = -1;

	/** 当前配方的生产进度(随存档持久化,经 CraftComp 的 @Write)。 */
	public float progress;

	public Recipe current() {
		return current >= 0 && current < recipes.size ? recipes.get(current) : null;
	}

	/** 切换当前配方(越界忽略;切换清进度)。 */
	public void select(int index) {
		if (index == current || index < 0 || index >= recipes.size) {
			return;
		}
		current = index;
		progress = 0f;
	}
}
