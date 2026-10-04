package caliniya.vergvoke.ui;

import arc.*;
import arc.assets.*;
import arc.files.*;
import arc.graphics.font.*;
import arc.graphics.font.FreeTypeFontGenerator.*;
import arc.graphics.font.FreetypeFontLoader.*;
import arc.graphics.*;
import arc.graphics.gl.Gl;
import arc.graphics.Texture.*;
import arc.graphics.g2d.*;
import arc.graphics.font.Font.*;
import arc.scene.style.*;
import arc.scene.ui.layout.Scl;
import arc.struct.*;
import arc.util.*;

public class Fonts {
  public static final String fontFile = "font/normal.ttf";

  // 用于字体生成的纹理打包器
  public static PixmapPacker packer;

  // 常用的字体实例
  public static Font def; // 默认字体
  public static Font outline; // 描边字体

  // 不需要缩放的字体名称集合
  private static final ObjectSet<String> unscaled = new ObjectSet<>();

  public static void setup() {
    def.getData().markupEnabled = true;
    outline.getData().markupEnabled = true;
  }

  /** 初始化字体加载器。 应该在游戏启动早期（如 ClientLoad 阶段）调用。 */
  public static void initFont() {
    // 获取显卡支持的最大纹理尺寸
    int max = Gl.getInt(Gl.maxTextureSize);

    // 创建纹理打包器，用于将生成的字形打包到一张大纹理上以提高性能
    // 如果显卡支持，使用 4096，否则使用 2048
    packer = new PixmapPacker(max >= 4096 ? 4096 : 2048, 2048, 2, true);

    // 注册 FreeType 加载器
    Core.assets.setLoader(
        FreeTypeFontGenerator.class, new FreeTypeFontGeneratorLoader(Core.files::internal));
    Core.assets.setLoader(
        Font.class,
        null,
        new FreetypeFontLoader(Core.files::internal) {
          ObjectSet<FreeTypeFontParameter> scaled = new ObjectSet<>();

          @Override
          public Font loadSync(
              AssetManager manager,
              String fileName,
              Fi file,
              FreeTypeFontLoaderParameter parameter) {
            // 特殊处理：如果是描边字体，调整字间距以防止描边重叠
            if (fileName.equals("outline")) {
              parameter.fontParameters.borderWidth = Scl.scl(2f);
              parameter.fontParameters.spaceX -= parameter.fontParameters.borderWidth;
            }

            // 处理高分屏缩放 (Scl.scl)
            // 确保同一个参数配置只被缩放一次
            if (!scaled.contains(parameter.fontParameters) && !unscaled.contains(fileName)) {
              parameter.fontParameters.size = (int) (Scl.scl(parameter.fontParameters.size));
              scaled.add(parameter.fontParameters);
            }

            // 设置纹理过滤为线性，使字体缩放更平滑
            parameter.fontParameters.magFilter = TextureFilter.linear;
            parameter.fontParameters.minFilter = TextureFilter.linear;
            parameter.fontParameters.packer = packer;

            return super.loadSync(manager, fileName, file, parameter);
          }
        });
  }

  /** 将字体添加到资源加载队列。 调用此方法后，需要等待 Core.assets.finishLoading() 或 update() 完成。 */
  public static void loadFonts() {
    // 基础字体参数
    FreeTypeFontParameter param =
        new FreeTypeFontParameter() {
          {
            size = 24; // 基础字号

            this.genMipMaps = true;
            this.hinting = FreeTypeFontGenerator.Hinting.none;
            this.gamma = 2f;

            shadowColor = Color.darkGray;
            shadowOffsetY = 1;

            incremental = true; // 增量加载：这对中文等字符集很大的语言至关重要
          }
        };

    // 加载默认字体
    Core.assets.load("default", Font.class, new FreeTypeFontLoaderParameter(fontFile, param))
            .loaded =
        f -> {
          Fonts.def = f;
        };

    // 加载描边字体 (复用 param，但在 loadSync 中会被修改添加边框)
    FreeTypeFontParameter outlineParam =
        new FreeTypeFontParameter() {
          {
            size = 24;
            borderColor = Color.darkGray;
            incremental = true;
          }
        };

    Core.assets.load("outline", Font.class, new FreeTypeFontLoaderParameter(fontFile, outlineParam))
            .loaded =
        t -> Fonts.outline = t;
  }

  /** 获取字体的单个字符作为 Drawable (用于 UI 图标等)。 如果字符不存在，会输出警告并返回默认字符。 */
  public static TextureRegionDrawable getGlyph(Font font, char glyph) {
    Glyph found = font.getData().getGlyph(glyph);
    if (found == null) {
      // 如果找不到，尝试找一个默认字符，比如 '?' 或 'F'
      found = font.getData().getGlyph('?');
      if (found == null) return new TextureRegionDrawable(); // 防止崩溃
    }
    Glyph g = found;

    float size = Math.max(g.width, g.height);

    // 创建一个自定义的 Drawable 来绘制这个字形
    TextureRegionDrawable draw =
        new TextureRegionDrawable(
            new TextureRegion(font.getRegion().texture, g.u, g.v2, g.u2, g.v)) {
          @Override
          public void draw(float x, float y, float width, float height) {
            Draw.color(Tmp.c1.set(tint).mul(Draw.getColor()).toFloatBits());
            float cx = x + width / 2f - g.width / 2f, cy = y + height / 2f - g.height / 2f;
            Draw.rect(region, (int) cx + g.width / 2f, (int) cy + g.height / 2f, g.width, g.height);
          }

          @Override
          public void draw(
              float x,
              float y,
              float originX,
              float originY,
              float width,
              float height,
              float scaleX,
              float scaleY,
              float rotation) {
            width *= scaleX;
            height *= scaleY;
            Draw.color(Tmp.c1.set(tint).mul(Draw.getColor()).toFloatBits());
            float cx = x + width / 2f - g.width / 2f, cy = y + height / 2f - g.height / 2f;
            Draw.rect(
                region,
                (int) cx + g.width / 2f,
                (int) cy + g.height / 2f,
                g.width * scaleX,
                g.height * scaleY,
                g.width / 2f,
                g.height / 2f,
                rotation);
          }

          @Override
          public float imageSize() {
            return size;
          }
        };

    draw.setMinWidth(size);
    draw.setMinHeight(size);
    return draw;
  }
}
