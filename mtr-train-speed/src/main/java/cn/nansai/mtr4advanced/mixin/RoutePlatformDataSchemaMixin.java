package cn.nansai.mtr4advanced.mixin;

import cn.nansai.mtr4advanced.api.RoutePlatformDoorAccess;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.generated.data.RoutePlatformDataSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.WriterBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 把「交路 × 站台」的三个字段写进/读出序列化数据（单端安装也不会破坏存档）。 */
@Mixin(RoutePlatformDataSchema.class)
public class RoutePlatformDataSchemaMixin {

	@Inject(method = "serializeData", at = @At("TAIL"))
	private void mtr4a$serializeData(WriterBase writerBase, CallbackInfo callbackInfo) {
		if ((Object) this instanceof RoutePlatformData) {
			final RoutePlatformDoorAccess access = (RoutePlatformDoorAccess) (Object) this;
			writerBase.writeDouble("mtr4aDoorDelaySeconds", access.mtr4a$getDoorDelaySeconds());
			writerBase.writeDouble("mtr4aDoorCloseEarlySeconds", access.mtr4a$getDoorCloseEarlySeconds());
			writerBase.writeBoolean("mtr4aTechnicalStop", access.mtr4a$isTechnicalStop());
		}
	}

	@Inject(method = "updateData", at = @At("TAIL"))
	private void mtr4a$updateData(ReaderBase readerBase, CallbackInfo callbackInfo) {
		if ((Object) this instanceof RoutePlatformData) {
			final RoutePlatformDoorAccess access = (RoutePlatformDoorAccess) (Object) this;
			readerBase.unpackDouble("mtr4aDoorDelaySeconds", value -> access.mtr4a$setDoorDelaySeconds((int) Math.round(value)));
			readerBase.unpackDouble("mtr4aDoorCloseEarlySeconds", value -> access.mtr4a$setDoorCloseEarlySeconds((int) Math.round(value)));
			readerBase.unpackBoolean("mtr4aTechnicalStop", access::mtr4a$setTechnicalStop);
		}
	}
}
