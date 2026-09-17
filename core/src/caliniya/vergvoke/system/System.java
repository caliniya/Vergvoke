package caliniya.vergvoke.system;

import arc.Core;
import arc.Events;
import arc.util.Log;
import arc.util.Threads;
import caliniya.vergvoke.base.type.EventType;

/**
 * 代表游戏或应用中的一个独立逻辑模块。
 *
 * <p>两种运行模式：
 *
 * <ul>
 *   <li>主线程运行：由外部手动调用 {@link #update()} 或 {@link #update(float)}。
 *   <li>独立线程运行：在 {@link #init(boolean, boolean)} 中开启独立线程循环，自带帧率控制（默认 60 TPS）和 TPS 统计。
 * </ul>
 *
 * @param <T> 自引用泛型类型，允许子类方法返回自身类型以支持链式调用。
 */
public abstract class System<T extends System<T>> implements Comparable<System<?>> {

  /** 系统是否已初始化的标志，防止重复初始化。 */
  public boolean inited = false;

  /** 系统的执行优先级索引，数值越小优先级越高（用于排序）。 */
  public int index = 0;

  /** 是否在独立线程中运行。 */
  protected boolean isThreaded = false;

  /** 系统是否可以被暂停。如果为 false，系统将忽略游戏暂停事件持续运行。 */
  protected boolean isPausable = true;

  /** 线程运行状态标志，使用 volatile 保证多线程可见性。 */
  private volatile boolean threadRunning = false;

  /** 暂停状态标志，使用 volatile 保证多线程可见性。 */
  protected volatile boolean paused = false;

  /** 暂停锁对象，用于线程的等待/唤醒机制。 */
  private final Object pauseLock = new Object();

  /** 系统运行的线程实例。 */
  private Thread systemThread;

  /** 目标帧时间（纳秒），默认为 16.6ms（约 60 TPS）。 */
  protected long targetNs = 16_666_666L;

  // 给一些特殊系统所使用的特殊标记
  public volatile boolean task = false;

  // ========== 公有测量变量 ==========

  /** 实时 TPS（每秒刻数），表示上一秒内的实际更新次数。 */
  public float tps = 0f;

  /** 平滑 TPS，基于过去 60 个采样值的移动平均值。 */
  public float smoothedTps = 0f;

  // ===================================

  /**
   * 使用默认配置初始化系统。
   *
   * <p>默认为线程模式取决于 {@link #isThreaded}，且默认可被暂停。
   *
   * @return 当前系统实例。
   */
  public T init() {
    return init(this.isThreaded);
  }

  /**
   * 初始化系统，默认为可暂停模式。
   *
   * @param runInThread 是否在独立线程中运行。
   * @return 当前系统实例。
   */
  public T init(boolean runInThread) {
    return init(runInThread, true);
  }

  /**
   * 初始化系统。
   *
   * <p>如果系统未初始化，将设置运行模式和暂停策略。 如果指定为线程模式，将启动独立线程。
   *
   * @param runInThread 是否在独立线程中运行。
   * @param pausable 是否响应游戏暂停事件。如果为 false，线程将在游戏暂停时继续运行。
   * @return 当前系统实例。
   */
  public T init(boolean runInThread, boolean pausable) {
    if (inited) return (T) this;

    this.isThreaded = runInThread;
    this.isPausable = pausable;
    this.inited = true;

    if (isThreaded) {
      startThread();
      Events.run(EventType.events.ThreadedStop, () -> stopThread());
      // Events.on(EventType.events.)
    }

    // 只有可暂停的系统才注册暂停事件监听
    if (this.isPausable) {
      Events.on(EventType.GamePause.class, event -> setPaused(event.pause));
    }

    return (T) this;
  }

  /**
   * 系统逻辑更新方法（带时间增量）。驱动方（主循环 / 系统线程）统一调用本方法。
   *
   * <p>默认转发到 {@link #update()}——所以系统只实现 {@code update()} 或 {@code update(float)} 之一都行；
   * 两个都实现时以 {@code update(float)} 为准（需要的话自己调 {@code super.update(delta)}）。
   *
   * @param delta 以 60TPS 为基准的帧时间增量（1.0 = 理想一帧；与 {@code arc.util.Time.delta} 同口径）。
   */
  public void update(float delta) {
    update();
  }

