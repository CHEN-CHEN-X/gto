"""Bake reference glyphs and density-preserving closed paths; no animation frames.
Outputs: build/reference_orbits. Dependencies: numpy, Pillow (existing .pyenv).
"""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"build/reference_orbits"
OUT.mkdir(parents=True,exist_ok=True)
from pathlib import Path
import sys
sys.path.insert(0,str(ROOT/'.pyenv'))
import numpy as np
from PIL import Image,ImageFilter,ImageDraw
root=ROOT
im=Image.open(root/'src/main/resources/assets/gto_menu/textures/reference.png').convert('RGB')
a=np.asarray(im).astype(float)
blur=np.asarray(im.filter(ImageFilter.GaussianBlur(3))).astype(float)
mask=(a.max(2)>135)&((a-blur).max(2)>30)&(a[:,:,2]>100)&(a.max(2)-a.min(2)>55)
# Join broken strokes belonging to one glyph, retaining gaps between digits.
mask=np.asarray(Image.fromarray(mask.astype('uint8')*255).filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.MinFilter(3)))>0
seen=np.zeros(mask.shape,bool);h,w=mask.shape;components=[];labels=np.zeros(mask.shape,np.uint16)
for yy,xx in zip(*np.where(mask)):
 if seen[yy,xx]:continue
 stack=[(int(yy),int(xx))];seen[yy,xx]=True;coords=[]
 while stack:
  y,x=stack.pop();coords.append((y,x))
  for dy,dx in ((0,1),(0,-1),(1,0),(-1,0)):
   ny,nx=y+dy,x+dx
   if 0<=ny<h and 0<=nx<w and mask[ny,nx] and not seen[ny,nx]:seen[ny,nx]=True;stack.append((ny,nx))
 if len(coords)<5:continue
 coords=np.array(coords);lo=coords.min(0);hi=coords.max(0);hh,ww=hi-lo+1
 if ww<=50 and hh<=50 and ww>=3 and hh>=3 and max(hh,ww)/min(hh,ww)<4.5 and len(coords)>ww*hh*.10:
  components.append((int(lo[1]),int(lo[0]),int(hi[1]+1),int(hi[0]+1)))
  labels[coords[:,0],coords[:,1]]=len(components)
draw=im.copy();d=ImageDraw.Draw(draw)
for box in components:d.rectangle(box,outline=(40,255,30))
draw.resize((1280,720)).save(OUT/'detected_digits.png')
np.save(OUT/'digit_boxes.npy',np.array(components))
Image.fromarray(mask.astype('uint8')*255).save(OUT/'digit_strokes.png')
np.savez_compressed(OUT/'digit_labels.npz',labels=labels)
print('Extracted glyph candidates:',len(components))

"""Extract reference glyph emission and fit closed paths; no animated frames."""
from pathlib import Path
import sys, gzip, struct, json
sys.path.insert(0,str(ROOT/'.pyenv'))
import numpy as np
from PIL import Image,ImageFilter,ImageDraw

im=Image.open(ROOT/'src/main/resources/assets/gto_menu/textures/reference.png').convert('RGB')
src=np.asarray(im,dtype=float)/255.;h,w=src.shape[:2]
boxes=np.load(OUT/'digit_boxes.npy');n=len(boxes)
centers=(boxes[:,:2]+boxes[:,2:])/2
points=np.stack([(centers[:,0]-w/2)/h,(h/2-centers[:,1])/h],axis=1)
cs,sn=np.cos(.26),np.sin(.26)
local=np.stack([cs*points[:,0]+sn*(points[:,1]+.015),-sn*points[:,0]+cs*(points[:,1]+.015)],axis=1)
radius=np.linalg.norm(local/np.array([1,.30]),axis=1)
theta=np.arctan2(local[:,1]/.30,local[:,0])
corona_r=np.linalg.norm(points-np.array([0,.015]),axis=1)
sizes=boxes[:,2:]-boxes[:,:2]
corona=(corona_r>.19)&(corona_r<.36)&(local[:,1]>-.135)&(np.abs(local[:,1]+.043)>.022)
active=np.ones(n,bool)
for i,(x0,y0,x1,y1) in enumerate(boxes):
    patch=src[y0:y1,x0:x1]
    # White crossing flares are illumination, not binary glyphs. Their shape
    # remains on the stationary plate instead of orbiting as bright fragments.
    if patch.min(axis=2).mean()>.12 and corona_r[i]<.40:active[i]=False
    if .211<corona_r[i]<.253 and sizes[i].max()<29:active[i]=True

