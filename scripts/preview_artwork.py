"""Compile the production artwork shader; verify fidelity, animation and sizing."""
from pathlib import Path
import sys, os, argparse, json, re
root=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root/'.pyenv'))
os.environ['__GL_SHADER_DISK_CACHE']='0'
import moderngl, glfw
import numpy as np
from PIL import Image

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--animate',action='store_true')
    parser.add_argument('--orbit-cycle',action='store_true',help='Accelerated preview of a full 60-second disk revolution')
    parser.add_argument('--baseline-shader',help='Compare approved regions with a previous shader')
    args=parser.parse_args()
    assert glfw.init()
    glfw.window_hint(glfw.CONTEXT_VERSION_MAJOR,3)
    glfw.window_hint(glfw.CONTEXT_VERSION_MINOR,3)
    glfw.window_hint(glfw.OPENGL_PROFILE,glfw.OPENGL_CORE_PROFILE)
    glfw.window_hint(glfw.VISIBLE,glfw.FALSE)
    win=glfw.create_window(1,1,'artwork verification',None,None)
    assert win
    glfw.make_context_current(win)
    ctx=moderngl.create_context()
    assets=root/'src/main/resources/assets/gto_menu'
    source=Image.open(assets/'textures/reference.png').convert('RGBA')
    sw,sh=source.size
    tex=ctx.texture(source.size,4,source.tobytes())
    tex.filter=(moderngl.LINEAR,moderngl.LINEAR)
    tex.repeat_x=tex.repeat_y=False
    prog=ctx.program(vertex_shader=(assets/'shaders/fsquad.vsh').read_text(encoding='utf-8'),fragment_shader=(assets/'shaders/artwork_flow.fsh').read_text(encoding='utf-8'))
    vao=ctx.vertex_array(prog,[])
    prog['uArtwork']=0
    prog['uSourceAspect']=sw/sh
    tex.use(0)
    out=root/'效果图'
    config=(root/'src/main/java/com/gto/menu/render/RenderConfig.java').read_text(encoding='utf-8')
    speed=float(re.search(r'ARTWORK_FLOW_STRENGTH = ([0-9.]+)f',config)[1])
    period=60.0/speed
    def render(w,h,t,strength=speed):
        target=ctx.texture((w,h),4)
        fbo=ctx.framebuffer([target])
        fbo.use()
        ctx.viewport=(0,0,w,h)
        prog['uResolution']=(w,h)
        prog['uOrbitAngle']=(t*strength/60.0 % 1.0)*2*np.pi
        prog['uFlowStrength']=strength
        vao.render(mode=moderngl.TRIANGLES,vertices=3)
        a=np.frombuffer(fbo.read(components=4,alignment=1),np.uint8).reshape(h,w,4)[::-1].copy()
        fbo.release();target.release()
        return a
    original=np.array(source)
    still=render(sw,sh,3.0,0)
    difference=np.abs(still.astype(int)-original.astype(int))
    assert difference.max()<=1, 'Static artwork differs from original'
    a=render(1280,720,1.25);b=render(1280,720,2.75)
    delta=np.abs(a[:,:,:3].astype(float)-b[:,:,:3].astype(float))
    assert delta.mean()>0.1, 'Animation does not change pixels'
    Image.fromarray(a).save(out/'artwork_flow_preview.png')
    portrait=render(720,960,2.75)
    assert portrait[:200,:,:3].max()==0, 'Letterbox is not black'
    Image.fromarray(portrait).save(out/'artwork_flow_portrait.png')
    before=render(640,360,period-0.0001);after=render(640,360,period+0.0001)
    seam=np.abs(before.astype(float)-after.astype(float)).mean()
    assert seam<0.2, 'Complete revolution seam pops'
    quarter=render(1280,720,period/4)
    Image.fromarray(quarter).save(out/'orbit_quarter.png')
    Image.fromarray(render(1280,720,period/2)).save(out/'orbit_half.png')
    first=render(640,360,0)
    full=render(640,360,period)
    full_error=float(np.abs(first.astype(float)-full.astype(float)).mean())
    assert full_error<0.05, 'A complete revolution does not close'
    partial_errors={}
    for moment in [2,6,period/4,period/2,period*.75,period-2]:
        partial_errors[str(moment)]=float(np.abs(first.astype(float)-render(640,360,moment).astype(float)).mean())
        assert partial_errors[str(moment)]>0.2, 'Orbit returned to start prematurely'
    # Verify actual output stays continuous at every old timer/phase boundary.
    seam_checks={}
    for moment in [2,30,60,120,256,period,period*2]:
        l=render(640,360,moment-0.0001)
        r=render(640,360,moment+0.0001)
        seam_checks[str(moment)]=float(np.abs(l.astype(float)-r.astype(float)).mean())
        assert seam_checks[str(moment)]<0.2, 'Visible clock boundary'
    if args.orbit_cycle:
        frames=[Image.fromarray(render(640,360,period*i/180)[:,:,:3]) for i in range(180)]
        frames[0].save(out/'orbit_full_turn_accelerated.gif',save_all=True,append_images=frames[1:],duration=33,loop=0)
    if args.animate:
        frames=[Image.fromarray(render(960,540,1+i/24)[:,:,:3]) for i in range(96)]
        frames[0].save(out/'artwork_flow_preview.gif',save_all=True,append_images=frames[1:],duration=42,loop=0)
    # User-annotated areas, in normalized image coordinates (top-left origin).
    rois={'left_foreground':(0.29,0.57,0.38,0.67),
          'lower_foreground':(0.37,0.63,0.52,0.72),
          'right_inner':(0.67,0.38,0.75,0.50),
          'central_crossing':(0.47,0.535,0.53,0.57)}
    region_motion={}
    for name,(x0,y0,x1,y1) in rois.items():
        patch=delta[int(y0*720):int(y1*720),int(x0*1280):int(x1*1280)]
        region_motion[name]=float(patch.mean())
        assert patch.mean()>0.5, f'{name} has no detectable motion'
    approved_regions={}
    if args.baseline_shader:
        current_prog,current_vao=prog,vao
        prog=ctx.program(vertex_shader=(assets/'shaders/fsquad.vsh').read_text(encoding='utf-8'),fragment_shader=Path(args.baseline_shader).read_text(encoding='utf-8'))
        vao=ctx.vertex_array(prog,[])
        prog['uArtwork']=0;prog['uSourceAspect']=sw/sh
        prior=render(1280,720,1.25)
        prog,vao=current_prog,current_vao
        for name,(x0,y0,x1,y1) in {'corona_top':(.48,.24,.52,.255),'outer_right':(.90,.25,.92,.28)}.items():
            patch=np.abs(a.astype(float)-prior.astype(float))[int(y0*720):int(y1*720),int(x0*1280):int(x1*1280),:3]
            approved_regions[name]=float(patch.mean())
            assert patch.mean()<0.1, f'{name} changed unexpectedly'
    report=dict(gpu=ctx.info['GL_RENDERER'],source_size=[sw,sh],texture_bytes=sw*sh*4,
                static_max_byte_error=int(difference.max()),static_mean_byte_error=float(difference.mean()),
                animated_mean_byte_change=float(delta.mean()),phase_seam_mean_byte_change=float(seam),
                letterbox_black=True,full_revolution_mean_byte_error=full_error,
                partial_turn_mean_byte_difference=partial_errors,boundary_mean_byte_change=seam_checks,
                speed_multiplier=speed,disk_revolution_seconds=period,corona_revolution_seconds=period/2,inner_revolution_seconds=period/2,
                user_annotated_region_motion=region_motion,approved_region_difference_from_baseline=approved_regions)
    (out/'artwork_flow_validation.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2))
    glfw.terminate()
if __name__=='__main__':main()
