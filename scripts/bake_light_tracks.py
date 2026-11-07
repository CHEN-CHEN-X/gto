"""Trace lower digit bands from the reference; emit equal-distance closed paths."""
from pathlib import Path
import sys,gzip,struct
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'.pyenv'))
import numpy as np
from PIL import Image,ImageDraw

def main():
 out=ROOT/'build/reference_orbits';out.mkdir(parents=True,exist_ok=True)
 boxes_path=out/'digit_boxes.npy'
 if not boxes_path.exists():
  import runpy
  runpy.run_path(str(ROOT/'scripts/bake_reference_orbits.py'),run_name='__main__')
 boxes=np.load(boxes_path);centers=(boxes[:,:2]+boxes[:,2:])/2
 im=Image.open(ROOT/'src/main/resources/assets/gto_menu/textures/reference.png').convert('RGB')
 w,h=im.size;co,si=np.cos(.26),np.sin(.26)
 p=np.stack([(centers[:,0]-w/2)/h,(h/2-centers[:,1])/h],axis=1)
 local=np.stack([co*p[:,0]+si*(p[:,1]+.015),-si*p[:,0]+co*(p[:,1]+.015)],axis=1)
 area=np.sqrt(np.prod(boxes[:,2:]-boxes[:,:2],axis=1))
 radii=[.493,.55,.60,.65,.704,.79,.88,.98];tracks=[]
 theta=np.linspace(-np.pi,np.pi,1024,endpoint=False)
 def model(a):
  k=[0,.493,.704,.95,1.4]
  return np.array([np.interp(a,k,[0,-.036,-.093,-.025,0]),np.interp(a,k,[0,.031,-.012,.030,.035])]),np.interp(a,k,[0,.100,.153,.270,.405])
 for a in radii:
  center,b=model(a);v=(local-center)/[a,b]
  rr=np.linalg.norm(v,axis=1);aa=np.arctan2(v[:,1],v[:,0])
  keep=(np.abs(rr-1)<.14)&(p[:,1]<-.025)
  ca,cr,cw=aa[keep],rr[keep],area[keep]
  order=np.argsort(cr);ca,cr,cw=ca[order],cr[order],cw[order]
  offsets=np.zeros(len(theta))
  for j,t in enumerate(theta):
   delta=(ca-t+np.pi)%(2*np.pi)-np.pi
   weight=np.exp(-.5*(delta/.11)**2-.5*((cr-1)/.060)**2)*cw
   if weight.sum()>1.:
    idx=np.searchsorted(np.cumsum(weight),weight.sum()/2)
    offsets[j]=np.clip(cr[min(idx,len(cr)-1)]-1,-.06,.06)*min(1.,weight.sum()/15.)
  freq=np.fft.rfftfreq(len(theta));kernel=np.exp(-2*np.pi*np.pi*20**2*freq**2)
  offsets=np.fft.irfft(np.fft.rfft(offsets)*kernel,n=len(theta))
  xy=center[:,None]+np.array([a*np.cos(theta),b*np.sin(theta)])*(1+offsets)
  world=np.stack([co*xy[0]-si*xy[1],si*xy[0]+co*xy[1]-.015],axis=1)
  closed=np.vstack([world,world[:1]])
  distance=np.r_[0,np.cumsum(np.linalg.norm(np.diff(closed,axis=0),axis=1))]
  equally=np.arange(512)/512*distance[-1]
  tracks.append(np.stack([np.interp(equally,distance,closed[:,0]),np.interp(equally,distance,closed[:,1])],axis=1))
 data=np.array(tracks,dtype='>f4')
 with gzip.GzipFile(filename=str(out/'light_tracks.bin.gz'),mode='wb',mtime=0) as f:
  f.write(struct.pack('>III',0x47544C31,8,512));f.write(data.tobytes())
 debug=im.resize((1280,720));draw=ImageDraw.Draw(debug)
 for i,path in enumerate(tracks):
  points=[]
  for x,y in np.vstack([path,path[:1]]):
   if y<-.035:points.append((float(x*720+640),float(360-y*720)))
   else:
    if len(points)>1:draw.line(points,fill=(255,220,40) if i%2==0 else (50,255,90),width=1)
    points=[]
  if len(points)>1:draw.line(points,fill=(255,220,40) if i%2==0 else (50,255,90),width=1)
 debug.save(out/'light_tracks_debug.png')
 print('Baked 8 source-guided paths (512 equal-distance samples each).')
if __name__=='__main__':main()
