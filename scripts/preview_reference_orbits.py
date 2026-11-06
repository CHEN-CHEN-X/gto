"""Exercise the production shaders/data on OpenGL, including long-run poses."""
from pathlib import Path
import argparse,gzip,struct,json,sys,re,time
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'.pyenv'))
import moderngl,glfw,numpy as np
from PIL import Image,ImageFilter,ImageDraw

def main():
    args=argparse.ArgumentParser();args.add_argument('--animate',action='store_true');opts=args.parse_args()
    assets=ROOT/'src/main/resources/assets/gto_menu';out=ROOT/'效果图';out.mkdir(exist_ok=True)
    read=lambda path:(assets/path).read_text(encoding='utf-8')
    config=(ROOT/'src/main/java/com/gto/menu/render/RenderConfig.java').read_text(encoding='utf-8')
    speed=float(re.search(r'ARTWORK_FLOW_STRENGTH = ([0-9.]+)f',config)[1])
    trail=float(re.search(r'ARTWORK_TRAIL_STRENGTH = ([0-9.]+)f',config)[1])
    period=60/speed if speed>0 else 300
    assert glfw.init()
    glfw.window_hint(glfw.CONTEXT_VERSION_MAJOR,3);glfw.window_hint(glfw.CONTEXT_VERSION_MINOR,2)
    glfw.window_hint(glfw.OPENGL_PROFILE,glfw.OPENGL_CORE_PROFILE);glfw.window_hint(glfw.VISIBLE,False)
    win=glfw.create_window(1,1,'Reference-orbit validation',None,None);assert win
    glfw.make_context_current(win);ctx=moderngl.create_context(require=320)
    plate=ctx.program(vertex_shader=read('shaders/fsquad.vsh'),fragment_shader=read('shaders/orbit_plate.fsh'))
    glyph=ctx.program(vertex_shader=read('shaders/glyph_orbit.vsh'),fragment_shader=read('shaders/glyph_orbit.fsh'))
    pvao=ctx.vertex_array(plate,[])
    data=gzip.open(assets/'textures/orbit_instances.bin.gz','rb').read()
    magic,count=struct.unpack('>II',data[:8]);assert magic==0x47544F31
    instances=np.frombuffer(data[8:],dtype='>f4').astype('f4').reshape(count,16)
    assert np.isfinite(instances).all() and count>500
    vertices=np.repeat(instances,6,axis=0);vbo=ctx.buffer(vertices.tobytes())
    vao=ctx.vertex_array(glyph,[(vbo,'4f 4f 4f 4f','aOrbit','aMotion','aAtlas','aSize')])
    textures=[];texture_bytes=0
    for unit,name in enumerate(['orbit_plate.png','orbit_glyphs.png','orbit_lut.png']):
        im=Image.open(assets/'textures'/name).convert('RGBA')
        tex=ctx.texture(im.size,4,im.tobytes());tex.filter=(moderngl.LINEAR,moderngl.LINEAR)
        tex.repeat_x=tex.repeat_y=False;tex.use(unit);textures.append(tex);texture_bytes+=im.width*im.height*4
        if unit==0:sw,sh=im.size
    plate['uArtwork']=0;glyph['uPlate']=0;glyph['uGlyphs']=1;glyph['uOrbitLut']=2
    target=None;fbo=None;size=None
    def draw(t,w=1280,h=720,sprites=True):
        nonlocal target,fbo,size
        if size!=(w,h):
            if fbo is not None:fbo.release();target.release()
            target=ctx.texture((w,h),4);fbo=ctx.framebuffer([target]);size=(w,h)
        fbo.use();ctx.viewport=(0,0,w,h)
        for p in [plate,glyph]:p['uResolution']=(w,h);p['uSourceAspect']=sw/sh
        ctx.disable(moderngl.BLEND);pvao.render(vertices=3)
        if sprites:
            ctx.enable(moderngl.BLEND);ctx.blend_equation=moderngl.FUNC_ADD
            ctx.blend_func=(moderngl.ONE,moderngl.ONE,moderngl.ZERO,moderngl.ONE)
            for delay,weight in [(.16,trail*.4),(.08,trail*.6),(0,1.)]:
                if weight<=0:continue
                glyph['uOrbitAngle']=((t-delay)*speed/60%1)*2*np.pi
                glyph['uOpacity']=weight;vao.render(vertices=count*6)
    def render(t,w=1280,h=720):
        draw(t,w,h)
        return np.frombuffer(fbo.read(components=4,alignment=1),np.uint8).reshape(h,w,4)[::-1].copy()
    a=render(0);b=render(5);quarter=render(period/4);half=render(period/2)
    for name,frame in [('start',a),('preview',b),('quarter',quarter),('half',half)]:Image.fromarray(frame).save(out/f'reference_orbits_{name}.png')
    reference=Image.open(assets/'textures/reference.png').convert('RGBA')
    reftex=ctx.texture(reference.size,4,reference.tobytes());reftex.filter=(moderngl.LINEAR,moderngl.LINEAR);reftex.repeat_x=reftex.repeat_y=False
    reftex.use(0);draw(0,sprites=False)
    baseline=np.frombuffer(fbo.read(components=4,alignment=1),np.uint8).reshape(720,1280,4)[::-1].copy()
    textures[0].use(0);reftex.release();Image.fromarray(baseline).save(out/'reference_orbits_source_fit.png')
    difference=lambda x,y:float(np.abs(x[:,:,:3].astype(float)-y[:,:,:3].astype(float)).mean())
    fidelity={'start_mean_byte_error':difference(a,baseline),'quarter_mean_byte_error':difference(quarter,baseline)}
    # This is a diagnostic, not a perceptual similarity percentage.
    assert fidelity['start_mean_byte_error']<4,'Initial composition is no longer close to the source'
    blurred=lambda x:np.array(Image.fromarray(x).filter(ImageFilter.GaussianBlur(20)))
    fidelity['quarter_blurred_mean_byte_error']=difference(blurred(quarter),blurred(baseline))
    motion={}
    rois={'foreground_left':(.20,.64,.39,.80),'foreground_middle':(.38,.57,.58,.70),'inner_right':(.68,.34,.80,.48),'corona':(.44,.23,.57,.29)}
    for name,(x0,y0,x1,y1) in rois.items():
        sl=np.s_[int(y0*720):int(y1*720),int(x0*1280):int(x1*1280)]
        motion[name]=difference(a[sl],b[sl]);assert motion[name]>.2,f'{name} appears frozen'
    boundary={}
    for sec in [2,30,60,120,256,period,period*2]:
        boundary[str(sec)]=difference(render(sec-.0001,640,360),render(sec+.0001,640,360))
        assert boundary[str(sec)]<.2,f'Timer discontinuity at {sec}s'
    full_error=difference(render(0,640,360),render(period,640,360));assert full_error<.1
    partial={str(t):difference(render(0,640,360),render(t,640,360)) for t in [2,30,period/4,period/2,period-2]}
    assert min(partial.values())>.1,'Animation repeats a short segment'
    portrait=render(5,720,960);assert portrait[:200,:,:3].max()==0
    assert b[:70,:,:3].max()==0,'Background is not pure black'
    Image.fromarray(portrait).save(out/'reference_orbits_portrait.png')
    for _ in range(8):draw(25)
    elapsed=[]
    for i in range(12):
        with ctx.query(time=True) as q:draw(25+i/60)
        elapsed.append(q.elapsed/1e6)
    # Contact sheet includes every quarter and intermediate long-run poses.
    sheet=Image.new('RGB',(1280,3*204),'black');d=ImageDraw.Draw(sheet)
    for i,t in enumerate(np.linspace(0,period,12,endpoint=False)):
        thumb=Image.fromarray(render(float(t))).convert('RGB').resize((320,180),Image.Resampling.LANCZOS)
        x=(i%4)*320;y=(i//4)*204;sheet.paste(thumb,(x,y));d.text((x+8,y+183),f'{t:.0f}s',fill='white')
    sheet.save(out/'reference_orbits_contact_sheet.png')
    if opts.animate:
        frames=[Image.fromarray(render(2+i/30,960,540)).convert('RGB') for i in range(180)]
        frames[0].save(out/'reference_orbits_realtime.gif',save_all=True,append_images=frames[1:],duration=33,loop=0)
    report={'gpu':ctx.info['GL_RENDERER'],'moving_glyphs':count,'disk_period_seconds':period,'corona_period_seconds':period/2,'trail_strength':trail,'runtime_gpu_texture_bytes':texture_bytes,'runtime_vertex_bytes':vertices.nbytes,'video_or_frame_sequence':False,'offscreen_runtime_targets':0,'fidelity_diagnostics':fidelity,'motion_in_user_marked_regions':motion,'timer_boundary_byte_changes':boundary,'full_revolution_byte_error':full_error,'partial_revolution_byte_changes':partial,'gpu_draw_median_ms_1280x720':float(np.median(elapsed)),'portrait_letterbox_black':True}
    (out/'reference_orbits_validation.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report,indent=2))
    glfw.terminate()
if __name__=='__main__':main()
