#version 150 core
uniform sampler2D uGlyphs,uPlate;
uniform float uOpacity;
uniform float uSourceAspect;
in vec2 glyphUv;
in vec2 sceneUv;
out vec4 fragColor;
void main(){
 if(any(lessThan(sceneUv,vec2(0)))||any(greaterThan(sceneUv,vec2(1))))discard;
 float visibility=texture(uPlate,sceneUv).a;
 vec2 p=(sceneUv-vec2(.5))*vec2(uSourceAspect,-1.);
 vec2 q=p-vec2(0.,.015);
 float ry=-sin(.26)*p.x+cos(.26)*(p.y+.015);
 if(length(q)<.19&&ry>-.065)discard;
 fragColor=vec4(texture(uGlyphs,glyphUv).rgb*visibility*uOpacity,0.);
}
