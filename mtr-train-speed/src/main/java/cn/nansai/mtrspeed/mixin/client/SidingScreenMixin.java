package cn.nansai.mtrspeed.mixin.client;

import cn.nansai.mtrspeed.api.SidingMaxSpeedAccess;
import cn.nansai.mtrspeed.mixin.access.SavedRailScreenBaseAccess;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Siding;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.CheckboxWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.InitClient;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.screen.SidingScreen;
import org.mtr.mod.screen.WidgetShorterSlider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 侧线界面加一块配置区，占四行：
 *
 * <pre>
 *   行 1：列车最高时速 km/h            [ 输入框 ]
 *   行 2：0 = 不限制，留空同 0
 *   行 3：[x] 跟随晚点浮动
 *   行 4：[ 复制到车场其他侧线 ]
 * </pre>
 *
 * 布局全部是相对的：
 * - 列：标签贴左边距 20，控件跟 MTR 自己那列对齐（20 + textWidth + 2）
 * - 行：以 MTR 最后一个控件 {@code sliderDwellTimeSec} 的底部为基准往下排，
 *   MTR 改布局（语言变化导致 textWidth 变化、控件增删）时这边自动跟着走，不写死坐标。
 */
@Mixin(SidingScreen.class)
public class SidingScreenMixin {

    @Unique
    private static final String MTRSPEED$LABEL = "列车最高时速 km/h";

    @Unique
    private static final String MTRSPEED$HINT = "0 = 不限制，留空同 0";

    @Unique
    private static final String MTRSPEED$FOLLOW_LABEL = "跟随晚点浮动";

    @Unique
    private static final String MTRSPEED$COPY_LABEL = "复制到车场其他侧线";

    /** 「复制到车场其他侧线」按钮宽度 */
    @Unique
    private static final int MTRSPEED$BUTTON_WIDTH = 140;

    /** 单次更新包最多塞多少条侧线（Fabric 自定义 payload 有单包上限，防止大车场整包被拒） */
    @Unique
    private static final int MTRSPEED$BATCH_SIZE = 40;

    /** 左边距，跟 MTR 自己的控件一致 */
    @Unique
    private static final int MTRSPEED$LEFT = 20;

    /** 单行控件高度 */
    @Unique
    private static final int MTRSPEED$ROW_HEIGHT = 20;

    /** 说明行高度（比控件行矮一点，省地方） */
    @Unique
    private static final int MTRSPEED$HINT_HEIGHT = 12;

    /** 和上一块 MTR 控件之间的间隙 */
    @Unique
    private static final int MTRSPEED$TOP_GAP = 4;

    /** MTR 的 drawText 传的是文字【顶部】，文字高 8；控件垂直居中要 +6 */
    @Unique
    private static final int MTRSPEED$TEXT_CENTER_OFFSET = 6;

    @Unique
    private TextFieldWidgetExtension mtrspeed$textFieldMaxSpeed;

    @Unique
    private CheckboxWidgetExtension mtrspeed$checkboxFollowDeviation;

    @Unique
    private ButtonWidgetExtension mtrspeed$buttonCopyToDepot;

