package caliniya.vergvoke.system.input;

import arc.*;
import arc.graphics.g2d.*;
import arc.input.GestureDetector.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import caliniya.vergvoke.base.ecs.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.base.type.*;
import caliniya.vergvoke.core.*;
import caliniya.vergvoke.core.meta.ui.*;
import caliniya.vergvoke.game.*;
import caliniya.vergvoke.game.data.*;
import caliniya.vergvoke.system.render.*;
import caliniya.vergvoke.ui.windows.*;

/**
 * 输入基类：实现平台无关的具体操作（移动相机 / 缩放等），平台子类在各手势、按键事件中接入这些操作。
 * <p>视图分流统一走 {@link CommandData#viewType}；暂停（GamePause）或界面存在模态窗口时（{@link Window#hasModal()}）不响应相机类操作。
 */
public abstract class Input implements InputProcessor, GestureListener {

	/// 当前指针/手指所在的世界坐标
	public float worldX, worldY;

	/// 游戏暂停标志（暂停时不响应相机类输入）
	protected boolean paused;

	public Input() {
		Events.on(EventType.GamePause.class, e -> paused = e.pause);
		// 框选指示挂进前处理渲染（建筑之后单位之前画）
		PreRender.renders.add(this::drawBoxSel);
	}

	/** 是否应屏蔽背景输入：暂停中或存在可见的模态窗口。 */
	protected boolean blocked() {
		return paused || Window.hasModal();
	}

	/** 每帧由主循环驱动（键盘平移等平台自有逻辑在子类覆盖）。 */
	public void update(float delta) {

	}

	public void tranPos(float x, float y) {
		if (CommandData.viewType == CommandData.ViewType.game) {
			Vec2 temp = Core.camera.unproject(x, y);
			worldX = temp.x;
			worldY = temp.y;
		} else if (
				CommandData.viewType == CommandData.ViewType.uni
		) {
			Vec2 temp = Render.uniCamera.unproject(x, y);
			worldX = temp.x;
			worldY = temp.y;
		}
	}

	/** 按当前视图平移对应相机。 */
	public void moveCamera(float x, float y) {
		if (CommandData.viewType == CommandData.ViewType.game) {
			Core.camera.position.add(x, y);
		} else if (
				CommandData.viewType == CommandData.ViewType.uni
		) {
			Render.uniCamera.position.add(x, y);
		}
	}

	/** 滚轮缩放：按当前视图调 Render 的缩放（内部自带 clamp）。 */
	public void wheelZoom(float amountY) {
		if (CommandData.viewType == CommandData.ViewType.game) {
			Render.zoom(amountY * 0.1f * Render.currentZoom);
		} else if (
				CommandData.viewType == CommandData.ViewType.uni
		) {
			Render.zoomUniverse(amountY * 0.1f * Render.universeZoom);
		}
	}

	/** 捏合缩放（移动端双指）：按两指距离比例缩放当前视图相机。 */
	public void pinchZoom(float initialDistance, float distance) {
		if (initialDistance == 0)
			return;
		float ratio = initialDistance / distance;
		if (CommandData.viewType == CommandData.ViewType.game) {
			Render.setZoom(pinchSnapshot * ratio);
		} else if (
				CommandData.viewType == CommandData.ViewType.uni
		) {
			Render.setUniverseZoom(pinchSnapshot * ratio);
		}
	}

	/// 捏合缩放开始前的缩放快照（touchDown 时记录）
	protected float pinchSnapshot = 1f;

	// ===== 框选状态（子类手势维护，绘制/结算共用）=====

	/// 是否正在框选拖拽
	public boolean boxDragging;

	/// 框选起点世界坐标
	public float boxX1, boxY1;

	/// 框选起点的屏幕坐标（起点不随相机变，存世界坐标即可）
	protected float selStartX, selStartY;

	/// 框选终点的屏幕坐标（终点随相机变，绘制/结算时实时反投影）
	protected float selEndX, selEndY;

	/** 当前框选终点的世界坐标（相机移动后自动跟随）。 */
	protected Vec2 selEndWorld() {
		if (CommandData.viewType == CommandData.ViewType.game) {
			return Core.camera.unproject(selEndX, selEndY);
		} else if (
				CommandData.viewType == CommandData.ViewType.uni
		) {
			return Render.uniCamera.unproject(selEndX, selEndY);
		}
		return Vec2.ZERO;
	}

	/** 按下时记录当前缩放，作为捏合缩放基准。 */
	@Override
	public boolean touchDown(float x, float y, int pointer, KeyCode button) {
		if (CommandData.viewType == CommandData.ViewType.game) {
			pinchSnapshot = Render.currentZoom;
		} else if (
				CommandData.viewType == CommandData.ViewType.uni
		) {
			pinchSnapshot = Render.universeZoom;
		}
		return false;
	}

	// ===== 选中（平台无关操作，指挥模式下生效）=====