def disk_family(a):
    keys=np.array([0.,.493,.704,.95,1.4])
    cx=np.interp(a,keys,[0,-.036,-.093,-.025,0])
    cy=np.interp(a,keys,[.0,.031,-.012,.030,.035])
    b=np.interp(a,keys,[0,.100,.153,.270,.405])
    return np.array([cx,cy]),b

def fitted_disk(point):
    lo,hi=.01,1.8
    for _ in range(40):
        a=(lo+hi)/2;c,b=disk_family(a)
        radius=np.linalg.norm((point-c)/[a,b])
        if radius>1:lo=a
        else:hi=a
    a=(lo+hi)/2;c,b=disk_family(a)
    return c,a,b

# Digit footprints are removed from a stationary scene plate. Their positive
# emission is stored once in an atlas and rendered as moving, original sprites.
# An owner map prevents overlapping extraction patches from doubling emission.
owner=np.full((h,w),-1,np.int32);distance=np.full((h,w),1e9)
padding=4
patches=[]
for i,(x0,y0,x1,y1) in enumerate(boxes):
    x0=max(0,x0-padding);y0=max(0,y0-padding);x1=min(w,x1+padding);y1=min(h,y1+padding)
    patches.append((x0,y0,x1,y1))
    yy,xx=np.mgrid[y0:y1,x0:x1]
    d=((xx-centers[i,0])/max(3,(x1-x0)/2))**2+((yy-centers[i,1])/max(3,(y1-y0)/2))**2
    old=distance[y0:y1,x0:x1];pick=d<old
    old[pick]=d[pick];owner[y0:y1,x0:x1][pick]=i
plate=src.copy()
smooth=np.asarray(im.filter(ImageFilter.GaussianBlur(9)),dtype=float)/255.
labels=np.load(OUT/'digit_labels.npz')['labels']
# A local low percentile removes the bright stroke while retaining its faint
# surrounding light. Restrict the operation to extracted glyph footprints.
for i,(x0,y0,x1,y1) in enumerate(patches):
    if not active[i]:continue
    patch=src[y0:y1,x0:x1]
    xx0=max(0,x0-12);xx1=min(w,x1+12);yy0=max(0,y0-12);yy1=min(h,y1+12)
    floor=np.quantile(src[yy0:yy1,xx0:xx1].reshape(-1,3),.25,axis=0)
    replacement=np.minimum(smooth[y0:y1,x0:x1]*.38,floor[None,None,:]*1.8+.008)
    mask=owner[y0:y1,x0:x1]==i
    p=plate[y0:y1,x0:x1]
    local_mask=Image.fromarray(np.uint8(labels[y0:y1,x0:x1]==i+1)*255)
    local_mask=local_mask.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.GaussianBlur(1.0))
    feather=np.asarray(local_mask,dtype=float)/255.
    candidate=np.maximum(patch-replacement,0)*feather[:,:,None]
    p[mask]=patch[mask]-candidate[mask]
emission=np.maximum(src-plate,0)
# The original glow provides a fixed visibility mask, including the black
# shadow and the upper/lower silhouettes. Sprites never drag those shapes.
glow=np.asarray(im.filter(ImageFilter.GaussianBlur(5)),dtype=float).max(2)/255.
visibility=np.clip((glow-.012)/.045,0,1)
visibility=visibility*visibility*(3-2*visibility)
alpha=np.uint8(visibility*255+.5)
plate_rgba=np.dstack([np.uint8(plate*255+.5),alpha])
Image.fromarray(plate_rgba).save(OUT/'orbit_plate.png',optimize=True)
atlas=np.zeros((1024,2048,4),np.uint8)
records=[];px=py=row_h=0
for i,(x0,y0,x1,y1) in enumerate(patches):
    ww,hh=x1-x0,y1-y0
    if px+ww+2>2048:px=0;py+=row_h+2;row_h=0
    if py+hh+2>1024:raise RuntimeError('Sprite atlas overflow')
    rgb=emission[y0:y1,x0:x1].copy()
    rgb[owner[y0:y1,x0:x1]!=i]=0
    atlas[py:py+hh,px:px+ww,:3]=np.uint8(rgb*255+.5)
    atlas[py:py+hh,px:px+ww,3]=255
    if corona[i]:
        c=np.array([0,.015]);a=b=corona_r[i];roll=0.;kind=1.
        q=points[i]-c;t=np.arctan2(q[1],q[0]);speed=2.
    else:
        c0,a,b=fitted_disk(local[i])
        v=(local[i]-c0)/[a,b];scale=np.linalg.norm(v);a*=scale;b*=scale
        t=np.arctan2(v[1],v[0]);roll=.26;kind=0.;speed=1.
        c=np.array([cs*c0[0]-sn*c0[1],sn*c0[0]+cs*c0[1]-.015])
    tangent=np.array([-a*np.sin(t),b*np.cos(t)])
    base_angle=np.arctan2(tangent[1],tangent[0])+roll
    records.extend([*c,a,b,t,roll,speed,kind,px/2048,py/1024,ww/2048,hh/1024,ww/h,hh/h,base_angle,np.linalg.norm(tangent)])
    px+=ww+2;row_h=max(row_h,hh)
