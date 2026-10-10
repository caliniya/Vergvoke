package caliniya.vergvoke.core;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.ui.layout.*;
import arc.util.viewport.*;
import caliniya.vergvoke.base.game.*;
import caliniya.vergvoke.core.meta.stat.*;
import caliniya.vergvoke.map.*;
import caliniya.vergvoke.ui.fragment.*;
import caliniya.vergvoke.ui.windows.*;

import static arc.Core.*;

public class UI {

	public static float scl;

	// 调试显示器
	public static DebugFragment debug;
	// 主游戏ui
	public static HUDFragment hud;
	// 游戏菜单ui
	public static MenuFragment menu;
	// 宇宙界面
	public static UniverseFragment universe;
	// 地图列表
	public static MapsWindow maps;

	// ui用的相机和视口
	public static Camera camera;
	public static Viewport vport;

	public static PauseWindow pauseWindow;

	private static boolean isDebugShown = true;

	public static float safeAreaSize;

	public static void initAll() {
		scl = Scl.scl();

		debug = new DebugFragment();
		hud = new HUDFragment();
		menu = new MenuFragment();
		maps = new MapsWindow();
		pauseWindow = new PauseWindow();
		universe = new UniverseFragment();
	}

	public static void Menu() {
		scene.clear();
		menu.build();
		Debug();
	}

	// 加载界面渲染逻辑
	public static void Loading(float progress) {
		float screenW = graphics.getWidth();
		float screenH = graphics.getHeight();
		float centerX = screenW / 2f;
		float centerY = screenH / 2f;

		float barWidth = 300f;
		float barHeight = 20f;
		float padding = 4f;

		Draw.color(Color.white);
		Lines.stroke(2f);
		Lines.rect(centerX - barWidth / 2f, centerY - barHeight / 2f, barWidth, barHeight);
		float maxFillWidth = barWidth - padding * 2;
		float currentFillWidth = maxFillWidth * progress;
		float fillHeight = barHeight - padding * 2;
		float leftEdgeX = centerX - barWidth / 2f + padding;
		float drawCenterX = leftEdgeX + currentFillWidth / 2f;
		Fill.rect(drawCenterX, centerY, currentFillWidth, fillHeight);
		Draw.flush();
	}

	public static void Game() {
		scene.clear();
		hud.build();
		Debug();
	}

	public static void Maps() {
		Maps.load();
		maps.h = (float) ((graphics.getHeight() * 0.7) / scl);
		maps.w = (float) ((graphics.getWidth() * 0.7) / scl);
		maps.build();
	}

	/**
	 * 创建一个窗口
	 *
	 * @param Ttitle      窗口标题
	 * @param widthRatio  窗口宽度占屏幕宽度的比例 (0~1)
	 * @param heightRatio 窗口高度占屏幕高度的比例 (0~1)
	 */
	public static void Window(String Ttitle, float widthRatio, float heightRatio) {
		float actualW = graphics.getWidth() * widthRatio / scl;
		float actualH = graphics.getHeight() * heightRatio / scl;
		Window win = new Window() {
			{
				w = actualW;
				h = actualH;
			}
		};
		win.build();
	}

	public static void Window(float widthRatio, float heightRatio, StatStack data) {
		float actualW = graphics.getWidth() * widthRatio / scl;
		float actualH = graphics.getHeight() * heightRatio / scl;
		DataWindow win = new DataWindow(data);
		win.w = actualW;
		win.h = actualH;
		win.build();
	}

	public static void openDataWindow(StatStack data) {
		new DataWindow(data).build();
	}

	public static void openEntityWindow(Entity<?, ?> entity) {
		new EntityWindow(entity).build();
	}

	/** 调试显示器是否开启（渲染层叠加导航可视化时用）。 */
	public static boolean debugShown() {
		return isDebugShown;
	}

	public static void Debug() {
		if (isDebugShown) {
			debug.add();
		}
	}
}
