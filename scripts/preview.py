#!/usr/bin/env python3
"""
GTO Menu 黑洞背景离线预览器
直接加载工程里的真实 shader 文件，在 GPU 上跑完整 4-pass HDR 管线，
几秒出一张 PNG，不用启动 Minecraft。

用法：
    python scripts/preview.py
    输出：效果图/preview_binary.png
"""
import sys, os, math, re, argparse
from pathlib import Path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', '.pyenv'))
# 沙箱中禁用 NVIDIA 着色器缓存，避免写文件被拦
os.environ['__GL_SHADER_DISK_CACHE'] = '0'
os.environ['__GL_SHADER_DISK_CACHE_SKIP_CLEANUP'] = '1'

import moderngl
import glfw
import numpy as np
from PIL import Image

# ==================== 配置参数（来自 RenderConfig.java） ====================
ROOT = Path(__file__).resolve().parents[1]
config = (ROOT / 'src/main/java/com/gto/menu/render/RenderConfig.java').read_text(encoding='utf-8')
for name, value in re.findall(r'public static final float (\w+) = (-?[0-9.]+)f;', config):
    globals()[name] = float(value)
parser = argparse.ArgumentParser(description='Render the actual GLSL pipeline on the GPU')
parser.add_argument('--width', type=int, default=1280)
parser.add_argument('--height', type=int, default=720)
parser.add_argument('--frames', type=int, default=48)
parser.add_argument('--fps', type=float, default=60.0)
parser.add_argument('--time', type=float, default=4.0)
parser.add_argument('--output', default='preview_binary.png')
parser.add_argument('--animate', action='store_true', help='Save a diagnostic GIF (time interval may not loop seamlessly)')
args = parser.parse_args()
SCREEN_W, SCREEN_H = args.width, args.height
TRAIL_FRAMES = args.frames

SHADER_DIR = os.path.join(os.path.dirname(__file__),
    '..', 'src', 'main', 'resources', 'assets', 'gto_menu', 'shaders')
OUT_DIR    = os.path.join(os.path.dirname(__file__), '..', '效果图')

def read_shader(name):
    with open(os.path.join(SHADER_DIR, name), 'r', encoding='utf-8') as f:
        return f.read()