Image.fromarray(atlas).save(OUT/'orbit_glyphs.png',optimize=True)
records=np.array(records,dtype=float).reshape(n,16)
row_ids=np.where(records[:,7]>.5,32+np.clip(np.floor((records[:,2]-.19)/.01),0,31),np.clip(np.floor(records[:,2]/.04),0,31)).astype(int)
L=2048
lut=np.zeros((64,L,4),np.uint8)
phase_theta=(records[:,4]+np.pi)/(2*np.pi)
initial_size=np.sqrt(records[:,12]*records[:,13])*h
for group in range(64):
    members=np.flatnonzero((row_ids==group)&active)
    if len(members)<4:
        members=np.flatnonzero((np.abs(row_ids-group)<=1)&active)
    hist=np.zeros(L);sums=np.zeros(L)
    for j in members:
        k=int(phase_theta[j]*L)%L
        hist[k]+=1;sums[k]+=initial_size[j]
    freq=np.fft.rfftfreq(L);kernel=np.exp(-2*np.pi*np.pi*28**2*freq**2)
    smooth_hist=np.maximum(np.fft.irfft(np.fft.rfft(hist)*kernel,n=L),0)
    smooth_sum=np.maximum(np.fft.irfft(np.fft.rfft(sums)*kernel,n=L),0)
    sizes_at_angle=smooth_sum/np.maximum(smooth_hist,1e-5)
    mean=float(initial_size[members].mean()) if len(members) else 12.
    sizes_at_angle=np.where(smooth_hist>max(smooth_hist.max()*.015,1e-5),sizes_at_angle,mean)
    density=smooth_hist+max(smooth_hist.mean()*.15,1e-5)
    cdf=np.r_[0,np.cumsum(density)];cdf/=cdf[-1]
    angular_edges=np.arange(L+1)/L
    phases=(np.arange(L)+.5)/L
    inverse=np.interp(phases,cdf,angular_edges)
    lut_sizes=np.interp(inverse,(np.arange(L)+.5)/L,sizes_at_angle)
    qt=np.uint16(np.clip(inverse,0,1)*65535+.5)
    qs=np.uint16(np.clip(lut_sizes/64,0,1)*65535+.5)
    lut[group,:,0]=qt//256;lut[group,:,1]=qt%256;lut[group,:,2]=qs//256;lut[group,:,3]=qs%256
    for j in np.flatnonzero(row_ids==group):
        records[j,4]=np.interp(phase_theta[j],angular_edges,cdf)
        records[j,7]=group
        records[j,15]=np.interp(phase_theta[j],(np.arange(L)+.5)/L,sizes_at_angle)
Image.fromarray(lut).save(OUT/'orbit_lut.png',optimize=True)
records=records[active].ravel().tolist();rendered_count=int(active.sum())
with gzip.open(OUT/'orbit_instances.bin.gz','wb') as f:
    f.write(struct.pack('>II',0x47544F31,rendered_count));f.write(struct.pack('>'+str(len(records))+'f',*records))

print(json.dumps({'glyph_candidates':n,'moving_glyphs':rendered_count,'corona_glyphs':int((corona&active).sum()),'plate_bytes':(OUT/'orbit_plate.png').stat().st_size,'glyph_bytes':(OUT/'orbit_glyphs.png').stat().st_size,'instance_bytes':(OUT/'orbit_instances.bin.gz').stat().st_size,'sprite_atlas_used_height':int(py+row_h)}))
