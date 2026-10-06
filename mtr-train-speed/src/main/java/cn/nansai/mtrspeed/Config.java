package cn.nansai.mtrspeed;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 极简配置：config/mtr4-advanced.properties
 * 不依赖 Fabric API / Gson，纯 JDK 实现。
 */
public final class Config {

    /** 提速由车尾控制：车尾通过提速点后才允许加速（ true = 现实逻辑，false = 原版逻辑 ） */
    public static boolean tailControlledAcceleration = true;
    /** 侧线最高时速为 0 时使用的默认值（0 = 不限制） */
    public static double defaultMaxSpeedKilometersPerHour = 0;
    /** 是否把列车最高时速也套用到侧线的时刻表推算（减少“永远晚点”） */
    public static boolean applyMaxSpeedToTimeSegments = true;
    /**
     * 推算时刻表时是否按【发车侧线自己的列车长度】做「车尾控制提速」。
     * 开启后：时刻表不再是质点模型，而是按该侧线的车型长度取 [车尾, 车头] 区间内最低限速，
     * 这样延迟加速导致的耗时会被算进计划时间里，不会结构性晚点。
     */
    public static boolean applyVehicleLengthToTimeSegments = true;

    private static volatile boolean loaded = false;

    private Config() {
    }

    /**
     * 加载配置。
     *
     * 注意两点（否则配置会静默失效）：
     * 1. 必须在 4 个字段全部赋值【之后】才置 loaded —— 否则别的线程可能在读文件途中
     *    看到 loaded==true 直接返回，拿到默认值。
     * 2. loaded 必须是 volatile —— 渲染线程（SidingScreenMixin）和服务端线程（SpeedLogic）
     *    都会调这里，靠 volatile 的 happens-before 保证字段写入对其它线程可见。
     */
    public static void load() {
        if (loaded) {
            return;
        }
        synchronized (Config.class) {
            if (loaded) {
                return;
            }
            File file = configFile();
            if (file.exists()) {
                Properties properties = new Properties();
                try (InputStream inputStream = new FileInputStream(file)) {
                    properties.load(inputStream);
                    tailControlledAcceleration = getBoolean(properties, "tail-controlled-acceleration", tailControlledAcceleration);
                    defaultMaxSpeedKilometersPerHour = getDouble(properties, "default-max-speed-kmh", defaultMaxSpeedKilometersPerHour);
                    applyMaxSpeedToTimeSegments = getBoolean(properties, "apply-max-speed-to-time-segments", applyMaxSpeedToTimeSegments);
                    applyVehicleLengthToTimeSegments = getBoolean(properties, "apply-vehicle-length-to-time-segments", applyVehicleLengthToTimeSegments);
                } catch (Exception e) {
                    warn("读取 " + file.getAbsolutePath() + " 失败，使用默认配置", e);
                }
            } else {
                writeDefault(file);
            }
            loaded = true;
        }
    }

    /**
     * 配置文件位置：<游戏目录>/config/mtr4-advanced.properties
     *
     * 不能写死 {@code System.getProperty("user.dir")} —— 那是【进程的工作目录】，不是游戏目录。
     * 双击 jar / 从别的目录跑 .bat / 面板（Pterodactyl 等）/ systemd 起服务端时，
     * user.dir 往往不是游戏目录 → 配置被写到别处、服务端静默跑默认值，
     * 属于最难查的那类 bug（表现是「配置明明改了却没生效」，日志里什么都没有）。
     *
     * 所以优先用 FabricLoader 认的那个 config 目录；反射调用是为了**编译期不依赖
     * fabric-loader**（本项目的 classpath 只有 MTR jar + mixin jar），取不到就回退 user.dir。
     */
    private static File configFile() {
        File dir = fabricConfigDir();
        if (dir == null) {
            dir = new File(System.getProperty("user.dir"), "config");
        }
        return new File(dir, "mtr4-advanced.properties");
    }

    private static File fabricConfigDir() {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            Object path = loaderClass.getMethod("getConfigDir").invoke(loader);
            if (path instanceof Path) {
                return ((Path) path).toFile();
            }
        } catch (Throwable t) {
            // 不在 Fabric 环境（理论上不会发生）→ 交给调用方回退到 user.dir
        }
        return null;
    }

    private static void warn(String message, Exception e) {
        // 不直接引用 MTR 的 LOGGER：log4j 不在编译 classpath 里（MTR 是运行时才拿得到）。
        // Minecraft 会把 System.err 收进 latest.log，够用了。
        System.err.println("[MTR4 Advanced] " + message);
        if (e != null) {
            e.printStackTrace();
        }
    }

    private static void writeDefault(File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            Properties properties = new Properties();
            properties.setProperty("tail-controlled-acceleration", Boolean.toString(tailControlledAcceleration));
            properties.setProperty("default-max-speed-kmh", Double.toString(defaultMaxSpeedKilometersPerHour));
            properties.setProperty("apply-max-speed-to-time-segments", Boolean.toString(applyMaxSpeedToTimeSegments));
            properties.setProperty("apply-vehicle-length-to-time-segments", Boolean.toString(applyVehicleLengthToTimeSegments));
            // 手写一份带说明的默认配置：服务端/客户端都是服主自己改，没注释等于要他翻 README。
            // 注释是 UTF-8 中文，读取端用 Properties.load(InputStream)（ISO-8859-1）也没事 ——
            // '#' 开头的行会被整个跳过，注释内容不会被解析。
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write("# MTR4 Advanced —— MTR 4.0.4 附属模组\n");
                writer.write("# 改完要重启游戏 / 服务端才生效（配置只在启动时读一次）\n");
                writer.write("#\n");
                writer.write("# tail-controlled-acceleration\n");
                writer.write("#   提速由车尾控制。true = 车尾通过提速点后才允许加速（现实逻辑）\n");
                writer.write("#   false = MTR 原版逻辑（车头通过即加速）\n");
                writer.write("# default-max-speed-kmh\n");
                writer.write("#   侧线没填「列车最高时速」时用的默认值。0 = 不限制\n");
                writer.write("# apply-max-speed-to-time-segments\n");
                writer.write("#   把列车最高时速套进侧线的时刻表推算（不勾的话限列车速的车会结构性晚点）\n");
                writer.write("# apply-vehicle-length-to-time-segments\n");
                writer.write("#   时刻表按发车侧线自己的列车长度，取 [车尾, 车头] 区间内最低限速\n");
                writer.write("\n");
                // comments 传 null 只是不额外写一行自定义注释；
                // Properties.store 仍会写一行 #日期（Java 的行为，去不掉），无副作用
                properties.store(writer, null);
            }
            System.out.println("[MTR4 Advanced] 已生成默认配置: " + file.getAbsolutePath());
        } catch (Exception e) {
            warn("写默认配置 " + file.getAbsolutePath() + " 失败", e);
        }
    }

    private static boolean getBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static double getDouble(Properties properties, String key, double fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
