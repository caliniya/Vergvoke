package caliniya.vergvoke.content;

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
		// 第一座生产建筑：每秒产出 1 Ge,堆积在自身仓库(容量 100)
		factory =
				new Factory("factory") {
					{
						this.size = 2;
						this.craftTime = 60f;
						this.outputItem = Items.Ge;
						this.outputAmount = 1;
					}
				};
	}
}
