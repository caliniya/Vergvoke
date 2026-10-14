package caliniya.vergvoke.system.input;

import arc.input.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.ui.*;
import caliniya.vergvoke.world.building.*;

/**
 * 移动端输入：拖拽平移相机，双指捏合缩放；
 * 指挥模式下长按进入框选（pan 期间框选），tap 点选单位。
 */
public class MobileInput extends Input {

	/** 指挥模式下游戏视图才响应选中类输入。 */
	private boolean canSelect() {
		return !blocked() && CommandData.mode == CommandData.Mode.cmd && CommandData.viewType == CommandData.ViewType.game && Game.inGame;
	}

	@Override
	public boolean touchDown(float x, float y, int pointer, KeyCode button) {
		// 按下即记框选起点：单指 pan 是相机平移，长按（longPress）后才升级为框选
		if (canSelect() && pointer == 0) {
			tranPos(x, y);
			boxX1 = worldX;
			boxY1 = worldY;
			selStartX = x;
			selStartY = y;
			selEndX = x;
			selEndY = y;
		}
		return super.touchDown(x, y, pointer, button);
	}

	/** 长按进入框选模式：pan 从相机平移切换为框选。 */
	@Override
	public boolean longPress(float x, float y) {
		if (canSelect()) {
			tranPos(x, y);
			boxX1 = worldX;
			boxY1 = worldY;
			selEndX = x;
			selEndY = y;
			boxDragging = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean pan(float x, float y, float deltaX, float deltaY) {
		if (blocked())
			return false;

		// 框选拖拽：只记屏幕终点，不动相机（终点世界坐标绘制/结算时反投影）
		if (boxDragging) {
			selEndX = x;
			selEndY = y;
			return true;
		}

		float s = CommandData.viewType == CommandData.ViewType.uni ? Render.universeZoom : Render.currentZoom;
		moveCamera(-deltaX * s, -deltaY * s);
		return false;
	}

	@Override
	public boolean panStop(float x, float y, int pointer, KeyCode button) {
		if (boxDragging) {
			selEndX = x;
			selEndY = y;
			boxSelectEnd();
			return true;
		}
		return false;
	}

	@Override
	public boolean tap(float x, float y, int count, KeyCode button) {
		// 普通/拆除模式：点计划 = 取消；点建筑 = 标记拆除（移动端无右键，点按即标记；与桌面右键同款）
		if (!blocked()
				&& (CommandData.mode == CommandData.Mode.nor || CommandData.mode == CommandData.Mode.demo)
				&& CommandData.viewType == CommandData.ViewType.game && Game.inGame) {
			tranPos(x, y);
			BuildPlan.Plan plan = BuildPlan.planAt(worldX, worldY);
			if (plan != null) {
				BuildPlan.cancel(plan);
				return true;
			}
			int tx = (int) (worldX / WorldData.TILE_SIZE);
			int ty = (int) (worldY / WorldData.TILE_SIZE);
			Building b = WorldData.world != null ? WorldData.world.getBuilding(tx, ty) : null;
			if (b != null && b.type != null) {
				BuildPlan.addDemolish(b);
				return true;
			}
			return false; // 点空地不消费（相机平移走 pan）
		}

		if (!canSelect())
			return false;
		tranPos(x, y);
		// 指挥模式建造命令：点正在建造的工地 = 协助建造；双击计划 = 前往建造。
		// 其余单击照常走下面的移动指令/点选（单击=移动，不拦截）
		BuildPlan.Plan site = buildSiteAt(worldX, worldY);
		if (site != null && commandUnitsBuild(site)) {
			return true;
		}
		if (count >= 2) {
			BuildPlan.Plan plan = BuildPlan.planAt(worldX, worldY);
			if (plan != null && commandUnitsBuild(plan)) {
				return true;
			}
		}
		// 指挥状态下 tap 点地图 = 下发当前指令；点选走长按外的普通 tap 也兼顾选单位：
		// 先尝试点选，命中单位则只切换选中不 redundant 下发
		boolean[] hit = {false};
		Game.team.data().intersect(worldX, worldY, 1f, e -> e instanceof Unit, e -> {
			if (((Unit) e).contains(worldX, worldY)) {
				hit[0] = true;
				tapSelect(e);
			}
		});
		// 没点中单位 → 按当前指挥状态对选中单位下发指令
		if (!hit[0]) {
			issueCommand(worldX, worldY);
		}
		return true;
	}

	@Override
	public boolean zoom(float initialDistance, float distance) {
		if (blocked())
			return false;
		pinchZoom(initialDistance, distance);
		return true;
	}
}
