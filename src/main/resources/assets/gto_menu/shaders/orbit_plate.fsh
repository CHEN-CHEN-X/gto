#version 150 core
uniform sampler2D uArtwork;
uniform sampler2D uBrightMaterial;
uniform sampler2D uDarkMaterial;
uniform vec2 uResolution;
uniform float uSourceAspect;
uniform vec4 uBrightRegions[34];
uniform vec4 uDarkRegions[34];
uniform vec2 uRegionStrength;
uniform vec3 uMouse;
uniform vec4 uParticles[12];
in vec2 vUv;
out vec4 fragColor;

float regionDistance(vec2 p,vec4 region){
 vec2 delta=(p-region.xy)/1.1;
 vec2 q=vec2(dot(delta,region.zw)/.035,dot(delta,vec2(-region.w,region.z))/.024);
 return dot(q,q);
}
// Each dark patch is a tapered bundle of narrow ellipses. The uploaded
// tangent follows the source-guided track continuously, including its seam.
float darkDistance(vec2 p,vec4 region,int index){
 vec2 delta=(p-region.xy)/1.1;
 vec2 q=vec2(dot(delta,region.zw),dot(delta,vec2(-region.w,region.z)));
 float result=1.e6;
 for(int strand=0;strand<5;strand++){
  float seed=float(index*5+strand);
  float length=mix(.035,.068,fract(sin(seed*12.9898+3.)*43758.5453));
  float width=mix(.004,.007,fract(sin(seed*7.13+1.)*14375.13));
  float offset=(float(strand)-2.)*.006;
  float shift=.012*sin(seed*2.3);
  // Slightly curved, staggered strands, rather than a rectangular footprint.
  vec2 e=vec2((q.x-shift)/length,(q.y-offset)/width);
  result=min(result,dot(e,e));
 }
 return result;
}
vec2 regionWeights(vec2 p){
 float b=1.e6,d=1.e6;
 for(int i=0;i<34;i++){
  b=min(b,regionDistance(p,uBrightRegions[i]));
  d=min(d,darkDistance(p,uDarkRegions[i],i));
 }
 // Bright supports have stable priority. Dark patches fade out before entering
 // them, so crossing patches never exchange ownership or cut each other up.
 // Supports remain disjoint: bright needs b<1, dark needs b>1.1.
 float bright=1.-smoothstep(0.,1.,b);
 float dark=(1.-smoothstep(0.,1.,d))*smoothstep(1.1,2.3,b);
 // Reveal both materials along the entire closed orbit.
 return vec2(bright,dark);
}
float rectGlyph(vec2 p,vec2 b){return max(abs(p.x)-b.x,abs(p.y)-b.y);}
vec3 cursorParticles(vec2 p){
 vec3 light=vec3(0.);
 float co=cos(.26),si=sin(.26);
 for(int i=0;i<12;i++){
  vec4 particle=uParticles[i];
  if(particle.w<.5)continue;
  vec2 v=particle.xy+vec2(0.,.015);
  vec2 local=vec2(co*v.x+si*v.y,-si*v.x+co*v.y);
  float a=particle.z*.10,cs=cos(a),sn=sin(a);
  vec2 orbit=vec2(local.x,local.y/.30);
  orbit=mat2(cs,sn,-sn,cs)*orbit;
  local=vec2(orbit.x,orbit.y*.30);
  vec2 pos=vec2(co*local.x-si*local.y,si*local.x+co*local.y-.015);
  vec2 tangent=normalize(vec2(-co*orbit.y-si*.30*orbit.x,-si*orbit.y+co*.30*orbit.x));
  vec2 delta=p-pos;
  vec2 uv=vec2(dot(delta,tangent),dot(delta,vec2(-tangent.y,tangent.x)));
  float outer=rectGlyph(uv,vec2(.0042,.0065));
  float inner=rectGlyph(uv,vec2(.0020,.0041));
  float glyph=particle.w<1.5 ? max(outer,-inner) : rectGlyph(uv,vec2(.0013,.0065));
  float ink=1.-smoothstep(.0004,.0016,glyph);
  float glow=exp(-length(uv/vec2(.009,.013))*2.);
  float fade=(1.-smoothstep(1.3,3.5,particle.z))*smoothstep(0.,.15,particle.z);
  light+=mix(vec3(.05,.75,1.),vec3(.85,.08,1.),float(i%2))*(ink*.55+glow*.3)*fade;
 }
 return light;
}
void main(){
 float aspect=uResolution.x/uResolution.y;
 vec2 uv=(vUv-.5)*vec2(aspect/uSourceAspect,1.)/min(1.,aspect/uSourceAspect)+.5;
 vec2 p=(uv-.5)*vec2(uSourceAspect,1.);
 vec3 c=vec3(0);
 if(all(greaterThanEqual(uv,vec2(0)))&&all(lessThanEqual(uv,vec2(1)))){
  // Inverse sample warp gently enlarges and pushes nearby artwork outwards.
  // Fade the warp near the horizon to preserve the black center.
  vec2 delta=p-uMouse.xy;
  float guard=smoothstep(.23,.32,length(p-vec2(0.,.015)));
  float lens=(1.-smoothstep(0.,.12,length(delta)))*.065*uMouse.z*guard;
  vec2 sampled=p-delta*lens;
  vec2 sourceUv=vec2(uv.x,1.-uv.y);
  if(lens>0.) {
   sourceUv=sampled/vec2(uSourceAspect,1.)+.5;
   sourceUv.y=1.-sourceUv.y;
  }
  c=texture(uArtwork,sourceUv).rgb;
  vec2 regions=regionWeights(sampled);
  vec4 bright=texture(uBrightMaterial,sourceUv);
  vec4 dark=texture(uDarkMaterial,sourceUv);
  float hover=(1.-smoothstep(.025,.085,length(p-uMouse.xy)))*uMouse.z;
  c=mix(c,bright.rgb,bright.a*max(regions.x*clamp(uRegionStrength.x,0.,1.),hover));
  c=mix(c,dark.rgb,dark.a*regions.y*(1.-hover)*clamp(uRegionStrength.y,0.,1.));
 }
 // Keep the event horizon opaque even when an orbit passes behind it.
 vec2 horizon=p-vec2(0.,.015);
 float ry=-sin(.26)*p.x+cos(.26)*(p.y+.015);
 if(!(length(horizon)<.19 && ry>-.065))c+=cursorParticles(p);
 if(max(max(c.r,c.g),c.b)<3./255.)c=vec3(0);
 fragColor=vec4(c,1);
}
