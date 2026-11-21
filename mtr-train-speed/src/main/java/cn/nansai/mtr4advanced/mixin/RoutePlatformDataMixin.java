package cn.nansai.mtr4advanced.mixin;

import cn.nansai.mtr4advanced.api.RoutePlatformDoorAccess;
import org.mtr.core.data.RoutePlatformData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 给「交路 × 站台」加三个开关门字段（秒，0 = 原版）。 */
@Mixin(RoutePlatformData.class)
public class RoutePlatformDataMixin implements RoutePlatformDoorAccess {

	@Unique
	private int mtr4a$doorDelaySeconds;

	@Unique
	private int mtr4a$doorCloseEarlySeconds;

	@Unique
	private boolean mtr4a$technicalStop;

	@Override
	public int mtr4a$getDoorDelaySeconds() {
		return mtr4a$doorDelaySeconds;
	}

	@Override
	public void mtr4a$setDoorDelaySeconds(int seconds) {
		mtr4a$doorDelaySeconds = Math.max(0, Math.min(3600, seconds));
	}

	@Override
	public int mtr4a$getDoorCloseEarlySeconds() {
		return mtr4a$doorCloseEarlySeconds;
	}

	@Override
	public void mtr4a$setDoorCloseEarlySeconds(int seconds) {
		mtr4a$doorCloseEarlySeconds = Math.max(0, Math.min(3600, seconds));
	}

	@Override
	public boolean mtr4a$isTechnicalStop() {
		return mtr4a$technicalStop;
	}

	@Override
	public void mtr4a$setTechnicalStop(boolean value) {
		mtr4a$technicalStop = value;
	}
}
