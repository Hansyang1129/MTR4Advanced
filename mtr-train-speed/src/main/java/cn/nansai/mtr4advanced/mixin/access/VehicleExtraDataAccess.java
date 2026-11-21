package cn.nansai.mtr4advanced.mixin.access;

import org.mtr.core.data.VehicleExtraData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** VehicleExtraData#openDoors/closeDoors 是 protected，跨包调用需要 @Invoker。 */
@Mixin(VehicleExtraData.class)
public interface VehicleExtraDataAccess {

	@Invoker("openDoors")
	void mtr4a$invokeOpenDoors();

	@Invoker("closeDoors")
	void mtr4a$invokeCloseDoors();
}
