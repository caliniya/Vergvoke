package caliniya.vergvoke.content;

import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.type.ability.*;
import caliniya.vergvoke.type.type.*;

public class UnitTypes {

	public static UnitType test, test2;

	public static void load() {
		test =
				new UnitType("testunit") {
					{
						// this.armor = 500;
						this.armorMax = 500;
						this.armorValue = 30;
						// this.energy = 100;
						this.energyMax = 100;
						this.energyRegen = 10;
						// 类型级演示数据：护甲对动能 50% 抗性（DataWindow 可展示）
						this.armorResist[DamageType.Kinetic.ordinal()] = 0.5f;
						this.hitbox = new float[]{0f, 60f, 60f, 0f, 0f, 60f, 60f, 0f, 60f};

						addWeapons(
								new WeaponType("aa") {
									{
										mirror = true;
										x = 50;
										bullet =
												new BulletType() {
													{
														this.damage = 1f;
														this.knock = 1f;
														this.speed = 1f;
													}
												};
										rotate = true;
										this.reload = 10f;
									}
								});

						this.abilities.add(
								new ShieldFieldAbility(500f, 195f) {
									{
										this.sides = 7;
										this.regen = 10;
										this.energyCost = 3;
										this.resist[DamageType.Energy.ordinal()] = 0.6f;
									}
								});

						this.abilities.add(
								new HeatAbility(100f) {
									{
										this.heatSpeed = 50f;
									}
								});
					}
				};

		test2 =
				new UnitType("starNode") {
					{
						this.size = 128f;
					}
				};
	}
}