    @Inject(method = "init2", at = @At("TAIL"))
    private void mtrspeed$init(CallbackInfo callbackInfo) {
        final SavedRailScreenBaseAccess access = (SavedRailScreenBaseAccess) this;
        if (!access.mtrspeed$showScheduleControls()) {
            return;
        }
        final Siding siding = (Siding) access.mtrspeed$getSavedRailBase();
        final int row1Y = mtrspeed$row1Y();
        final int controlX = MTRSPEED$LEFT + access.mtrspeed$getTextWidth() + 2;

        if (mtrspeed$textFieldMaxSpeed == null) {
            mtrspeed$textFieldMaxSpeed = new TextFieldWidgetExtension(0, 0, 0, MTRSPEED$ROW_HEIGHT, 4, TextCase.DEFAULT, "\\D", null);
        }
        final double current = siding == null
                ? 0
                : ((SidingMaxSpeedAccess) (Object) siding).mtrspeed$getTrainMaxSpeedKilometersPerHour();
        // 输入框只接受数字，但万一存过 80.5 这类值，截断成 "80" 会在关闭时被写回成 80。
        // 整数原样显示，小数保留最多 3 位。
        if (mtrspeed$textFieldMaxSpeed.getText2().isEmpty()) {
            mtrspeed$textFieldMaxSpeed.setText2(mtrspeed$format(current));
        }
        mtrspeed$textFieldMaxSpeed.setX2(controlX);
        mtrspeed$textFieldMaxSpeed.setY2(row1Y);
        mtrspeed$textFieldMaxSpeed.setWidth2(60);
        ((ScreenExtension) (Object) this).addChild(new ClickableWidget(mtrspeed$textFieldMaxSpeed));

        if (mtrspeed$checkboxFollowDeviation == null) {
            mtrspeed$checkboxFollowDeviation = new CheckboxWidgetExtension(
                    0, 0, MTRSPEED$ROW_HEIGHT, MTRSPEED$ROW_HEIGHT, true, value -> {
            });
            // 只在首次创建时读一次：Minecraft resize 会复用 Screen 实例重跑 init2，
            // 而 siding 的值要等 onClose2 才写回，无条件覆盖会把用户刚勾的状态清掉。
            mtrspeed$checkboxFollowDeviation.setChecked(
                    siding != null && ((SidingMaxSpeedAccess) (Object) siding).mtrspeed$getMaxSpeedFollowsDeviation()
            );
        }
        mtrspeed$checkboxFollowDeviation.setX2(MTRSPEED$LEFT);
        mtrspeed$checkboxFollowDeviation.setY2(mtrspeed$row3Y(row1Y));
        ((ScreenExtension) (Object) this).addChild(new ClickableWidget(mtrspeed$checkboxFollowDeviation));

        if (mtrspeed$buttonCopyToDepot == null) {
            mtrspeed$buttonCopyToDepot = new ButtonWidgetExtension(
                    0, 0, MTRSPEED$BUTTON_WIDTH, MTRSPEED$ROW_HEIGHT,
                    MTRSPEED$COPY_LABEL, button -> mtrspeed$copyToOtherSidingsInDepot()
            );
        } else {
            mtrspeed$buttonCopyToDepot.setMessage2(Text.of(MTRSPEED$COPY_LABEL));
        }
        mtrspeed$buttonCopyToDepot.setX2(MTRSPEED$LEFT);
        mtrspeed$buttonCopyToDepot.setY2(mtrspeed$row4Y(row1Y));
        ((ScreenExtension) (Object) this).addChild(new ClickableWidget(mtrspeed$buttonCopyToDepot));
    }

