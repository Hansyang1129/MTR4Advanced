package cn.nansai.mtr4advanced;

import cn.nansai.mtr4advanced.api.SidingMaxSpeedAccess;
import cn.nansai.mtr4advanced.mixin.access.VehicleAccess;
import cn.nansai.mtr4advanced.mixin.access.VehicleSchemaAccess;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;

import java.util.List;

/**
 * 限速核心逻辑。
 *
 * 现实规则（本模组实现）：
 * - 限速【提高】：整列车（车尾）通过提速点后才允许加速 -> 取列车占用区间 [车尾, 车头] 内的最低线路限速。
 * - 限速【降低】：车头一到达降速点就开始制动 -> 沿用 MTR 原本的 Siding#getUpcomingSlowerSpeed 前瞻制动曲线。
 * - 列车最高时速：侧线里配置。默认硬顶，勾选「跟随晚点浮动」后随晚点增益一起上浮。
 */
public final class SpeedLogic {

    private SpeedLogic() {
    }

    /** km/h -> m/ms（与 MTR 内部 Utilities 同一换算） */
    public static double kilometersPerHourToMetersPerMillisecond(double kilometersPerHour) {
        return kilometersPerHour / 3600.0;
    }

    /** 该列车的硬上限（m/ms），没有限制时返回 Double.MAX_VALUE */
    public static double getTrainMaxSpeedMetersPerMillisecond(Vehicle vehicle) {
        double kilometersPerHour = 0;
        Siding siding = ((VehicleAccess) vehicle).mtr4a$getSiding();
        if (siding != null) {
            kilometersPerHour = ((SidingMaxSpeedAccess) (Object) siding).mtr4a$getTrainMaxSpeedKilometersPerHour();
        }
        if (kilometersPerHour <= 0) {
            kilometersPerHour = Config.defaultMaxSpeedKilometersPerHour;
        }
        return kilometersPerHour > 0 ? kilometersPerHourToMetersPerMillisecond(kilometersPerHour) : Double.MAX_VALUE;
    }

    /** 该列车的最高时速是否跟晚点增益一起上浮 */
    public static boolean shouldFollowDeviation(Vehicle vehicle) {
        Siding siding = ((VehicleAccess) vehicle).mtr4a$getSiding();
        return siding != null && ((SidingMaxSpeedAccess) (Object) siding).mtr4a$getMaxSpeedFollowsDeviation();
    }

    /**
     * 计算加速/巡航目标速度（对应原版 pathData.getSpeedLimitMetersPerMillisecond()）。
     *
     * @param rawLimit 原版取值（车头所在轨段的线路限速）
     */
    public static double getAccelerationTarget(Vehicle vehicle, double rawLimit) {
        Config.load();
        double limit = rawLimit;
        if (Config.tailControlledAcceleration) {
            limit = Math.min(limit, getOccupiedMinimumSpeedLimit(vehicle, limit));
        }
        return applyHardCap(vehicle, limit);
    }

    /**
     * 计算减速目标速度（对应原版 Siding#getUpcomingSlowerSpeed 的返回值）。
     * 返回值 < 0 表示“前方没有更低限速”，沿用原版语义。
     */
    public static double getDecelerationTarget(Vehicle vehicle, double upcomingSlowerSpeed) {
        Config.load();
        if (upcomingSlowerSpeed < 0) {
            return upcomingSlowerSpeed;
        }
        if (Config.tailControlledAcceleration) {
            upcomingSlowerSpeed = Math.min(upcomingSlowerSpeed, getOccupiedMinimumSpeedLimit(vehicle, upcomingSlowerSpeed));
        }
        return applyHardCap(vehicle, upcomingSlowerSpeed);
    }

    /**
     * 列车当前占用区间 [车尾, 车头] 内的最低线路限速。
     * 车尾 = railProgress - 列车总长，车头 = railProgress（MTR 内部就是这么定义占用区间的）。
     */
    public static double getOccupiedMinimumSpeedLimit(Vehicle vehicle, double fallback) {
        if (vehicle.vehicleExtraData == null) {
            return fallback;
        }
        final ObjectImmutableList<PathData> path = vehicle.vehicleExtraData.immutablePath;
        if (path == null || path.isEmpty()) {
            return fallback;
        }
        final double head = ((VehicleSchemaAccess) vehicle).mtr4a$getRailProgress();
        final double tail = head - vehicle.vehicleExtraData.getTotalVehicleLength();
        return occupiedMinimumSpeedLimit(path, tail, head, fallback);
    }

    /**
     * 占用区间 [tail, head] 内的最低线路限速（与具体列车无关，时刻表推算也用这个）。
     */
    public static double occupiedMinimumSpeedLimit(List<PathData> path, double tail, double head, double fallback) {
        if (path == null || path.isEmpty()) {
            return fallback;
        }
        int startIndex = indexOfStartDistance(path, tail);
        int endIndex = indexOfStartDistance(path, head);
        if (endIndex < 0) {
            return fallback;
        }
        if (startIndex < 0) {
            startIndex = 0;
        }
        double result = Double.MAX_VALUE;
        for (int i = startIndex; i <= endIndex && i < path.size(); i++) {
            final PathData pathData = path.get(i);
            if (pathData.getEndDistance() < tail) {
                continue;
            }
            if (pathData.getStartDistance() > head) {
                break;
            }
            result = Math.min(result, pathData.getSpeedLimitMetersPerMillisecond());
        }
        return result == Double.MAX_VALUE ? fallback : result;
    }

    /**
     * 把列车最高时速套上去。
     * 原版在算出目标速度后会再乘上晚点追赶系数 deviationSpeedAdjustment（默认最多 +25%）。
     * - 硬顶模式（默认）：先除后乘，最终目标速度 = min(限速, 列车最高时速)
     * - 浮动模式：最终目标速度 = min(限速, 列车最高时速) × 晚点增益，最高可到 1.25 倍
     */
    private static double applyHardCap(Vehicle vehicle, double limit) {
        final double cap = getTrainMaxSpeedMetersPerMillisecond(vehicle);
        if (cap == Double.MAX_VALUE) {
            return limit;
        }
        if (shouldFollowDeviation(vehicle)) {
            return Math.min(limit, cap);
        }
        final double adjustment = ((VehicleAccess) vehicle).mtr4a$getDeviationSpeedAdjustment();
        final double safeAdjustment = adjustment > 0 ? adjustment : 1.0;
        return Math.min(limit, cap / safeAdjustment);
    }

    /** 二分查找：最后一个 startDistance <= value 的轨段下标，找不到返回 -1 */
    private static int indexOfStartDistance(List<PathData> path, double value) {
        int low = 0;
        int high = path.size() - 1;
        int result = -1;
        while (low <= high) {
            final int mid = (low + high) >>> 1;
            if (path.get(mid).getStartDistance() <= value) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }
}
