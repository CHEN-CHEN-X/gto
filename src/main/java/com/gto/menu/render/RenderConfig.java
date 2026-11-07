package com.gto.menu.render;

/**
 * 渲染参数集中配置。
 *
 * 黑洞部分基于史瓦西光线追踪（几何单位 r_s = 1），
 * Java 侧通过 uniform 传给 GLSL；方块渲染器直接读取。
 * 修改此文件即可调整整体效果。
 */
public final class RenderConfig {

    private RenderConfig() {}

    /** Reference-derived glyph orbits; no video/frame sequence. */
    public static final boolean ARTWORK_FLOW = true;
    /** 0 = paused; 0.2 = disk 300 s/turn, corona 150 s/turn; motion follows local source density. */
    public static final float ARTWORK_FLOW_STRENGTH = 0.2f;
    /** Faint past-pose emission at 80/160 ms; no framebuffer history. */
    public static final float ARTWORK_TRAIL_STRENGTH = 0.05f;
    /** Opacity of the aligned bright-digit / dark-line materials (0..1); no UV motion. */
    public static final float ARTWORK_SPOT_BRIGHT_GAIN = 0.7f;
    public static final float ARTWORK_SPOT_DARK_DROP = 0.28f;
    public static final float ARTWORK_SPOT_ORBIT_SECONDS = 300.0f;
    /** Background binary stars, relative to full brightness; 0 disables them. */
    public static final float BACKGROUND_STAR_STRENGTH = 0.4f;

    // ==================== 相机（轨道相机） ====================
    /** 方位角（弧度），固定；可改为随时间缓慢自转 */
    public static final float CAM_AZIMUTH = 0.7f;
    /** 极角（弧度）：1.18 ≈ 高出盘面 22.4°；主像为扁平椭圆 */
    public static final float CAM_POLAR = 1.26f;
    /** 相机距离（r_s 单位），大于盘外缘；12.8 保留四周黑场 */
    public static final float CAM_DISTANCE = 11.6f;
    /** 垂直视场角（度） */
    public static final float CAM_FOV_DEG = 57.0f;
    /** 相机缓慢自转速度（rad/s），0.03 ≈ 3.5 分钟一圈；0 = 固定不动 */
    public static final float CAM_AUTO_SPIN = 0.0f;

    /** 固定画面旋转（弧度），匹配参考图的斜向椭圆盘。 */
    public static final float CAM_ROLL = -0.26f;
    /** 纯效果主菜单；false 恢复现有菜单控件。 */
    public static final boolean EFFECT_ONLY = true;

    // ==================== 光线追踪 ====================
    /** 积分步长缩放：low=1.5 / medium=1.2 / high=1.0 / ultra=0.75 */
    public static final float RAY_STEP_SCALE = 1.2f;
    /** 内部分辨率缩放（<1 时先低分辨率追踪再线性放大，显著提速） */
    public static final float RENDER_SCALE = 1.0f;

    // ==================== 吸积盘 ====================
    /** 艺术化发光盘内缘（1.9；物理稳定轨道 ISCO 为 3，r_s 单位） */
    public static final float DISK_INNER = 1.9f;
    /** 盘外缘 */
    public static final float DISK_OUTER = 10.5f;
    /** 数字自发光亮度（HDR 线性） */
    public static final float DISK_BRIGHTNESS = 1.2f;
    /** 多普勒集束 0~1（靠近侧更亮、远离侧更暗），0.8 保留方向感又不过曝 */
    public static final float BEAMING = 0.5f;
    /** 公转方向：1 或 -1 */
    public static final float ORBIT_DIR = 1.0f;
    /** 盘纹理时间流速（内缘开普勒周期约 46s，此值再整体缩放） */
    public static final float TIME_SCALE = 0.85f;

