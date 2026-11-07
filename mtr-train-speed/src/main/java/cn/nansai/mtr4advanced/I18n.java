package cn.nansai.mtr4advanced;

import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.TextHelper;

/**
 * GUI 文案统一入口。
 *
 * <p>走 MTR 自己的语言系统：{@link TextHelper#translatable(String, Object...)} 会生成一个
 * 可翻译组件，真正取词发生在客户端渲染时，因此玩家切换语言后会立刻生效（无需重进界面）。</p>
 *
 * <p>语言键定义在 jar 内的 {@code assets/mtr4advanced/lang/<语言>.json}；Minecraft 会合并
 * 所有模组资源包里的 lang 文件，所以键名只要够独特（这里统一 {@code mtr4a.} 前缀）就不会冲突。</p>
 *
 * <p>注意：本类必须放在 {@code cn.nansai.mtr4advanced.mixin} 包【之外】——
 * Mixin 会把该包下所有类当作 mixin 处理。</p>
 */
public final class I18n {

	private I18n() {
	}

	/** 可翻译文本（用于 drawText / 按钮构造器等接受 MutableText 的地方）。 */
	public static MutableText text(String key, Object... args) {
		return TextHelper.translatable(key, args);
	}

	/** 可翻译文本的不可变形式（用于只接受 Text 的 API，例如 setMessage2）。 */
	public static Text component(String key, Object... args) {
		return Text.cast(TextHelper.translatable(key, args));
	}
}
