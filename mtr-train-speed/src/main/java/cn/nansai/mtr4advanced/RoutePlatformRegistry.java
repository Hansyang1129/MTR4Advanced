package cn.nansai.mtr4advanced;

import cn.nansai.mtr4advanced.api.RoutePlatformDoorAccess;
import cn.nansai.mtr4advanced.mixin.access.RoutePlatformDataSchemaAccess;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * 运行时快速查「这条交路在这一站」的设置。
 *
 * <p>键 = 交路 id → 站台 id。交路数据在服务端加载/更新时会经过 RouteSchema#updateData，
 * 我们那时把每一站注册进来；车辆每 tick 查表拿设置，O(1)，不碰 MTR 自己的数据结构。</p>
 */
public final class RoutePlatformRegistry {

	private static final Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<RoutePlatformDoorAccess>> MAP = new Long2ObjectOpenHashMap<>();

	private RoutePlatformRegistry() {
	}

	/** 交路数据更新后调用：登记这条交路的每一站 */
	public static synchronized void register(Route route) {
		final ObjectArrayList<RoutePlatformData> routePlatforms = route.getRoutePlatforms();
		if (routePlatforms == null || routePlatforms.isEmpty()) {
			MAP.remove(route.getId());
			return;
		}
		final Long2ObjectOpenHashMap<RoutePlatformDoorAccess> forPlatforms = new Long2ObjectOpenHashMap<>();
		for (int i = 0; i < routePlatforms.size(); i++) {
			final RoutePlatformData routePlatformData = routePlatforms.get(i);
			if (routePlatformData instanceof RoutePlatformData) {
				final long platformId = ((RoutePlatformDataSchemaAccess) (Object) routePlatformData).mtr4a$getPlatformId();
				forPlatforms.put(platformId, (RoutePlatformDoorAccess) (Object) routePlatformData);
			}
		}
		MAP.put(route.getId(), forPlatforms);
	}

	/** 取某一站的设置；没有则 null（= 原版行为） */
	public static RoutePlatformDoorAccess get(long routeId, long platformId) {
		final Long2ObjectOpenHashMap<RoutePlatformDoorAccess> forPlatforms = MAP.get(routeId);
		return forPlatforms == null ? null : forPlatforms.get(platformId);
	}
}
