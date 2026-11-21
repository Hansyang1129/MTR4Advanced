package cn.nansai.mtr4advanced.mixin.access;

import org.mtr.core.generated.data.RoutePlatformDataSchema;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RoutePlatformDataSchema.class)
public interface RoutePlatformDataSchemaAccess {

	@Accessor("platformId")
	long mtr4a$getPlatformId();
}
