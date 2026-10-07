package caliniya.vergvoke.content;

import caliniya.vergvoke.type.*;
import caliniya.vergvoke.type.def.craft.output.*;
import caliniya.vergvoke.type.def.craft.recipe.*;
import caliniya.vergvoke.type.type.*;
import caliniya.vergvoke.world.*;
import caliniya.vergvoke.world.blocks.defence.*;
import caliniya.vergvoke.world.blocks.production.*;

public class Blocks {

	public static Block TestBlock;
	public static Turret testTurret;
	public static Factory factory;

	public static void load() {
		TestBlock =
				new Block("test-building") {
					{
						this.size = 3;
					}
				};
		testTurret =
				new Turret("testturret") {
					{
						this.size = 3;
						this.bulletType =
								new BulletType() {
									{
									}
								};
					}
				};
		// 第一座生产建筑:配方是共享模板,create 深拷贝。两条配方速率不同,供调试窗切换测试
		factory =
				new Factory("factory") {
					{
						this.size = 2;
						this.recipes.add(
								new Recipe()
										.time(60f)
										.output(new ItemOutputer().output(new Item(Items.Ge, 1))));
						this.recipes.add(
								new Recipe()
										.time(180f)
										.output(new ItemOutputer().output(new Item(Items.Ge, 4))));
					}
				};
	}
}
