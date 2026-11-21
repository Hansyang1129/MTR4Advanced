package cn.nansai.mtr4advanced.mixin;

import cn.nansai.mtr4advanced.RoutePlatformRegistry;
import cn.nansai.mtr4advanced.api.RoutePlatformDoorAccess;
import cn.nansai.mtr4advanced.mixin.access.VehicleExtraDataAccess;
import cn.nansai.mtr4advanced.mixin.access.VehicleSchemaAccess;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 按「交路 × 站台」的设置在停站时控制车门：
 *
 * <ul>
 *   <li>延迟开门：到站后不足 N 秒不开门</li>
 *   <li>提前关门：发车前 N 秒就关门（停站时间不变）</li>
 *   <li>技术停车：这一站完全不开门</li>
 * </ul>
 *
 * <p>拦截点是 MTR 自己在 simulateStopped 里调 VehicleExtraData#openDoors 的那一处 ——
 * 那是「停稳在站台且停站时间 &gt; 0」的分支；没配设置的站直接走原版。</p>
 */
@Mixin(Vehicle.class)
public abstract class VehicleDoorMixin {

	@Unique
	private int mtr4a$currentIndex;

	@Inject(method = "simulateStopped", at = @At("HEAD"))
	private void mtr4a$captureIndex(long millisElapsed, ObjectArrayList vehiclePositions, int currentIndex, CallbackInfo callbackInfo) {
		mtr4a$currentIndex = currentIndex;
	}

	@Redirect(method = "simulateStopped", at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/VehicleExtraData;openDoors()V"))
	private void mtr4a$openDoors(VehicleExtraData vehicleExtraData) {
		boolean doorOpened = false;
		try {
			final Vehicle self = (Vehicle) (Object) this;
			final PathData here = Utilities.getElement(self.vehicleExtraData.immutablePath, mtr4a$currentIndex);
			final long platformId = here == null ? 0 : here.getSavedRailBaseId();
			final RoutePlatformDoorAccess settings = RoutePlatformRegistry.get(self.vehicleExtraData.getThisRouteId(), platformId);

			if (settings != null) {
				if (settings.mtr4a$isTechnicalStop()) {
					return;
				}
				final long elapsed = ((VehicleSchemaAccess) (Object) this).mtr4a$getElapsedDwellTime();
				final PathData previous = mtr4a$currentIndex <= 0 ? null : Utilities.getElement(self.vehicleExtraData.immutablePath, mtr4a$currentIndex - 1);
				final long totalDwell = previous == null ? 0 : previous.getDwellTime();
				final long closeEarlyMillis = settings.mtr4a$getDoorCloseEarlySeconds() * 1000L;
				if (closeEarlyMillis > 0 && totalDwell > 0 && elapsed >= totalDwell - closeEarlyMillis) {
					((VehicleExtraDataAccess) vehicleExtraData).mtr4a$invokeCloseDoors();
					return;
				}
				if (elapsed < settings.mtr4a$getDoorDelaySeconds() * 1000L) {
					return;
				}
			}
			doorOpened = true;
		} catch (Throwable ignored) {
			// 出错就退回原版行为
		}
		if (doorOpened) {
			((VehicleExtraDataAccess) vehicleExtraData).mtr4a$invokeOpenDoors();
		}
	}
}
