#version 150 core
uniform sampler2D uArtwork;
uniform vec2 uResolution;
uniform vec3 uMouse;
uniform float uSourceAspect,uStarsTime,uStarsStrength;
in vec2 vUv;
out vec4 fragColor;
float rectGlyph(vec2 p,vec2 b){return max(abs(p.x)-b.x,abs(p.y)-b.y);}
float starHash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
vec3 binaryStars(vec2 p,float visibility){
 // Fixed cells and identities: only their individual brightness changes.
 vec2 grid=p/.055,cell=floor(grid),f=fract(grid);
 float seed=starHash(cell);
 if(seed>.576)return vec3(0.);
 vec2 center=vec2(.20+.60*starHash(cell+11.3),.20+.60*starHash(cell+47.1));
 vec2 q=(f-center)*.055;
 float size=mix(.0018,.0032,starHash(cell+8.7));
 vec2 uv=q/size;
 float outer=rectGlyph(uv,vec2(.55,1.));
 float inner=rectGlyph(uv,vec2(.25,.60));
 float glyph=starHash(cell+31.4)<.5 ? max(outer,-inner) : rectGlyph(uv,vec2(.20,1.));
 float edge=max(fwidth(glyph),.08);
 float ink=1.-smoothstep(-edge,edge,glyph);
 float phase=starHash(cell+71.8)*6.2831853;
 float speed=mix(.20,.55,starHash(cell+93.2));
 float twinkle=.25+.75*(.5+.5*sin(uStarsTime*speed+phase));
 vec3 color=mix(vec3(.13,.50,.72),vec3(.55,.18,.70),starHash(cell+53.7));
 // Mouse light follows the fixed glyph centre, so a digit brightens as a unit.
 vec2 starCenter=(cell+center)*.055;
 float hover=(1.-smoothstep(.025,.13,length(starCenter-uMouse.xy)))*uMouse.z;
 float brightness=mix(twinkle,1.8,hover);
 return color*ink*brightness*uStarsStrength*visibility;
}
void main(){
 float aspect=uResolution.x/uResolution.y;
 vec2 uv=(vUv-.5)*vec2(aspect/uSourceAspect,1.)/min(1.,aspect/uSourceAspect)+.5;
 if(any(lessThan(uv,vec2(0)))||any(greaterThan(uv,vec2(1)))){fragColor=vec4(0);return;}
 vec2 p=(uv-.5)*vec2(uSourceAspect,1.);
 float alpha=texture(uArtwork,vec2(uv.x,1.-uv.y)).a;
 float visibility=1.-smoothstep(.015,.12,alpha);
 vec2 horizon=p-vec2(0.,.015);
 float ry=-sin(.26)*p.x+cos(.26)*(p.y+.015);
 if(length(horizon)<.23 && ry>-.10)visibility=0.;
 fragColor=vec4(binaryStars(p,visibility),0.);
}
