package caliniya.vergvoke.ui.windows;

import arc.*;
import arc.graphics.*;
import arc.math.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import caliniya.vergvoke.ui.Button;

public class Window {

	public Table window;
	public Table main, top, low;
	// 最小尺寸：pack 自适应后的下限，内容少时不会缩得更小。
	// 全局兜底下限是屏幕宽/高的 3/7（见 build()），这里设置的更大值会覆盖默认下限。
	public float w = 400f, h = 300f;
	public float maxOut = 0.8f; // 允许80%移出屏幕
	public String title = "Window";

	/** 是否全屏：true 时窗口铺满整个屏幕（忽略 {@link #maxExpand} 与最小尺寸）。 */
	public boolean fullscreen = false;

	/** 非全屏时窗口最大扩张到屏幕宽/高的比例（0~1，默认 60%）。 */
	public float maxExpand = 0.6f;

	/** 是否显示全屏切换按钮（默认打开；某些小弹窗如暂停菜单/进度条可关闭）。 */
	public boolean showFullButton = true;

	/** 是否为模态窗口。模态窗口会显示暗色遮罩并阻断背景所有输入。 */
	public boolean modal = false;

	/** 模态遮罩层，仅 modal=true 时使用 */
	public Table modalOverlay;

	/** 遮罩透明度，0=完全透明，1=完全不透明。默认 0.5 */
	public float modalAlpha = 0.5f;

	public Window() {
		this("");
	}

	public Window(String titleText) {
		this.title = titleText;
		// 容器
		main = new Table();
		top = new Table();
		low = new Table();
	}

	/** 链式设置模态 */
	public Window modal(boolean modal) {
		this.modal = modal;
		return this;
	}

	/** 链式设置遮罩透明度 */
	public Window modalAlpha(float alpha) {
		this.modalAlpha = alpha;
		return this;
	}

	public void build() {
		if (window != null) window.remove();
		if (modalOverlay != null) modalOverlay.remove();

		main.clearChildren();
		top.clearChildren();
		low.clearChildren();

		window = new Table();
		if (!fullscreen) window.setSize(w, h);

		window.setBackground(
				new NinePatchDrawable((NinePatchDrawable) Core.atlas.getDrawable("Window")));

		window.touchable = Touchable.enabled;
		window.cullable = true;

		// --- 模态遮罩 ---
		if (modal) {
			modalOverlay = new Table();
			modalOverlay.setFillParent(true);
			modalOverlay.touchable = Touchable.enabled;

			// 暗色半透明背景
			modalOverlay.setBackground(Core.atlas.getDrawable("white"));
			modalOverlay.setColor(0, 0, 0, modalAlpha);

			// 拦截所有点击，阻止穿透到背景
			modalOverlay.addListener(
					new InputListener() {
						@Override
						public boolean touchDown(
								InputEvent event, float x, float y, int pointer, arc.input.KeyCode button) {
							return true;
						}
					});

			Core.scene.add(modalOverlay);
		}

		top(top);
		main(main);
		low(low);
		ScrollPane scrollPane = new ScrollPane(main);
		scrollPane.setFadeScrollBars(true);
		scrollPane.setScrollingDisabled(false, false); // 纵向 + 横向滚动（内容过宽时可横滚）

		Table titleTable = new Table();
		Label titleLabel = new Label(title);
		titleLabel.setColor(Color.white);

		// 拖动监听
		titleLabel.addListener(
				new InputListener() {
					float grabX, grabY;

					@Override
					public boolean touchDown(
							InputEvent event, float x, float y, int pointer, arc.input.KeyCode button) {
						grabX = event.stageX - window.x;
						grabY = event.stageY - window.y;
						window.toFront();
						return true;
					}

					@Override
					public void touchDragged(InputEvent event, float x, float y, int pointer) {
						float newX = event.stageX - grabX;
						float newY = event.stageY - grabY;

						float parentWidth = Core.scene.getWidth();
						float parentHeight = Core.scene.getHeight();
						float winW = window.getWidth();
						float winH = window.getHeight();
						float minX = -winW * maxOut;
						float minY = -winH * maxOut;
						float maxX = parentWidth - winW * (1 - maxOut);
						float maxY = parentHeight - winH * (1 - maxOut);

						window.setPosition(Mathf.clamp(newX, minX, maxX), Mathf.clamp(newY, minY, maxY));
					}
				});

		titleTable.add(titleLabel).growX().fillX().left().padLeft(10f);

		// 全屏切换按钮（可选，默认显示）：全屏 ↔ 还原为最大 60%
		if (showFullButton) {
			Button fullBtn =
					new Button(
							fullscreen ? "@window.restore" : "@window.fullscreen",
							() -> {
								fullscreen = !fullscreen;
								build();
							});
			// 与关闭按钮保持间隔
			titleTable.add(fullBtn).padRight(5f).align(Align.topRight);
		}

		Button closeBtn = new Button("@close", () -> remove());
		titleTable.add(closeBtn).align(Align.topRight);

		// --- 组装窗口 ---
		window.add(titleTable).growX();
		window.row();

		window.image().color(Color.valueOf("03ECEDFF")).fillX().height(1f);
		window.row();

		window.add(top).growX().top();
		window.row();
		window.add(scrollPane).grow().pad(10f);
		window.row();
		window.add(low).growX().bottom();

		Core.scene.add(window);

		if (fullscreen) {
			window.setSize(Core.scene.getWidth(), Core.scene.getHeight());
		} else {
			float maxW = Core.scene.getWidth() * maxExpand;
			float maxH = Core.scene.getHeight() * maxExpand;
			float minW = Math.min(Math.max(w, Core.scene.getWidth() * 3f / 7f), maxW);
			float minH = Math.min(Math.max(h, Core.scene.getHeight() * 3f / 7f), maxH);
			window.pack();
			float finalW = Mathf.clamp(window.getWidth(), minW, maxW);
			float finalH = Mathf.clamp(window.getHeight(), minH, maxH);
			window.setSize(finalW, finalH);
		}
		window.validate();

		// 初始居中（使用最终尺寸）
		window.setPosition(
				(Core.scene.getWidth() - window.getWidth()) / 2,
				(Core.scene.getHeight() - window.getHeight()) / 2);
	}

	public void remove() {
		if (window != null) window.remove();
		if (modalOverlay != null) {
			modalOverlay.remove();
			modalOverlay = null;
		}
		top.remove();
		main.remove();
		low.remove();
	}

	// 应该通过覆写这三个方法来实现内容
	public void main(Table t) {
	}

	public void top(Table t) {
	}

	public void low(Table t) {
	}
}
