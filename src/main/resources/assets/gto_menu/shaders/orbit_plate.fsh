#version 150 core
uniform sampler2D uArtwork;
uniform sampler2D uBrightMaterial;
uniform sampler2D uDarkMaterial;
uniform vec2 uResolution;
uniform float uSourceAspect;
uniform vec4 uBrightRegions[34];
uniform vec4 uDarkRegions[34];
uniform vec2 uRegionStrength;
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
  float width=mix(.0018,.0038,fract(sin(seed*7.13+1.)*14375.13));
  float offset=(float(strand)-2.)*.006;
  float shift=.012*sin(seed*2.3);
  // Slightly curved, staggered strands, rather than a rectangular footprint.
  vec2 e=vec2((q.x-shift)/length,(q.y-offset-.0015*sin(q.x*65.+seed))/width);
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
 // Disjoint supports with a neutral gap. Bright and dark can never both
 // affect one pixel, including where neighbouring tracks approach each other.
 float bright=(1.-smoothstep(.16,1.,b))*smoothstep(.05,.20,d-b);
 float dark=(1.-smoothstep(.16,1.,d))*smoothstep(.05,.20,b-d);
 // Reveal both materials along the entire closed orbit.
 return vec2(bright,dark);
}
void main(){
 float aspect=uResolution.x/uResolution.y;
 vec2 uv=(vUv-.5)*vec2(aspect/uSourceAspect,1.)/min(1.,aspect/uSourceAspect)+.5;
 vec2 p=(uv-.5)*vec2(uSourceAspect,1.);
 vec3 c=vec3(0);
 if(all(greaterThanEqual(uv,vec2(0)))&&all(lessThanEqual(uv,vec2(1)))){
  c=texture(uArtwork,vec2(uv.x,1.-uv.y)).rgb;
  vec2 regions=regionWeights(p);
  vec2 sourceUv=vec2(uv.x,1.-uv.y);
  vec4 bright=texture(uBrightMaterial,sourceUv);
  vec4 dark=texture(uDarkMaterial,sourceUv);
  c=mix(c,bright.rgb,bright.a*regions.x*clamp(uRegionStrength.x,0.,1.));
  c=mix(c,dark.rgb,dark.a*regions.y*clamp(uRegionStrength.y,0.,1.));
 }
 if(max(max(c.r,c.g),c.b)<3./255.)c=vec3(0);
 fragColor=vec4(c,1);
}