	/** 切换单个单位的选中状态（已选取消，未选加入）。 */
	protected void toggleUnit(Unit u) {
		if (CommandData.checkedUnits.contains(u)) {
			u.isSelected = false;
			CommandData.checkedUnits.remove(u, true);
		} else {
			u.isSelected = true;
			CommandData.checkedUnits.add(u);
		}
	}

	/**
	 * 点选：切换单个单位的选中态。
	 *
	 * @param e 命中的实体（由空间查询回调给出；非单位、非本方或没真正点中 hitbox 的忽略）
	 */
	protected void tapSelect(Entity<?, ?> e) {
		if (e instanceof Unit u && u.team == Game.team && u.contains(worldX, worldY)) {
			toggleUnit(u);
			UI.hud.refreshCommand();
		}
	}

	/**
	 * 框选：矩形范围内友军单位已选的取消、未选的加入。
	 * 四叉树按外接正方形粗查，这里再用真实矩形精判（外接圆角落的误命中不生效）。
	 * 结算后清除拖拽状态。
	 *
	 * @param x1 框选起点世界坐标
	 * @param y1 框选起点世界坐标
	 * @param x2 框选终点世界坐标
	 * @param y2 框选终点世界坐标
	 */
	protected void boxSelect(float x1, float y1, float x2, float y2) {
		boxDragging = false;
		float minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
		float minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
		Game.team.data().intersect(
				(minX + maxX) / 2f,
				(minY + maxY) / 2f,
				Math.max(maxX - minX, maxY - minY) / 2f,
				e -> e instanceof Unit && e.team == Game.team
						&& ((Unit) e).x + ((Unit) e).hitboxSize() / 2f >= minX
						&& ((Unit) e).x - ((Unit) e).hitboxSize() / 2f <= maxX
						&& ((Unit) e).y + ((Unit) e).hitboxSize() / 2f >= minY
						&& ((Unit) e).y - ((Unit) e).hitboxSize() / 2f <= maxY,
				e -> toggleUnit((Unit) e));
		UI.hud.refreshCommand();
	}

	/** 用当前框选状态结算（终点实时反投影，相机移动后松手也正确）。 */
	protected void boxSelectEnd() {
		Vec2 end = selEndWorld();
		boxSelect(boxX1, boxY1, end.x, end.y);
	}

	// ===== 下令（平台无关操作，指挥模式下生效）=====

	/**
	 * 按当前指挥状态（{@link CommandData#commandMode}）对选中单位下发指令。
	 * Move → 就近可达点寻路移动；Stop → 原地停下；None → 不执行。
	 */
	protected void issueCommand(float wx, float wy) {
		if (CommandData.checkedUnits.isEmpty())
			return;

		if (CommandData.commandMode == CommandData.CommandMode.Move) {
			moveUnits(wx, wy);
		} else if (CommandData.commandMode == CommandData.CommandMode.Stop) {
			stopUnits();
		}
	}

	/** 下达移动指令：目标点取就近可达瓦片中心，无选中单位或不可达（8 格内无空位）不下发。 */
	protected void moveUnits(float wx, float wy) {
		for (Unit u : CommandData.checkedUnits) {
			if (u == null || u.health <= 0)
				continue;
			Vec2 dst = RouteData.nearestPassable(wx, wy, Mathf.ceil(u.sizet));
			if (dst == null)
				continue;

			// 直接改字段：RouteSystem 每帧重算，pathed=false 触发重新寻路
			synchronized (WorldData.moveunits) {
				u.targetX = dst.x;
				u.targetY = dst.y;
				u.pathed = false;
				u.path = null;
				if (!WorldData.moveunits.array.contains(u)) {
					WorldData.moveunits.add(u);
				}
			}
		}
	}

	/** 停止选中单位：清速度/路径并摘出寻路队列（未在队列的直接原地清目标）。 */
	protected void stopUnits() {
		synchronized (WorldData.moveunits) {
			for (Unit u : CommandData.checkedUnits) {
				if (u == null || u.health <= 0)
					continue;
				u.speedX = 0;
				u.speedY = 0;
				u.targetX = u.x;
				u.targetY = u.y;
				u.path = null;
				u.pathed = false;
				WorldData.moveunits.remove(u);
			}
		}
	}

	/** 框选指示（前处理渲染回调）：拖拽中画选择矩形。 */
	public void drawBoxSel() {
		if (!boxDragging)
			return;
		Vec2 end = selEndWorld();
		float minX = Math.min(boxX1, end.x), maxX = Math.max(boxX1, end.x);
		float minY = Math.min(boxY1, end.y), maxY = Math.max(boxY1, end.y);
		Draw.color(Pal.light, 0.15f);
		Fill.rect((minX + maxX) / 2f, (minY + maxY) / 2f, maxX - minX, maxY - minY);
		Draw.color(Pal.light, 0.9f);
		Lines.stroke(1.5f);
		Lines.rect(minX, minY, maxX - minX, maxY - minY);
		Draw.color();
	}
}
