package caliniya.vergvoke.system.input;

import arc.input.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.system.render.*;
import caliniya.vergvoke.world.building.*;

/**
 * 桌面端输入：方向键/WASD 平移相机，滚轮缩放；
 * 指挥模式下左键点选单位，左键拖拽框选（tap 与 pan 由 GestureDetector 区分）。
 */
public class DesktopInput extends Input {

	/** 键盘平移速度（像素/帧，随当前缩放放大）。 */
	private static final float keySpeed = 10f;

	/// 方向键/WASD 按住标志
	private boolean up, down, left, right;

	public DesktopInput() {
		// 建造预览 + 计划可视化挂后处理（画在世界内容之上的顶层叠加；光标坐标每帧从 worldX/worldY 取）
		PostRender.renders.add(() -> BuildPlan.drawPreview(worldX, worldY));
		PostRender.renders.add(BuildPlan::drawLinksAndFx);
	}

	@Override
	public void update(float delta) {
		if (blocked())
			return;

		// 帧时间乘进来（60TPS 时 delta ≈ 1），随缩放放大保证手感一致
		float speed = keySpeed * delta * (CommandData.viewType == CommandData.ViewType.uni ? Render.universeZoom : Render.currentZoom);

		if (up)
			moveCamera(0, speed);
		if (down)
			moveCamera(0, -speed);
		if (left)
			moveCamera(-speed, 0);
		if (right)
			moveCamera(speed, 0);
	}

	@Override
	public boolean keyDown(KeyCode key) {
		if (blocked())
			return false;

		// 建造模式：R 旋转放置朝向（绕锚点 0-3），Esc 退回普通模式
		if (canBuild()) {
			if (key == KeyCode.r) {
				CommandData.planAngle = (CommandData.planAngle + 1) % 4;
				return true;
			}
			if (key == KeyCode.escape) {
				exitBuild();
				return true;
			}
		}

		if (key == KeyCode.w || key == KeyCode.up)
			up = true;
		if (key == KeyCode.s || key == KeyCode.down)
			down = true;
		if (key == KeyCode.a || key == KeyCode.left)
			left = true;
		if (key == KeyCode.d || key == KeyCode.right)
			right = true;
		return false;
	}

	@Override
	public boolean keyUp(KeyCode key) {
		if (key == KeyCode.w || key == KeyCode.up)
			up = false;
		if (key == KeyCode.s || key == KeyCode.down)
			down = false;
		if (key == KeyCode.a || key == KeyCode.left)
			left = false;
		if (key == KeyCode.d || key == KeyCode.right)
			right = false;
		return false;
	}

	@Override
	public boolean scrolled(float amountX, float amountY) {
		if (blocked())
			return false;
		wheelZoom(amountY);
		return true;
	}

	@Override
	public boolean mouseMoved(int screenX, int screenY) {
		// 建造模式持续跟踪光标世界坐标：只在 tap 时 tranPos 的话，预览不跟随悬停移动
		// （arc fork 的 InputProcessor.mouseMoved 是 int 签名，int 自动拓宽传 tranPos）
		if (canBuild()) {
			tranPos(screenX, screenY);
		}
		return false;
	}

	/** 指挥模式下游戏视图才响应选中类输入。 */
	private boolean canSelect() {
		return !blocked() && CommandData.mode == CommandData.Mode.cmd && CommandData.viewType == CommandData.ViewType.game && Game.inGame;
	}

	/** 建造模式下游戏视图才响应放置类输入。 */
	private boolean canBuild() {
		return !blocked() && CommandData.mode == CommandData.Mode.build
				&& CommandData.viewType == CommandData.ViewType.game && Game.inGame;
	}

	/**
	 * 退出建造模式：回普通模式并清掉选中建筑。
	 *
	 * <p>选中态必须一起清——{@code selectBlock} 是切换逻辑，留着高亮会让再点同一图标
	 * 变成“先取消选中”，得点两次才能重新进入建造。
	 */
	private void exitBuild() {
		CommandData.mode = CommandData.Mode.nor;
		CommandData.buildBlock = null;
	}

	@Override
	public boolean touchDown(float x, float y, int pointer, KeyCode button) {
		// 左键按下且可选中：记框选起点（是否真的成框由 GestureDetector 区分 tap/pan 决定）
		if (canSelect() && button == KeyCode.mouseLeft) {
			tranPos(x, y);
			boxX1 = worldX;
			boxY1 = worldY;
			selStartX = x;
			selStartY = y;
			selEndX = x;
			selEndY = y;
			boxDragging = false;
		}
		return super.touchDown(x, y, pointer, button);
	}

	@Override
	public boolean tap(float x, float y, int count, KeyCode button) {
		// 建造模式：左键放置计划（锚点=光标左下角格），右键退回普通模式
		if (canBuild()) {
			tranPos(x, y);
			if (button == KeyCode.mouseLeft) {
				int tx = (int) (worldX / WorldData.TILE_SIZE);
				int ty = (int) (worldY / WorldData.TILE_SIZE);
				BuildPlan.tryPlace(CommandData.buildBlock, tx, ty, CommandData.planAngle);
				return true;
			}
			if (button == KeyCode.mouseRight) {
				// 右键点到计划 = 取消该计划（不退出模式）；点空地才退出建造模式
				BuildPlan.Plan plan = BuildPlan.planAt(worldX, worldY);
				if (plan != null) {
					BuildPlan.cancel(plan);
					return true;
				}
				exitBuild();
				return true;
			}
			return false;
		}

		// 普通模式：右键点计划 = 取消；点建筑 = 标记拆除（进计划池，单位会自动来拆并持续返还物品）
		if (!blocked() && CommandData.mode == CommandData.Mode.nor
				&& CommandData.viewType == CommandData.ViewType.game && Game.inGame
				&& button == KeyCode.mouseRight) {
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
			return false; // 点空地不消费（普通模式右键暂无其他行为）
		}

		if (!canSelect())
			return false;
		tranPos(x, y);

		// 右键 = 按当前指挥状态下发指令
		if (button == KeyCode.mouseRight) {
			issueCommand(worldX, worldY);
			return true;
		}

		// 左键 = 建造命令优先（点正在建造的工地=协助建造，点计划=前往建造），否则点选单位
		if (button == KeyCode.mouseLeft) {
			BuildPlan.Plan plan = buildSiteAt(worldX, worldY);
			if (plan == null) {
				plan = BuildPlan.planAt(worldX, worldY);
			}
			if (plan != null && commandUnitsBuild(plan)) {
				return true;
			}
			Game.team.data().intersect(worldX, worldY, 1f, e -> e instanceof Unit, this::tapSelect);
			return false;
		}
		return false;
	}

	@Override
	public boolean pan(float x, float y, float deltaX, float deltaY) {
		// 左键拖拽 = 框选中：只记屏幕终点，相机不平移（终点世界坐标绘制/结算时反投影）
		if (canSelect()) {
			selEndX = x;
			selEndY = y;
			boxDragging = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean panStop(float x, float y, int pointer, KeyCode button) {
		// 拖拽结束：结算框选（终点实时反投影）
		if (canSelect() && boxDragging) {
			selEndX = x;
			selEndY = y;
			boxSelectEnd();
			return true;
		}
		return false;
	}
}
