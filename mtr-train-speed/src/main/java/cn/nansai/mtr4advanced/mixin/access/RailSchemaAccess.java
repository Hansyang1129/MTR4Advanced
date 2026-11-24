package cn.nansai.mtr4advanced.mixin.access;

import org.mtr.core.generated.data.RailSchema;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 原始限速字段（不经过 reversePositions 翻转）。
 *
 * <p>MTR 用 <b>speedLimit1 == 0</b> 表示单向轨道；而 getSpeedLimitKilometersPerHour(reversed)
 * 会按 reversePositions 翻转，拿它判断单向会误判反向建造的轨道。</p>
 */
@Mixin(RailSchema.class)
public interface RailSchemaAccess {

	@Accessor("speedLimit1")
	long mtr4a$getRawSpeedLimit1();

	@Accessor("speedLimit2")
	long mtr4a$getRawSpeedLimit2();
}
