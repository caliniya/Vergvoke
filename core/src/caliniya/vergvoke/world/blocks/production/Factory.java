package caliniya.vergvoke.world.blocks.production;

import arc.struct.Ar;

import caliniya.vergvoke.base.ecs.Building;
import caliniya.vergvoke.base.type.TeamTypes;
import caliniya.vergvoke.type.def.craft.recipe.Recipe;
import caliniya.vergvoke.type.def.craft.recipe.RecipeStack;
import caliniya.vergvoke.type.type.BuildingType;

/**
 * 工厂:生产建筑配置载体(行为在 {@code CraftComp}),建筑从配方组里选一条工作。
 */
public class Factory extends BuildingType {

	/** 可选配方(共享只读模板;create 时深拷贝 + 绑定运行时模块)。 */
	public Ar<Recipe> recipes = new Ar<>();

	public Factory(String name) {
		super(name);
		this.solid = true;
	}

	/** 把共享配方模板深拷贝进实例并绑定运行时模块;默认选第一条(无配方 current = -1,不开工)。 */
	@Override
	public Building create(TeamTypes team, int tx, int ty, int angle) {
		Building b = super.create(team, tx, ty, angle);
		b.stack = new RecipeStack();
		for (Recipe r : recipes) {
			Recipe copy = r.copy();
			copy.bind(b.item, b.liquid, b.power);
			b.stack.recipes.add(copy);
		}
		// 默认选第一条配方（有配方才开工）
		b.stack.current = recipes.isEmpty() ? -1 : 0;
		return b;
	}
}
