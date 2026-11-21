package cn.nansai.mtr4advanced.mixin;

import cn.nansai.mtr4advanced.RailSpeedLevels;
import org.mtr.core.data.Rail;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 允许「重建 Rail」时覆盖限速。
 *
 * <p>MTR 的 Rail 在构造器里把 speedLimit1/2 算成 final 的 speedLimit1/2MetersPerMillisecond，
 * 没有 setter。所以改限速 = 用同一个 Rail.copy(...) 重建一次，只在重建期间由
 * {@link RailSpeedLevels} 给出新档位。没有覆盖值时这两个方法返回原值，行为与未安装本模组一致。</p>
 *
 * <p>只匹配 14 参数的私有构造器（描述符里有两个 long），ReaderBase 构造器不受影响。</p>
 *
 * <p>注意：构造器 HEAD 处的 @ModifyVariable <b>必须是 static</b> —— 那时候 this 还没初始化，
 * 非 static handler 会让 Mixin 直接拒绝整个 RailMixin（日志：handler before super() invocation must be static）。</p>
 */
@Mixin(Rail.class)
public abstract class RailMixin {

	@ModifyVariable(
			method = "<init>(Lorg/mtr/core/data/Position;Lorg/mtr/core/tool/Angle;Lorg/mtr/core/data/Position;Lorg/mtr/core/tool/Angle;Lorg/mtr/core/data/Rail$Shape;DLorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;JJZZZZZZLorg/mtr/core/data/TransportMode;)V",
			at = @At("HEAD"),
			index = 9
	)
	private static long mtr4a$speedLimit1(long speedLimit1) {
		final int[] override = RailSpeedLevels.get();
		return override == null ? speedLimit1 : override[0];
	}

	@ModifyVariable(
			method = "<init>(Lorg/mtr/core/data/Position;Lorg/mtr/core/tool/Angle;Lorg/mtr/core/data/Position;Lorg/mtr/core/tool/Angle;Lorg/mtr/core/data/Rail$Shape;DLorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;JJZZZZZZLorg/mtr/core/data/TransportMode;)V",
			at = @At("HEAD"),
			index = 11
	)
	private static long mtr4a$speedLimit2(long speedLimit2) {
		final int[] override = RailSpeedLevels.get();
		return override == null ? speedLimit2 : override[1];
	}
}
