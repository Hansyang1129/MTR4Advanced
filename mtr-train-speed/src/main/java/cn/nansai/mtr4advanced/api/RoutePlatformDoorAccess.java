package cn.nansai.mtr4advanced.api;

/**
 * 「交路 × 站台」这一站的开关门行为。
 *
 * <p>颗粒度是 RoutePlatformData（某条交路停在某个站台的那一站），不是整个站台 ——
 * 同一个站台可能被多条交路使用，各自的行为互不影响。</p>
 */
public interface RoutePlatformDoorAccess {

	/** 到站后延迟多少秒再开门（0 = 原版） */
	int mtr4a$getDoorDelaySeconds();

	void mtr4a$setDoorDelaySeconds(int seconds);

	/** 发车前提前多少秒关门（0 = 原版） */
	int mtr4a$getDoorCloseEarlySeconds();

	void mtr4a$setDoorCloseEarlySeconds(int seconds);

	/** 技术停车：照常停站，但不开门 */
	boolean mtr4a$isTechnicalStop();

	void mtr4a$setTechnicalStop(boolean value);
}
