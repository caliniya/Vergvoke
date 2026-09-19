package caliniya.vergvoke.system.render;

import static arc.Core.*;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.Group;
import caliniya.vergvoke.core.meta.ui.Pal;

/**
 * 调试渲染器：给 scene 中每一个 UI 元素绘制边框，便于制作 UI 时定位。
 *
 * <p>在 {@code Vergvoke.update()} 中于 {@code Draw.proj(UI.camera)} 之后单独调用，
 * 因此这里的绘制坐标必须是 stage 全局坐标系。
 */
public class DebugRender extends caliniya.vergvoke.system.System<DebugRender> {

  /** 全局实例（需要时由外部创建）。 */
  public static DebugRender it;

  /** 复用的坐标转换缓存，避免每帧每元素 new。 */
  private final Vec2 tmp = new Vec2();

  @Override
  public DebugRender init() {
    index = 14;
    return super.init(false,false);
  }

  @Override
  public void update(float delta) {
    Lines.stroke(2f, Color.green);

    // 递归遍历整棵 UI 树，给每个元素画边框
    for (Element e : scene.root.getChildren()) {
      drawBounds(e);
    }

    // 重置绘制状态，避免污染后续（尤其是地图）的渲染
    Draw.reset();
  }

  /** 递归绘制某元素及其所有子元素的边框（转换到 stage 全局坐标）。 */
  private void drawBounds(Element e) {
    if (e == null || !e.visible) return;

    // fillParent 的全屏容器画出来就是整屏边框，无意义 —— 跳过自身，但仍递归子元素
    if (!e.fillParent && e.getWidth() > 0f && e.getHeight() > 0f) {
      tmp.set(0f, 0f);
      e.localToStageCoordinates(tmp);
      Lines.rect(tmp.x, tmp.y, e.getWidth(), e.getHeight());
    }

    // 递归子元素
    if (e instanceof Group g) {
      for (Element child : g.getChildren()) {
        drawBounds(child);
      }
    }
  }
}
