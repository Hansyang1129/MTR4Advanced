package cn.nansai.mtr4advanced;

/**
 * 原版轨道限速档位（对应 MTR 的 RailType.speedLimit）。
 *
 * <p>MTR 的限速是「轨道类型」的固有属性，且 RailSchema 里 speedLimit1/2 是 final，
 * 所以改限速只能「重建一个 Rail」。{@link Override} 是一个线程本地的临时覆盖，
 * 只在我们主动调用 {@code Rail.copy(...)} 时生效 —— 运行时的车辆/信号逻辑完全看不到它。</p>
 */
public final class RailSpeedLevels {

	/** 与 MTR RailType 的档位一致：WOODEN 20 ... DIAMOND 300 */
	public static final int[] LEVELS = {20, 40, 60, 80, 100, 120, 140, 160, 200, 300};

	private static final ThreadLocal<int[]> OVERRIDE = new ThreadLocal<>();

	private RailSpeedLevels() {
	}

	/** 设置一次覆盖：oneWay 为 true 时正向(0)保持 0，只改反向。 */
	public static void set(int level, boolean oneWay) {
		OVERRIDE.set(oneWay ? new int[]{0, level} : new int[]{level, level});
	}

	public static void clear() {
		OVERRIDE.remove();
	}

	/** @return 当前线程的覆盖值，没有则 null */
	public static int[] get() {
		return OVERRIDE.get();
	}

	/** 在档位表里找最接近的档位下标（找不到返回 0） */
	public static int indexOf(int kilometersPerHour) {
		for (int i = 0; i < LEVELS.length; i++) {
			if (LEVELS[i] == kilometersPerHour) {
				return i;
			}
		}
		return 0;
	}

	public static int next(int kilometersPerHour) {
		return LEVELS[Math.min(LEVELS.length - 1, indexOf(kilometersPerHour) + 1)];
	}

	public static int previous(int kilometersPerHour) {
		return LEVELS[Math.max(0, indexOf(kilometersPerHour) - 1)];
	}
}
