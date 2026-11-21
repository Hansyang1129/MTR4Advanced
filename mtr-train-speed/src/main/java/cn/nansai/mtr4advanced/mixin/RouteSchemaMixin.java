package cn.nansai.mtr4advanced.mixin;

import cn.nansai.mtr4advanced.RoutePlatformRegistry;
import org.mtr.core.data.Route;
import org.mtr.core.generated.data.RouteSchema;
import org.mtr.core.serializer.ReaderBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 交路数据（加载或收到更新包）解析完成后，把每一站登记到运行时注册表。 */
@Mixin(RouteSchema.class)
public class RouteSchemaMixin {

	@Inject(method = "updateData", at = @At("TAIL"))
	private void mtr4a$updateData(ReaderBase readerBase, CallbackInfo callbackInfo) {
		if ((Object) this instanceof Route) {
			try {
				RoutePlatformRegistry.register((Route) (Object) this);
			} catch (Throwable ignored) {
			}
		}
	}
}