    @Inject(method = "tick2", at = @At("TAIL"))
    private void mtrspeed$tick(CallbackInfo callbackInfo) {
        if (mtrspeed$textFieldMaxSpeed != null) {
            mtrspeed$textFieldMaxSpeed.tick2();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mtrspeed$render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta, CallbackInfo callbackInfo) {
        final SavedRailScreenBaseAccess access = (SavedRailScreenBaseAccess) this;
        if (!access.mtrspeed$showScheduleControls()) {
            return;
        }
        final int light = GraphicsHolder.getDefaultLight();
        final int row1Y = mtrspeed$row1Y();
        graphicsHolder.drawText(MTRSPEED$LABEL, MTRSPEED$LEFT, row1Y + MTRSPEED$TEXT_CENTER_OFFSET, -1, false, light);
        graphicsHolder.drawText(MTRSPEED$HINT, MTRSPEED$LEFT, mtrspeed$row2Y(row1Y), -1, false, light);
        graphicsHolder.drawText(
                MTRSPEED$FOLLOW_LABEL,
                MTRSPEED$LEFT + 24,
                mtrspeed$row3Y(row1Y) + MTRSPEED$TEXT_CENTER_OFFSET,
                -1, false, light
        );
    }

    @Inject(method = "onClose2", at = @At("HEAD"))
    private void mtrspeed$onClose(CallbackInfo callbackInfo) {
        final SavedRailScreenBaseAccess access = (SavedRailScreenBaseAccess) this;
        if (!access.mtrspeed$showScheduleControls()) {
            return;
        }
        final Siding siding = (Siding) access.mtrspeed$getSavedRailBase();
        if (siding == null) {
            return;
        }
        final SidingMaxSpeedAccess sidingAccess = (SidingMaxSpeedAccess) (Object) siding;

        if (mtrspeed$textFieldMaxSpeed != null) {
            double kilometersPerHour = 0;
            try {
                kilometersPerHour = Double.parseDouble(mtrspeed$textFieldMaxSpeed.getText2());
            } catch (Exception ignored) {
            }
            sidingAccess.mtrspeed$setTrainMaxSpeedKilometersPerHour(kilometersPerHour);
        }
        if (mtrspeed$checkboxFollowDeviation != null) {
            sidingAccess.mtrspeed$setMaxSpeedFollowsDeviation(mtrspeed$checkboxFollowDeviation.isChecked2());
        }
    }

    /**
     * 第 1 行的 y：锚点取 MTR 自己的 {@code sliderDwellTimeSec}（界面上最后一个原生控件），
     * 排在它底部 + 间隙的位置。语言切换导致 textWidth 变化、MTR 增删控件时都会自动跟随。
     */
    @Unique
    private int mtrspeed$row1Y() {
        final WidgetShorterSlider anchor = ((SavedRailScreenBaseAccess) this).mtrspeed$getSliderDwellTimeSec();
        if (anchor == null) {
            // 兜底：sliderDwellTimeSec 在 MTR 4.0.4 的 SidingScreen#setButtons 里被硬编码为 y=238、高 20，
            // 这里取「238 + 行高 + 间隙」等价于锚点存在时的结果。MTR 改版后以锚点为准，这个分支只是保险。
            return 238 + MTRSPEED$ROW_HEIGHT + MTRSPEED$TOP_GAP;
        }
        final int height = anchor.getHeight2();
        return anchor.getY2() + (height > 0 ? height : MTRSPEED$ROW_HEIGHT) + MTRSPEED$TOP_GAP;
    }

    /** 整数不带小数点，小数最多 3 位（避免 80.5 显示成 80、关界面时又被写回成 80） */
    @Unique
    private static String mtrspeed$format(double kilometersPerHour) {
        final double rounded = Math.round(kilometersPerHour * 1000.0) / 1000.0;
        return rounded == Math.rint(rounded)
                ? String.valueOf((long) rounded)
                : String.valueOf(rounded);
    }

    /** 第 2 行（说明文字，y 是文字顶部） */
    @Unique
    private int mtrspeed$row2Y(int row1Y) {
        return row1Y + MTRSPEED$ROW_HEIGHT + 1;
    }

    /** 第 3 行（勾选框） */
    @Unique
    private int mtrspeed$row3Y(int row1Y) {
        return row1Y + MTRSPEED$ROW_HEIGHT + MTRSPEED$HINT_HEIGHT;
    }

    /** 第 4 行（复制按钮） */
    @Unique
    private int mtrspeed$row4Y(int row1Y) {
        return mtrspeed$row3Y(row1Y) + MTRSPEED$ROW_HEIGHT;
    }

    /**
     * 把界面上当前的「列车最高时速」+「跟随晚点浮动」一次性刷到同一个车场（Depot）里的其他所有侧线。
     *
     * 走的是 MTR 自己的更新通道：改完本地对象后塞进 {@link UpdateDataRequest} 发给服务端，
     * 否则只有当前这条侧线会被 {@code SidingScreen#onClose2} 同步，其他侧线的改动不会落库。
     *
     * 注意这里是**先改本地、再发请求**，且拿不到服务端回执 —— 多人服非 OP 时服务端会直接丢包，
     * 本地却看起来是改成了。所以按钮文案只说「已提交」，详见方法末尾的说明。
     *
     * 分批发送：一条 Siding 序列化出来含 pathDistances / timeSegments 等数组，体积随线路长度增长，
     * 而 Fabric 自定义 payload 有单包上限。不分批的话大车场一旦超限，整个包被拒，
     * 连玩家自己这条都存不进去。
     */
    @Unique
    private void mtrspeed$copyToOtherSidingsInDepot() {
        final SavedRailScreenBaseAccess access = (SavedRailScreenBaseAccess) this;
        final Siding siding = (Siding) access.mtrspeed$getSavedRailBase();
        if (siding == null || mtrspeed$buttonCopyToDepot == null) {
            return;
        }

        double kilometersPerHour = 0;
        if (mtrspeed$textFieldMaxSpeed != null) {
            try {
                kilometersPerHour = Double.parseDouble(mtrspeed$textFieldMaxSpeed.getText2());
            } catch (Exception ignored) {
            }
        }
        final boolean followDeviation = mtrspeed$checkboxFollowDeviation != null
                && mtrspeed$checkboxFollowDeviation.isChecked2();

        final SidingMaxSpeedAccess self = (SidingMaxSpeedAccess) (Object) siding;
        self.mtrspeed$setTrainMaxSpeedKilometersPerHour(kilometersPerHour);
        self.mtrspeed$setMaxSpeedFollowsDeviation(followDeviation);

        UpdateDataRequest request = new UpdateDataRequest(MinecraftClientData.getDashboardInstance());
        request.addSiding(siding);
        int pending = 1;
        int count = 0;
        int packets = 0;

        final Depot depot = siding.area;
        if (depot != null) {
            for (Object entry : depot.savedRails) {
                if (!(entry instanceof Siding) || entry == siding) {
                    continue;
                }
                final SidingMaxSpeedAccess other = (SidingMaxSpeedAccess) entry;
                other.mtrspeed$setTrainMaxSpeedKilometersPerHour(kilometersPerHour);
                other.mtrspeed$setMaxSpeedFollowsDeviation(followDeviation);
                request.addSiding((Siding) entry);
                count++;
                pending++;
                if (pending >= MTRSPEED$BATCH_SIZE) {
                    mtrspeed$sendUpdate(request);
                    packets++;
                    request = new UpdateDataRequest(MinecraftClientData.getDashboardInstance());
                    pending = 0;
                }
            }
        }

        if (pending > 0) {
            mtrspeed$sendUpdate(request);
            packets++;
        }
        // 文案必须说「已提交」而不是「已复制」：
        // 客户端这边是先改本地对象、再 sendPacketToServer，而 UpdateDataRequest#update() 的
        // UpdateDataResponse 只有服务端才拿得到 —— 客户端永远不会收到确认。
        // 多人服非 OP（或没在 dashboard 编辑模式）时服务端会直接丢包：
        // 本地显示改成功了，重进存档 / 重拉 dashboard 数据后会静默回滚。
        // 两种情况对外表现都是「按钮提示成功但实际没生效」，所以措辞要把「已发出待确认」说清楚，
        // 别让玩家以为这一下就已经落库了。
        // 顺带：分包后存在「部分成功」的中间状态（某包超时/被拒时车场里一半改了一半没改），
        // 所以要把包数一起报出来。
        mtrspeed$buttonCopyToDepot.setMessage2(Text.of(
                count == 0
                        ? "该车场没有其他侧线"
                        : packets > 1
                        ? "已提交 " + count + " 条（" + packets + " 包）"
                        : "已提交 " + count + " 条，等待服务端确认"
        ));
    }

    @Unique
    private static void mtrspeed$sendUpdate(UpdateDataRequest request) {
        InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(request));
    }
}