def main():
    # ---- GLFW 上下文 ----
    if not glfw.init():
        raise RuntimeError('GLFW init failed')
    glfw.window_hint(glfw.CONTEXT_VERSION_MAJOR, 3)
    glfw.window_hint(glfw.CONTEXT_VERSION_MINOR, 3)
    glfw.window_hint(glfw.OPENGL_PROFILE, glfw.OPENGL_CORE_PROFILE)
    glfw.window_hint(glfw.VISIBLE, glfw.FALSE)
    window = glfw.create_window(1, 1, 'preview', None, None)
    if not window:
        glfw.terminate(); raise RuntimeError('GLFW window creation failed')
    glfw.make_context_current(window)

    ctx = moderngl.create_context()
    ctx.disable(moderngl.DEPTH_TEST)
    print('GPU:', ctx.info['GL_RENDERER'], ctx.info['GL_VERSION'])

    # ---- 分辨率 ----
    w = max(1, round(SCREEN_W * RENDER_SCALE))
    h = max(1, round(SCREEN_H * RENDER_SCALE))

    # ---- 纹理 / FBO ----
    def make_tex(size, comp, dtype='f4'):
        t = ctx.texture(size, comp, dtype=dtype)
        t.repeat_x = t.repeat_y = False
        t.filter = (moderngl.LINEAR, moderngl.LINEAR)
        return t

    scene_tex = make_tex((w, h), 4, 'f2')
    scene_fbo = ctx.framebuffer(color_attachments=[scene_tex])

    trail_tex = [make_tex((w, h), 4, 'f2'), make_tex((w, h), 4, 'f2')]
    trail_fbo = [ctx.framebuffer(color_attachments=[t]) for t in trail_tex]

    bw, bh = max(1, w // 2), max(1, h // 2)
    bloom_tex = [make_tex((bw, bh), 4, 'f2'), make_tex((bw, bh), 4, 'f2')]
    bloom_fbo = [ctx.framebuffer(color_attachments=[t]) for t in bloom_tex]

    final_tex = make_tex((SCREEN_W, SCREEN_H), 4, 'f2')  # 与游戏内一致 RGBA16F
    final_fbo = ctx.framebuffer(color_attachments=[final_tex])

    for target in trail_fbo:
        target.clear()

    # ---- Shader Programs ----
    scene_prog = ctx.program(
        vertex_shader=read_shader('fsquad.vsh'),
        fragment_shader=read_shader('blackhole.fsh'))
    trail_prog = ctx.program(
        vertex_shader=read_shader('fsquad.vsh'),
        fragment_shader=read_shader('trail.fsh'))
    bloom_prog = ctx.program(
        vertex_shader=read_shader('fsquad.vsh'),
        fragment_shader=read_shader('bloom.fsh'))
    composite_prog = ctx.program(
        vertex_shader=read_shader('fsquad.vsh'),
        fragment_shader=read_shader('composite.fsh'))

    # 调试：打印 scene program 的所有 uniform
    print('All four GLSL 150 programs compiled and linked.')

    # 每个 program 一个空 VAO（gl_VertexID 驱动全屏三角形）
    vao_scene     = ctx.vertex_array(scene_prog, [])
    vao_trail     = ctx.vertex_array(trail_prog, [])
    vao_bloom     = ctx.vertex_array(bloom_prog, [])
    vao_composite = ctx.vertex_array(composite_prog, [])

    def setu(prog, name, value):
        try:
            prog[name].value = value
        except KeyError:
            pass  # uniform 被 GLSL 编译器优化掉了，安全跳过

    def set_scene(prog, time_val):
        setu(prog, 'uResolution', (w, h))
        setu(prog, 'uTime', time_val)

        az = CAM_AZIMUTH + time_val * CAM_AUTO_SPIN
        pol = CAM_POLAR
        dist = CAM_DISTANCE
        sinP = math.sin(pol); cosP = math.cos(pol)
        px = dist * sinP * math.cos(az)
        py = dist * cosP
        pz = dist * sinP * math.sin(az)
        fx = -px / dist; fy = -py / dist; fz = -pz / dist
        rx = -fz; rz = fx
        rlen = math.hypot(rx, rz)
        rx /= rlen; rz /= rlen
        ux = -rz * fy
        uy =  rz * fx - rx * fz
        uz =  rx * fy

        setu(prog, 'uCamPos', (px, py, pz))
        # 用 tuple 传 mat3，ModernGL 内部自动处理
        cr, sr = math.cos(CAM_ROLL), math.sin(CAM_ROLL)
        prog['uCamMat'].value = (cr*rx+sr*ux, sr*uy, cr*rz+sr*uz, cr*ux-sr*rx, cr*uy, cr*uz-sr*rz, fx, fy, fz)
        setu(prog, 'uTanHalfFov', math.tan(math.radians(CAM_FOV_DEG) * 0.5))
        setu(prog, 'uStepScale', RAY_STEP_SCALE)

        setu(prog, 'uDiskInner',      DISK_INNER)
        setu(prog, 'uDiskOuter',      DISK_OUTER)
        setu(prog, 'uDiskBrightness', DISK_BRIGHTNESS)
        setu(prog, 'uBeaming',        BEAMING)
        setu(prog, 'uOrbitDir',       ORBIT_DIR)
        setu(prog, 'uTimeScale',      TIME_SCALE)

        setu(prog, 'uDigitDensity',   DIGIT_DENSITY)
        setu(prog, 'uDigitDensityR',  DIGIT_DENSITY_R)
        setu(prog, 'uSpiralTurns',    SPIRAL_TURNS)
        setu(prog, 'uBillboardRadius',BILLBOARD_RADIUS)
        setu(prog, 'uRingStrength',   RING_STRENGTH)
        setu(prog, 'uGasStrength',    GAS_STRENGTH)
        setu(prog, 'uRibbonGain',     RIBBON_GAIN)

        setu(prog, 'uStarIntensity',  STAR_INTENSITY)
        setu(prog, 'uStarDensity',    STAR_DENSITY)
        # 调试
        if time_val < 0.01:
            print(f'cam=({px:.2f},{py:.2f},{pz:.2f}) f=({fx:.3f},{fy:.3f},{fz:.3f}) r=({rx:.3f},0,{rz:.3f}) u=({ux:.3f},{uy:.3f},{uz:.3f})')

    # ---- 渲染循环（尾迹累积） ----
    images = []
    trail_idx = 0
    for frame in range(TRAIL_FRAMES + 1):
        t = args.time + frame / args.fps  # 每帧推进 0.04 秒

        # Pass 1: Scene
        scene_fbo.use()
        ctx.viewport = (0, 0, w, h)
        set_scene(scene_prog, t)
        vao_scene.render(mode=moderngl.TRIANGLES, vertices=3)

        # Pass 2: Trail
        nxt = 1 - trail_idx
        trail_fbo[nxt].use()
        ctx.viewport = (0, 0, w, h)
        trail_prog['uCur'].value   = 0
        trail_prog['uPrev'].value  = 1
        trail_prog['uDecay'].value = 0.0 if frame == 0 else TRAIL_DECAY ** (60.0 / args.fps)
        scene_tex.use(location=0)
        trail_tex[trail_idx].use(location=1)
        vao_trail.render(mode=moderngl.TRIANGLES, vertices=3)

        # Pass 3: Bloom
        bloom_prog['uTexel'].value = (1.0 / w, 1.0 / h)

        # extract + H
        bloom_fbo[0].use()
        ctx.viewport = (0, 0, bw, bh)
        bloom_prog['uDir'].value = (1.0, 0.0)
        bloom_prog['uThreshold'].value = BLOOM_THRESHOLD
        trail_tex[nxt].use(location=0)
        vao_bloom.render(mode=moderngl.TRIANGLES, vertices=3)

        # V
        bloom_prog['uTexel'].value = (1.0 / bw, 1.0 / bh)
        bloom_fbo[1].use()
        bloom_prog['uDir'].value = (0.0, 1.0)
        bloom_prog['uThreshold'].value = 0.0
        bloom_tex[0].use(location=0)
        vao_bloom.render(mode=moderngl.TRIANGLES, vertices=3)

        # H (wider)
        bloom_fbo[0].use()
        ctx.viewport = (0, 0, bw, bh)
        bloom_prog['uDir'].value = (1.0, 0.0)
        bloom_tex[1].use(location=0)
        vao_bloom.render(mode=moderngl.TRIANGLES, vertices=3)

        # V
        bloom_prog['uTexel'].value = (1.0 / bw, 1.0 / bh)
        bloom_fbo[1].use()
        bloom_prog['uDir'].value = (0.0, 1.0)
        bloom_tex[0].use(location=0)
        vao_bloom.render(mode=moderngl.TRIANGLES, vertices=3)

        trail_idx = nxt

        if args.animate or frame == TRAIL_FRAMES:
            # Pass 4: Composite (same uniforms and RGBA16F targets as Java).
            final_fbo.use()
            ctx.viewport = (0, 0, SCREEN_W, SCREEN_H)
            for name, value in dict(uTrail=0, uBloom=1, uResolution=(SCREEN_W, SCREEN_H),
                uTime=t, uExposure=EXPOSURE, uBloomStrength=BLOOM_STRENGTH,
                uVignette=VIGNETTE, uBottomStart=BOTTOM_FADE_START,
                uBottomEnd=BOTTOM_FADE_END, uBottomDark=BOTTOM_FADE_DARK).items():
                setu(composite_prog, name, value)
            trail_tex[trail_idx].use(location=0)
            bloom_tex[1].use(location=1)
            vao_composite.render(mode=moderngl.TRIANGLES, vertices=3)
            arr = np.frombuffer(final_tex.read(), dtype=np.float16).reshape(SCREEN_H, SCREEN_W, 4)[:, :, :3]
            assert np.isfinite(arr).all(), 'Non-finite GPU output'
            img = Image.fromarray(np.clip(arr[::-1] * 255, 0, 255).astype(np.uint8))
            images.append(img)
    out = Path(OUT_DIR) / args.output
    out.parent.mkdir(parents=True, exist_ok=True)
    images[-1].save(out)
    if args.animate:
        images[0].save(out.with_suffix('.gif'), save_all=True, append_images=images[1:],
                       duration=round(1000/args.fps), loop=0)
    print('Saved:', out.resolve())
    glfw.terminate()

if __name__ == '__main__':
    main()
