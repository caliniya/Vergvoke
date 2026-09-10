package caliniya.vergvoke;

import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static arc.Core.app;
import static arc.Core.assets;
import static arc.Core.atlas;
import static arc.Core.batch;
import static arc.Core.bundle;
import static arc.Core.camera;
import static arc.Core.files;
import static arc.Core.gl30;
import static arc.Core.graphics;
import static arc.Core.input;
import static arc.Core.scene;
import static arc.Core.settings;
import arc.assets.AssetManager;
import arc.graphics.Camera;
import arc.graphics.Texture;
import arc.graphics.g2d.SpriteBatch;
import arc.graphics.g2d.TextureAtlas;
import arc.scene.Scene;
import arc.util.I18NBundle;
import arc.util.Log;
import arc.util.Log.LogHandler;
import arc.util.Strings;
import arc.util.viewport.ScreenViewport;
import caliniya.vergvoke.core.UI;
import caliniya.vergvoke.ui.Fonts;

public class Init {

  public static boolean android = app.isAndroid();
  public static boolean desktop = app.isDesktop();

  public static boolean inited;

  public static Locale locale = Locale.getDefault();

  @SuppressWarnings("unused")
  public static void init() {

System.out.println("file.encoding = " + System.getProperty("file.encoding"));
System.out.println("stdout.encoding = " + System.getProperty("stdout.encoding"));
System.out.println("sun.stdout.encoding = " + System.getProperty("sun.stdout.encoding"));
System.out.println("native.encoding = " + System.getProperty("native.encoding"));

    // assets.load("");
    inited = false;
    
    //assert false : "Assert！";

    settings.setAppName("Vergvoke");

    if (desktop) {
      try {
        Writer writer = settings.getDataDirectory().child("log.txt").writer(false);
        LogHandler originalLogger = Log.logger;
        // 要过滤的标签列表(它们太多了而且一般没有用)
        String[] filteredTags = {"AndroidGraphics", "GL30"};

        // 定义时间格式
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        Log.level = Log.LogLevel.info;

        Log.logger =
            (level, text) -> {
              if (Log.level != Log.LogLevel.debug) {
                // 直接进行过滤检查
                for (String tag : filteredTags) {
                  if (text.matches("\\[" + tag + "\\].*")) {
                    return;
                  }
                }
              }

              originalLogger.log(level, text);
              try {
                // 获取当前时间
                String timestamp = dateFormat.format(new Date());

                writer.write(
                    "["
                        + timestamp
                        + "] ["
                        + Character.toUpperCase(level.name().charAt(0))
                        + "] "
                        + Log.removeColors(text)
                        + "\n");
                writer.flush();
              } catch (Exception e) {
                e.printStackTrace();
              }
            };
      } catch (Exception e) {
        // 只能这么做了
        Log.err(e);
      }
      // Log.level = Log.LogLevel.info;
      Log.info("Start-Desktop");
      Log.info("Log Level :" + Log.level);
    }

    // 基本平台信息
    Log.info("Graphics init");
    Log.info("[GL] Version:" + graphics.getGLVersion());
    Log.info("[GL] Using " + (gl30 != null ? "OpenGL 3" : "OpenGL 2"));
    if (gl30 == null) {
      Log.warn(
          "[Waning] device or video drivers do not support OpenGL 3. This will cause performance issues.");
    }
    long ram = Runtime.getRuntime().maxMemory();
    boolean gb = ram >= 1024 * 1024 * 1024;
    Log.info(
        "[RAM] Available: @ @",
        Strings.fixed(gb ? ram / 1024f / 1024 / 1024f : ram / 1024f / 1024f, 1),
        gb ? "GB" : "MB");

    bundle = I18NBundle.createBundle(files.internal("language/language"), locale);
    assets = new AssetManager();
    camera = new Camera();
    UI.camera = new Camera();
    UI.vport =
        new ScreenViewport(UI.camera);
    scene = new Scene(UI.vport);
    batch = new SpriteBatch();
    input.addProcessor(scene);
    Log.info("inited basic system");

    assets.load("sprites/white.png", Texture.class);
    assets.finishLoading();
    // 在这里阻塞加载让加载界面能用
    atlas = new TextureAtlas();
    atlas.addRegion("white", assets.get("sprites/white.png"), 1, 1, 1, 1);

    scene.resize(graphics.getWidth(), graphics.getHeight());
    UI.Loading(0f);

    Fonts.initFont();
    Fonts.loadFonts();
    assets.load("sprites/sprites.aatls", TextureAtlas.class);
    inited();
  }

  public static void inited() {
    inited = true;
  }
}
