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
    bright_gain=float(re.search(r'ARTWORK_SPOT_BRIGHT_GAIN = ([0-9.]+)f',config)[1])
    dark_drop=float(re.search(r'ARTWORK_SPOT_DARK_DROP = ([0-9.]+)f',config)[1])
    spot_period=float(re.search(r'ARTWORK_SPOT_ORBIT_SECONDS = ([0-9.]+)f',config)[1])
    assert glfw.init()
    glfw.window_hint(glfw.CONTEXT_VERSION_MAJOR,3);glfw.window_hint(glfw.CONTEXT_VERSION_MINOR,2)
    glfw.window_hint(glfw.OPENGL_PROFILE,glfw.OPENGL_CORE_PROFILE);glfw.window_hint(glfw.VISIBLE,False)
    win=glfw.create_window(1,1,'Reference-orbit validation',None,None);assert win
    glfw.make_context_current(win);ctx=moderngl.create_context(require=320)
    plate=ctx.program(vertex_shader=read('shaders/fsquad.vsh'),fragment_shader=read('shaders/orbit_plate.fsh'))
    glyph=ctx.program(vertex_shader=read('shaders/glyph_orbit.vsh'),fragment_shader=read('shaders/glyph_orbit.fsh'))
    pvao=ctx.vertex_array(plate,[])
    stars_prog=ctx.program(vertex_shader=read('shaders/fsquad.vsh'),fragment_shader=read('shaders/binary_stars.fsh'))
    stars_vao=ctx.vertex_array(stars_prog,[])
    stars_prog['uArtwork']=0
    data=gzip.open(assets/'textures/orbit_instances.bin.gz','rb').read()
    magic,count=struct.unpack('>II',data[:8]);assert magic==0x47544F31
    instances=np.frombuffer(data[8:],dtype='>f4').astype('f4').reshape(count,16)
    assert np.isfinite(instances).all() and count>500
    vertices=np.repeat(instances,6,axis=0);vbo=ctx.buffer(vertices.tobytes())
    vao=ctx.vertex_array(glyph,[(vbo,'4f 4f 4f 4f','aOrbit','aMotion','aAtlas','aSize')])
    textures=[];texture_bytes=0
    for unit,name in enumerate(['orbit_plate.png','orbit_glyphs.png','orbit_lut.png','plate_bright_digits.png','plate_dark_lines.png']):
        im=Image.open(assets/'textures'/name).convert('RGBA')
        tex=ctx.texture(im.size,4,im.tobytes());tex.filter=(moderngl.LINEAR,moderngl.LINEAR)
        tex.repeat_x=tex.repeat_y=False;tex.use(unit);textures.append(tex);texture_bytes+=im.width*im.height*4
        if unit==0:sw,sh=im.size
    plate['uBrightMaterial']=3;plate['uDarkMaterial']=4;plate['uArtwork']=0;glyph['uPlate']=0;glyph['uGlyphs']=1;glyph['uOrbitLut']=2
    track_data=gzip.open(assets/'textures/light_tracks.bin.gz','rb').read()
    assert struct.unpack('>III',track_data[:12])==(0x47544C31,8,512)
    light_tracks=np.frombuffer(track_data[12:],dtype='>f4').astype('f4').reshape(8,512,2)
    def region_values(t,offset):
        counts=np.array([5,5,4,4,4,4,4,4])
        rows=np.repeat(np.arange(8),counts)
        phases=np.concatenate([np.arange(n)/n for n in counts])
        offsets=offset/np.repeat(counts,counts)
        x=((t/spot_period+rows*.38196601125+phases+offsets)%1)*512
        i=np.floor(x).astype(int);f=(x-i).astype('f4')[:,None];row=rows
        p0=light_tracks[row,(i-1)%512];p1=light_tracks[row,i%512]
        p2=light_tracks[row,(i+1)%512];p3=light_tracks[row,(i+2)%512]
        a=-p0+p2;b=2*p0-5*p1+4*p2-p3;c=-p0+3*p1-3*p2+p3
        position=.5*(2*p1+a*f+b*f*f+c*f*f*f)
        tangent=.5*(a+2*b*f+3*c*f*f)
        tangent/=np.maximum(np.linalg.norm(tangent,axis=1,keepdims=True),1e-7)
        return np.concatenate([position,tangent],axis=1).astype('f4')
    def upload_regions(program,t):
        program['uBrightRegions'].write(region_values(t,0).tobytes())
        program['uDarkRegions'].write(region_values(t,.5).tobytes())
    target=None;fbo=None;size=None
    def draw(t,w=1280,h=720,sprites=True,lights=True,stars=False):
        nonlocal target,fbo,size
        if size!=(w,h):
            if fbo is not None:fbo.release();target.release()
            target=ctx.texture((w,h),4);fbo=ctx.framebuffer([target]);size=(w,h)
        fbo.use();ctx.viewport=(0,0,w,h)
        for p in [plate,glyph,stars_prog]:p['uResolution']=(w,h);p['uSourceAspect']=sw/sh
        upload_regions(plate,t)
        plate['uRegionStrength']=(bright_gain,dark_drop) if lights and speed>0 else (0.,0.)
        ctx.disable(moderngl.BLEND);pvao.render(vertices=3)
        if stars:
            stars_prog['uStarsTime']=t;stars_prog['uStarsStrength']=.4
            ctx.enable(moderngl.BLEND);ctx.blend_equation=moderngl.FUNC_ADD
            ctx.blend_func=(moderngl.ONE,moderngl.ONE,moderngl.ZERO,moderngl.ONE)
            stars_vao.render(vertices=3)
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
    # Exercise real shaders with a pointer and an emitted 0/1 particle.
    initial=render(1)
    plate['uMouse']=(-.4,-.20,1.);glyph['uMouse']=(-.4,-.20,1.)
    hover=render(1)
    assert np.abs(hover.astype(int)-initial.astype(int)).max()>10, 'Mouse hover has no visible effect'
    emit=np.zeros((12,4),dtype='f4');emit[0]=(-.4,-.20,.5,1.)
    plate['uParticles'].write(emit.tobytes())
    moved=render(1)
    assert np.abs(moved.astype(int)-hover.astype(int)).max()>5, 'Mouse particle is not rendered'
    emit[0,2]=1.5;plate['uParticles'].write(emit.tobytes())
    later=render(1)
    assert np.abs(later.astype(int)-moved.astype(int)).max()>5, 'Mouse particle did not travel'
    Image.fromarray(moved).save(out/'cursor_effect_preview.png')
    plate['uMouse']=(0.,0.,0.);glyph['uMouse']=(0.,0.,0.)
    plate['uParticles'].write(np.zeros((12,4),dtype='f4').tobytes())
    a=render(0);b=render(5);quarter=render(period/4);half=render(period/2)
    for name,frame in [('start',a),('preview',b),('quarter',quarter),('half',half)]:Image.fromarray(frame).save(out/f'reference_orbits_{name}.png')
    reference=Image.open(assets/'textures/reference.png').convert('RGBA')
    reftex=ctx.texture(reference.size,4,reference.tobytes());reftex.filter=(moderngl.LINEAR,moderngl.LINEAR);reftex.repeat_x=reftex.repeat_y=False
    reftex.use(0);draw(0,sprites=False,lights=False)
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
    def plate_only(t,lights=True):
        draw(t,sprites=False,lights=lights)
        return np.frombuffer(fbo.read(components=4,alignment=1),np.uint8).reshape(720,1280,4)[::-1].copy()
    # Stars stay in fixed positions; time changes only their brightness.
    draw(0,sprites=False,lights=False,stars=True)
    stars0=np.frombuffer(fbo.read(components=4,alignment=1),np.uint8).reshape(720,1280,4)[::-1].copy()
    draw(8,sprites=False,lights=False,stars=True)
    stars8=np.frombuffer(fbo.read(components=4,alignment=1),np.uint8).reshape(720,1280,4)[::-1].copy()
    draw(0,sprites=False,lights=False,stars=False)
    no_stars=np.frombuffer(fbo.read(components=4,alignment=1),np.uint8).reshape(720,1280,4)[::-1].copy()
    delta0=stars0[:,:,:3].astype(int)-no_stars[:,:,:3].astype(int)
    delta8=stars8[:,:,:3].astype(int)-no_stars[:,:,:3].astype(int)
    changed0=(delta0.max(2)>1);changed8=(delta8.max(2)>1)
    assert changed0.sum()>20 and changed8.sum()>20,'Background stars are not visible'
    assert np.max(delta0)<=102 and np.max(delta8)<=102,'Stars exceed requested brightness'
    assert np.abs(stars0.astype(int)-stars8.astype(int)).max()>2,'Stars do not twinkle'
    stars_prog['uMouse']=(0.,.38,1.)
    draw(0,sprites=False,lights=False,stars=True)
    hovered=np.frombuffer(fbo.read(components=4,alignment=1),np.uint8).reshape(720,1280,4)[::-1].copy()
    hover_delta=hovered[:,:,:3].astype(int)-stars0[:,:,:3].astype(int)
    assert hover_delta.max()>5,'Mouse does not brighten background digits'
    assert hover_delta.min()>=0,'Mouse darkens background digits'
    yy,xx=np.mgrid[:720,:1280]
    dist=np.hypot((xx-640)/720, (360-yy)/720-.38)
    assert not (hover_delta.max(2)[dist>.15]>1).any(),'Mouse affects distant stars'
    stars_prog['uMouse']=(0.,0.,0.)
    oldstars=ctx.program(vertex_shader=read('shaders/fsquad.vsh'),fragment_shader=read('shaders/binary_stars.fsh').replace('seed>.576','seed>.288'))
    oldvao=ctx.vertex_array(oldstars,[])
    for k,v in [('uArtwork',0),('uResolution',(1280,720)),('uSourceAspect',sw/sh),('uStarsTime',0.),('uStarsStrength',.4),('uMouse',(0.,0.,0.))]:oldstars[k]=v
    ctx.disable(moderngl.BLEND);oldvao.render(vertices=3)
    old_image=np.frombuffer(fbo.read(components=4,alignment=1),np.uint8).reshape(720,1280,4)[::-1].copy()
    old_lit=(old_image[:,:,:3].max(2)>1).sum()
    assert changed0.sum()>old_lit*1.6,'Star density did not increase sufficiently'
    print('Star density pixel ratio:',float(changed0.sum()/old_lit))
    oldvao.release();oldstars.release()

    assert ((changed0|changed8)[:70]).sum()>0,'Upper background has no stars'
    assert not (changed0|changed8)[300:405,570:710].any(),'Stars enter event horizon'
    draw(0,stars=True)
    Image.fromarray(np.frombuffer(fbo.read(components=4,alignment=1),np.uint8).reshape(720,1280,4)[::-1].copy()).save(out/'binary_stars_preview.png')
    base=plate_only(0,False)
    samples=[plate_only(float(t)) for t in np.linspace(0,spot_period,24,endpoint=False)]
    max_delta=np.maximum.reduce([np.abs(frame[:,:,:3].astype(int)-base[:,:,:3].astype(int)).max(axis=2) for frame in samples])
    assert max_delta[:int(.5*720)].max()>5,'Upper orbit is still hidden'
    assert difference(plate_only(0),plate_only(spot_period))==0,'Light orbit does not close'
    fractions=[float((np.abs(frame[:,:,:3].astype(int)-base[:,:,:3].astype(int)).max(axis=2)>1).mean()) for frame in samples]
    assert max(fractions)<.08,'Light regions cover too much of the image'
    bright=max(int((frame[:,:,:3].astype(int)-base[:,:,:3].astype(int)).max()) for frame in samples)
    dark=min(int((frame[:,:,:3].astype(int)-base[:,:,:3].astype(int)).min()) for frame in samples)
    assert bright>5 and dark<-3,'Both brighter and darker regions are required'
    disabled=plate_only(spot_period/2,False)
    assert difference(base,disabled)==0,'Disabled lights must leave the previous plate unchanged'
    old_fragment='#version 150 core\nuniform sampler2D uArtwork;\nuniform vec2 uResolution;\nuniform float uSourceAspect;\nin vec2 vUv;\nout vec4 fragColor;\nvoid main(){\n float aspect=uResolution.x/uResolution.y;\n vec2 uv=(vUv-.5)*vec2(aspect/uSourceAspect,1.)/min(1.,aspect/uSourceAspect)+.5;\n vec3 c=vec3(0);\n if(all(greaterThanEqual(uv,vec2(0)))&&all(lessThanEqual(uv,vec2(1))))c=texture(uArtwork,vec2(uv.x,1.-uv.y)).rgb;\n // The source contains 1-2 byte compression noise in otherwise black areas.\n if(max(max(c.r,c.g),c.b)<3./255.)c=vec3(0);\n fragColor=vec4(c,1);\n}\n'
    old_prog=ctx.program(vertex_shader=read('shaders/fsquad.vsh'),fragment_shader=old_fragment)
    old_vao=ctx.vertex_array(old_prog,[])
    old_prog['uArtwork']=0;old_prog['uResolution']=(1280,720);old_prog['uSourceAspect']=sw/sh
    ctx.disable(moderngl.BLEND);old_vao.render(vertices=3)
    prior=np.frombuffer(fbo.read(components=4,alignment=1),np.uint8).reshape(720,1280,4)[::-1].copy()
    assert np.array_equal(base,prior),'Previous plate was not restored exactly'
    old_vao.release();old_prog.release()
    Image.fromarray(samples[0]).save(out/'localized_spots_start.png')
    Image.fromarray(samples[2]).save(out/'localized_spots_later.png')
    spot_checks={'upper_scene_max_byte_change':int(max_delta[:int(.535*720)].max()),'max_frame_changed_fraction':max(fractions),'max_brightening_bytes':bright,'max_darkening_bytes':dark,'disabled_plate_matches_previous':True}

    # Render the two supports themselves, independently of the artwork colours.
    debug_fragment=read('shaders/orbit_plate.fsh').replace('fragColor=vec4(c,1);','fragColor=vec4(regionWeights(p),0.,1.);')
    mask_program=ctx.program(vertex_shader=read('shaders/fsquad.vsh'),fragment_shader=debug_fragment)
    mask_vao=ctx.vertex_array(mask_program,[])
    mask_program['uResolution']=(1280,720);mask_program['uSourceAspect']=sw/sh
    overlap_pixels=0
    for moment in np.linspace(0,spot_period,48,endpoint=False):
        upload_regions(mask_program,float(moment));ctx.disable(moderngl.BLEND);mask_vao.render(vertices=3)
        masks=np.frombuffer(fbo.read(components=4,alignment=1),np.uint8).reshape(720,1280,4)[::-1]
        overlap_pixels+=int(((masks[:,:,0]>0)&(masks[:,:,1]>0)).sum())
    assert overlap_pixels==0,'Bright and dark regions overlap'
    spot_checks['bright_dark_overlap_pixels']=overlap_pixels
    spot_checks['source_guided_path_samples']=512
    upload_regions(mask_program,2.);mask_vao.render(vertices=3)
    masks=np.frombuffer(fbo.read(components=4,alignment=1),np.uint8).reshape(720,1280,4)[::-1].copy()
    overlay=base[:,:,:3].astype(float)
    for channel,colour in [(0,np.array([255,180,0])),(1,np.array([0,180,255]))]:
        alpha=masks[:,:,channel:channel+1]/255.*.65
        overlay=overlay*(1-alpha)+colour*alpha
    Image.fromarray(overlay.clip(0,255).astype('uint8')).save(out/'light_regions_debug.png')
    mask_vao.release();mask_program.release()
    if opts.animate:
        crops=[]
        for t in np.arange(0,12,1/15):
            frame=plate_only(float(t))
            crops.append(Image.fromarray(frame).crop((80,420,850,650)).convert('RGB'))
        crops[0].save(out/'stationary_digits_lighting.gif',save_all=True,append_images=crops[1:],duration=67,loop=0)


    boundary={}
    for sec in sorted(set([2,spot_period,30,60,120,256,period,period*2])):
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
    report={'gpu':ctx.info['GL_RENDERER'],'moving_glyphs':count,'disk_period_seconds':period,'corona_period_seconds':period/2,'trail_strength':trail,'runtime_gpu_texture_bytes':texture_bytes,'runtime_vertex_bytes':vertices.nbytes,'video_or_frame_sequence':False,'offscreen_runtime_targets':0,'fidelity_diagnostics':fidelity,'motion_in_user_marked_regions':motion,'localized_spot_checks':spot_checks,'spot_orbit_seconds':spot_period,'bright_gain':bright_gain,'dark_drop':dark_drop,'timer_boundary_byte_changes':boundary,'full_revolution_byte_error':full_error,'partial_revolution_byte_changes':partial,'gpu_draw_median_ms_1280x720':float(np.median(elapsed)),'portrait_letterbox_black':True}
    (out/'reference_orbits_validation.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report,indent=2))
    glfw.terminate()
if __name__=='__main__':main()