  /** 系统逻辑更新方法（无参数）。 */
  public void update() {}

  /** 销毁系统，释放资源。 */
  public void dispose() {
    stopThread();
  }

  /**
   * 设置系统的暂停状态。
   *
   * <p>仅对标记为 {@code isPausable = true} 的系统生效。 如果从暂停恢复，会重置计时器并唤醒等待的线程。
   *
   * @param paused true 为暂停，false 为恢复。
   */
  public void setPaused(boolean paused) {
    // 如果系统不可暂停，直接忽略设置请求
    if (!isPausable) return;

    synchronized (pauseLock) {
      this.paused = paused;
      if (!paused) {
        lastLoopTime = java.lang.System.nanoTime();
        pauseLock.notifyAll();
      }
    }
  }

  /**
   * 获取系统是否处于暂停状态。
   *
   * @return 如果暂停返回 true。
   */
  public boolean isPaused() {
    return paused;
  }

  // ========== 内部计时与统计变量 ==========

  private long lastLoopTime = 0;
  private int tickCounter = 0;
  private long lastTPSUpdate = 0;
  private final float[] tpsSamples = new float[60];
  private int tpsIndex = 0;
  private int tpsFilled = 0;

  // =============================

  /** 启动独立系统线程。 */
  private void startThread() {
    if (threadRunning) return;

    threadRunning = true;

    systemThread =
        Threads.daemon(
            "System-" + this.getClass().getSimpleName(),
            () -> {
              Log.info("thread started: @ ", this.getClass().getSimpleName(), isPausable);

              lastLoopTime = java.lang.System.nanoTime();
              lastTPSUpdate = lastLoopTime;

              while (threadRunning) {
                try {
                  // --- 暂停控制 ---
                  // 仅当系统可暂停且处于暂停状态时，线程才会等待
                  if (isPausable) {
                    synchronized (pauseLock) {
                      while (paused && threadRunning) {
                        pauseLock.wait();
                      }
                    }
                  }

                  if (!threadRunning) break;

                  // --- 帧时间计算 ---
                  long now = java.lang.System.nanoTime();
                  long elapsedNs = now - lastLoopTime;
                  lastLoopTime = now;

                  float delta = (float) (elapsedNs / (double) targetNs);
                  if (delta > 4f) delta = 4f;

                  try {
                    update(delta);
                    tickCounter++;
                  } catch (Exception e) {
                    Log.err("Error in thread: @", this.getClass().getSimpleName() + "  " + e);
                    // Threads.throwAppException(e);
                  }

                  // --- TPS 统计 ---
                  if (now - lastTPSUpdate >= 1_000_000_000L) {
                    tps = tickCounter;
                    tickCounter = 0;
                    lastTPSUpdate = now;
                    updateSmoothedTPS(tps);
                  }

                  // --- 帧率控制 ---
                  long endTime = java.lang.System.nanoTime();
                  long sleepNs = targetNs - (endTime - now);

                  if (sleepNs > 0) {
                    Thread.sleep(sleepNs / 1_000_000, (int) (sleepNs % 1_000_000));
                  } else {
                    Thread.yield();
                  }

                } catch (InterruptedException e) {
                  threadRunning = false;
                  Thread.currentThread().interrupt();
                  Log.err(e);
                } catch (Exception e) {
                  Log.err("Critical error in system loop: @", this.getClass().getSimpleName(), e);
                }
              }
              Log.info("thread stopped: @", this.getClass().getSimpleName());
            });
  }

  /** 更新平滑 TPS 值。 */
  private void updateSmoothedTPS(float tps) {
    tpsSamples[tpsIndex] = tps;
    tpsIndex = (tpsIndex + 1) % tpsSamples.length;
    if (tpsFilled < tpsSamples.length) tpsFilled++;

    float sum = 0f;
    for (int i = 0; i < tpsFilled; i++) {
      sum += tpsSamples[i];
    }
    smoothedTps = sum / tpsFilled;
  }

  /** 停止独立系统线程。 */
  private void stopThread() {
    threadRunning = false;
    if (systemThread != null) {
      systemThread.interrupt();
      try {
        systemThread.join(100);
      } catch (Exception ignored) {
      }
      systemThread = null;
    }
  }

  @Override
  public int compareTo(System<?> other) {
    return this.index - other.index;
  }
}
