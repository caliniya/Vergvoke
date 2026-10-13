package caliniya.vergvoke.type.def.comps;

import caliniya.vergvoke.annotation.Annotations.*;
import caliniya.vergvoke.game.data.WorldData;

/// 建造能力组件：纯数据（建造逻辑在 BuildAi）；辅助加成 = Σ 各辅助单位 buildSpeed
@Component(name = "Build", index = 9)
public class BuildComp {

	/// 并非所有实体都有建造能力（false = 挂着但不参与建造）
	public boolean canBuild = true;

	/// 建造速度；辅助加成取所有参与单位此值之和
	public float buildSpeed = 1f;

	/// 消耗效率：>1 消耗更少物品（1 = 标准消耗）
	public float buildEfficiency = 1f;

	/// 建造半径（像素）：进入它才登记 spawn 工地
	public float buildRange = 7f * WorldData.TILE_SIZE;
}
