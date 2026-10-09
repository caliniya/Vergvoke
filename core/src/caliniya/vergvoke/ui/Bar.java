package caliniya.vergvoke.ui;

import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.ui.layout.*;

/**
 * 进度条:每帧从 {@link Floatp} 取 0~1 的比例值绘制,
 * 自带平滑动画(lerpDelta)与数值下降时的闪烁(blink)
 *
 * <p>
 */
public class Bar extends Element {

	private Floatp fraction;
	private float value, lastValue, blink, outlineRadius;
	private Color blinkColor = new Color(), outlineColor = new Color();

	public Bar(Floatp fraction, Color color) {
		this.fraction = fraction;
		lastValue = value = Mathf.clamp(fraction.get());
		setColor(color);
		this.blinkColor.set(Color.white);
	}

	/** 动态颜色版:每帧从供给者取色(比如血量低变红)。 */
	public Bar(Floatp fraction, Prov<Color> color) {
		this.fraction = fraction;
		lastValue = value = Mathf.clamp(fraction.get());
		update(() -> {
			setColor(color.get());
			this.blinkColor.set(Color.white);
		});
	}

	public void setFraction(Floatp fraction) {
		this.fraction = fraction;
	}

	/** 立即把显示值对齐到当前比例(重建/切换内容后调,避免从旧值慢慢滑过来)。 */
	public void snap() {
		lastValue = value = Mathf.clamp(fraction.get());
	}

	/** 手动触发一次闪烁。 */
	public void flash() {
		blink = 1f;
	}

	public Bar blink(Color color) {
		blinkColor.set(color);
		return this;
	}

	/** 描边(stroke 为屏幕像素,内部按 Scl 缩放)。 */
	public Bar outline(Color color, float stroke) {
		outlineColor.set(color);
		outlineRadius = Scl.scl(stroke);
		return this;
	}

	@Override
	public void draw() {
		if (fraction == null) {
			return;
		}

		float computed = Mathf.clamp(fraction.get());

		// 数值下降(受伤/消耗)时闪一下
		if (lastValue > computed) {
			blink = 1f;
			lastValue = computed;
		}

		if (Float.isNaN(lastValue)) lastValue = 0;
		if (Float.isInfinite(lastValue)) lastValue = 1f;
		if (Float.isNaN(value)) value = 0;
		if (Float.isInfinite(value)) value = 1f;
		if (Float.isNaN(computed)) computed = 0;
		if (Float.isInfinite(computed)) computed = 1f;

		blink = Mathf.lerpDelta(blink, 0f, 0.2f);
		value = Mathf.lerpDelta(value, computed, 0.15f);

		float w = getWidth();
		float h = getHeight();

		if (outlineRadius > 0) {
			Draw.color(outlineColor);
			Lines.stroke(outlineRadius * 2f);
			Lines.rect(x - outlineRadius, y - outlineRadius, w + outlineRadius * 2, h + outlineRadius * 2);
		}

		// 底色
		Draw.colorl(0.1f);
		Draw.alpha(parentAlpha);
		Fill.rect(x + w / 2f, y + h / 2f, w, h);

		// 前景:显示值 lerp 向真实值,闪烁时向 blinkColor 过渡
		Draw.color(color, blinkColor, blink);
		Draw.alpha(parentAlpha);
		Fill.rect(x + w * value / 2f, y + h / 2f, w * value, h);

		Draw.color();
	}
}