    // ==================== 0/1 数字 ====================
    /** 角向密度（每单位弧长的数字数），越大数字越密 */
    public static final float DIGIT_DENSITY = 1.9f;
    /** 径向轨道密度（每 r_s；默认约 13 层） */
    public static final float DIGIT_DENSITY_R = 1.5f;
    /** 对数螺旋缠绕强度 */
    public static final float SPIRAL_TURNS = 2.8f;
    /** （环系统重构后不再使用，保留兼容） */
    public static final float BILLBOARD_RADIUS = 1.1f;
    /** 环霓虹线 + 视界火花环强度（奇异博士式传送门光弧；0=关闭） */
    public static final float RING_STRENGTH = 1.0f;
    /** 环间气体托底辉光强度（保持环间近黑，只留氛围；0=关闭） */
    public static final float GAS_STRENGTH = 0.045f;
    /** 环上流动亮斑增益：数值越大，沿环奔跑的霓虹亮斑越明显 */
    public static final float RIBBON_GAIN = 1.0f;

    // ==================== 星辰 ====================
    /** 星辰整体亮度 */
    public static final float STAR_INTENSITY = 0.0f;
    /** 每格出现概率（很小） */
    public static final float STAR_DENSITY = 0.0f;

    // ==================== 尾迹 / Bloom / 后期 ====================
    /** 每 1/60 秒尾迹衰减；Java 按实际 dt 指数换算，0.8 的半衰期约 52ms */
    public static final float TRAIL_DECAY = 0.80f;
    /** Bloom 亮部阈值（HDR 线性亮度），只让光子环/最亮数字发光 */
    public static final float BLOOM_THRESHOLD = 0.85f;
    /** Bloom 强度 */
    public static final float BLOOM_STRENGTH = 0.7f;
    /** 曝光 */
    public static final float EXPOSURE = 1.1f;
    /** 暗角强度 */
    public static final float VIGNETTE = 0.12f;
    /** 底部标题区压暗：起始/结束（NDC y），压暗量 */
    public static final float BOTTOM_FADE_START = -0.95f;
    public static final float BOTTOM_FADE_END = -0.70f;
    public static final float BOTTOM_FADE_DARK = 0.0f;

    // ========================================================================
    // 以下为 GUI 空间电压方块渲染器保留的旧参数（暂不改动）
    // ========================================================================

    /** 屏幕空间事件视界半径（方块渲染器坐标用，uv 单位） */
    public static final float RS = 0.18f;
    public static final float TILT = 1.31f;
    public static final float COS_TILT = (float) Math.cos(TILT);
    public static final float SIN_TILT = (float) Math.sin(TILT);

    // ==================== 电压方块 ====================
    public static final float BLOCK_BASE_SCALE = 1.5f;
    public static final float BLOCK_DEPTH_SCALE = 0.3f;
    public static final float BLOCK_TIDAL_STRENGTH = 2.5f;
    public static final float BLOCK_SPIN_SPEED = 0.5f;
    public static final float BLOCK_CYCLE_BASE = 60.0f;
    public static final float BLOCK_CYCLE_STEP = 5.0f;
    public static final float BLOCK_FADE_IN_END = 0.05f;
    public static final float BLOCK_FADE_OUT_START = 0.92f;

    // ==================== 电压方块轨道 ====================
    // 格式：{ 轨道半径(uv), 角速度(rad/s), 该轨道方块数 }
    public static final float[][] ORBITS = {
        {0.22f, 0.50f, 1},   // ULV  最内轨
        {0.30f, 0.40f, 1},   // LV
        {0.39f, 0.32f, 1},   // MV
        {0.49f, 0.26f, 1},   // HV
        {0.60f, 0.21f, 2},   // EV   双星
        {0.72f, 0.17f, 1},   // IV
        {0.85f, 0.14f, 1},   // LuV
        {0.99f, 0.12f, 2},   // ZPM  双星
        {1.14f, 0.10f, 1},   // UV
        {1.30f, 0.08f, 1},   // UHV
        {1.47f, 0.07f, 1},   // UEV
        {1.65f, 0.06f, 2},   // UIV  双星
        {1.84f, 0.05f, 1},   // UXV
        {2.04f, 0.04f, 1},   // MAX  最外轨
    };
}
