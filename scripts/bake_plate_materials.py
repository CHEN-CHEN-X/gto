"""Bake aligned bright-digit and dark-line materials from the existing plate."""
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'.pyenv'))
import numpy as np
from PIL import Image,ImageFilter
assets=ROOT/'src/main/resources/assets/gto_menu/textures'
im=Image.open(assets/'orbit_plate.png').convert('RGB');rgb=np.array(im)
blur=np.array(im.filter(ImageFilter.GaussianBlur(3))).astype(float)
candidate=(rgb.max(2)>65)&((rgb.astype(float)-blur).max(2)>14)&(rgb[:,:,2]>45)
candidate=np.array(Image.fromarray(candidate.astype('uint8')*255).filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.MinFilter(3)))>0
h,w=candidate.shape;seen=np.zeros_like(candidate);digits=np.zeros_like(candidate);count=0
for yy,xx in zip(*np.where(candidate)):
 if seen[yy,xx]:continue
 stack=[(int(yy),int(xx))];seen[yy,xx]=True;coords=[]
 while stack:
  y,x=stack.pop();coords.append((y,x))
  for dy,dx in ((0,1),(0,-1),(1,0),(-1,0)):
   ny,nx=y+dy,x+dx
   if 0<=ny<h and 0<=nx<w and candidate[ny,nx] and not seen[ny,nx]:
    seen[ny,nx]=True;stack.append((ny,nx))
 a=np.array(coords);lo=a.min(0);hi=a.max(0);hh,ww=hi-lo+1
 if 8<=ww<=60 and 6<=hh<=48 and max(ww,hh)/min(ww,hh)<4 and len(a)>ww*hh*.12:
  digits[a[:,0],a[:,1]]=True;count+=1
# Protect known source digits as well as residual stationary glyphs from darkening.
labels_path=ROOT/'build/reference_orbits/digit_labels.npz'
if not labels_path.exists():
 import runpy
 runpy.run_path(str(ROOT/'scripts/bake_reference_orbits.py'),run_name='__main__')
labels=np.load(labels_path)['labels']
def feather(mask,radius,blur):
 return np.array(Image.fromarray(mask.astype('uint8')*255).filter(ImageFilter.MaxFilter(radius)).filter(ImageFilter.GaussianBlur(blur)))/255.
# Include extracted large source glyphs even when their remaining plate pixels
# are too dim for residual detection. Restore their original emission on reveal.
boxes=np.load(ROOT/'build/reference_orbits/digit_boxes.npy')
sizes=boxes[:,2:]-boxes[:,:2]
large_ids=np.flatnonzero((sizes[:,0]>=8)&(sizes[:,1]>=6))+1
large=np.isin(labels,large_ids)
bright=feather(digits|large,5,1.0)
source=np.array(Image.open(assets/'reference.png').convert('RGB'))
bright_rgb=np.maximum(rgb.astype(float)*2.4,source.astype(float)*1.15)
protected=feather(digits|(labels>0),9,1.5)
line=np.clip(1-protected,0,1)
# Alpha confines each material to its own content; transparent texels retain RGB
# to avoid dark fringes with linear filtering.
Image.fromarray(np.dstack([np.minimum(bright_rgb,255).astype('uint8'),(bright*255).astype('uint8')])).save(assets/'plate_bright_digits.png',optimize=True)
Image.fromarray(np.dstack([(rgb*.15).astype('uint8'),(line*255).astype('uint8')])).save(assets/'plate_dark_lines.png',optimize=True)
out=ROOT/'build/reference_orbits';out.mkdir(parents=True,exist_ok=True)
Image.fromarray((bright*255).astype('uint8')).save(out/'bright_digits_mask.png')
Image.fromarray((line*255).astype('uint8')).save(out/'dark_lines_mask.png')
assert line[digits].max()<.08
print('Stationary digit components:',count)
