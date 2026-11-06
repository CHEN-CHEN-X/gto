#version 150 core
uniform sampler2D uArtwork;
uniform vec2 uResolution;
uniform float uSourceAspect;
in vec2 vUv;
out vec4 fragColor;
void main(){
 float aspect=uResolution.x/uResolution.y;
 vec2 uv=(vUv-.5)*vec2(aspect/uSourceAspect,1.)/min(1.,aspect/uSourceAspect)+.5;
 vec3 c=vec3(0);
 if(all(greaterThanEqual(uv,vec2(0)))&&all(lessThanEqual(uv,vec2(1))))c=texture(uArtwork,vec2(uv.x,1.-uv.y)).rgb;
 // The source contains 1-2 byte compression noise in otherwise black areas.
 if(max(max(c.r,c.g),c.b)<3./255.)c=vec3(0);
 fragColor=vec4(c,1);
}
