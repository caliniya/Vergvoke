package caliniya.vergvoke.world.blocks.production;

import arc.struct.Ar;

import caliniya.vergvoke.type.def.craft.recipe.Recipe;
import caliniya.vergvoke.world.*;

/**
 * 工厂:生产建筑的配置载体(行为在 {@code CraftComp})。
 *
 * <p>持多条可选配方(content 侧共享模板);create 时深拷贝进实体的 RecipeStack,
 * 建筑从配方组里选一条工作。
 */
public class Factory extends Block {

	/** 可选配方(共享只读模板;create 时深拷贝 + 绑定运行时模块)。 */
	public Ar<Recipe> recipes = new Ar<>();

	public Factory(String name) {
		super(name);
		this.solid = true;
	}
}
