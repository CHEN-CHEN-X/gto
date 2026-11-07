#version 150 core
uniform sampler2D uGlyphs,uPlate;
uniform float uOpacity;
uniform float uSourceAspect;
in vec2 glyphUv;
in vec2 sceneUv;
flat in vec2 centerUv;
out vec4 fragColor;
void main(){
 if(any(lessThan(sceneUv,vec2(0)))||any(greaterThan(sceneUv,vec2(1))))discard;
 // Whole-glyph visibility: holes in the fixed plate must not erase individual
// strokes as a sprite passes between source digits. A small neighbourhood keeps
// it legible longer; the existing silhouette and event-horizon checks remain.
 vec2 stepUv=vec2(.010/uSourceAspect,.010);
 float visibility=texture(uPlate,centerUv).a;
 visibility=max(visibility,texture(uPlate,centerUv+vec2(stepUv.x,0)).a);
 visibility=max(visibility,texture(uPlate,centerUv-vec2(stepUv.x,0)).a);
 visibility=max(visibility,texture(uPlate,centerUv+vec2(0,stepUv.y)).a);
 visibility=max(visibility,texture(uPlate,centerUv-vec2(0,stepUv.y)).a);
 visibility=smoothstep(.0,.35,visibility);
 vec2 p=(sceneUv-vec2(.5))*vec2(uSourceAspect,-1.);
 vec2 q=p-vec2(0.,.015);
 float ry=-sin(.26)*p.x+cos(.26)*(p.y+.015);
 if(length(q)<.19&&ry>-.065)discard;
 fragColor=vec4(texture(uGlyphs,glyphUv).rgb*visibility*uOpacity,0.);
}
