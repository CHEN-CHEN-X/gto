#version 150 core
uniform vec2 uResolution;
uniform float uSourceAspect,uOrbitAngle;
uniform sampler2D uOrbitLut;
in vec4 aOrbit;
in vec4 aMotion;
in vec4 aAtlas;
in vec4 aSize;
out vec2 glyphUv;
out vec2 sceneUv;
flat out vec2 centerUv;
const float PI=3.141592653589793;
mat2 rotation(float a){return mat2(cos(a),sin(a),-sin(a),cos(a));}
vec2 pose(float phase,int row){
 float x=fract(phase)*2048.-.5;
 int a=int(floor(x));float f=fract(x);
 vec4 q0=texelFetch(uOrbitLut,ivec2((a+2048)%2048,row),0);
 vec4 q1=texelFetch(uOrbitLut,ivec2((a+2049)%2048,row),0);
 vec2 d0=vec2(dot(q0.rg,vec2(65280.,255.))/65535.*2.*PI-PI,dot(q0.ba,vec2(65280.,255.))/65535.*64.);
 vec2 d1=vec2(dot(q1.rg,vec2(65280.,255.))/65535.*2.*PI-PI,dot(q1.ba,vec2(65280.,255.))/65535.*64.);
 d1.x=d0.x+mod(d1.x-d0.x+PI,2.*PI)-PI;
 return mix(d0,d1,f);
}
void main(){
 int vertex=gl_VertexID%6;
 vec2 corner=vec2(float((vertex==1)||(vertex==2)||(vertex==4)),float(vertex>=2&&vertex<=4));
 // 0,1,2 / 0,2,3 in a six-vertex triangle list.
 if(vertex==3)corner=vec2(0,0);
 if(vertex==4)corner=vec2(1,1);
 if(vertex==5)corner=vec2(0,1);
 vec2 state=pose(aMotion.x+uOrbitAngle/(2.*PI)*aMotion.z,int(aMotion.w+.5));
 float t=state.x;
 vec2 orbit=vec2(cos(t),sin(t))*aOrbit.zw;
 vec2 tangent=vec2(-sin(t),cos(t))*aOrbit.zw;
 vec2 local=(corner-.5)*aSize.xy;
 float angle=atan(tangent.y,tangent.x)+aMotion.y-aSize.z;
 local=rotation(angle)*local;
 local*=clamp(state.y/max(aSize.w,1.),.55,1.8);
 vec2 center=aOrbit.xy+rotation(aMotion.y)*orbit;
 centerUv=center/vec2(uSourceAspect,1.)+.5;
 centerUv.y=1.-centerUv.y;
 vec2 p=center+local;
 float aspect=uResolution.x/uResolution.y;
 float fit=min(1.,aspect/uSourceAspect);
 gl_Position=vec4(p/vec2(aspect,1.)*fit*2.,0,1);
 glyphUv=aAtlas.xy+vec2(corner.x,1.-corner.y)*aAtlas.zw;
 sceneUv=p/vec2(uSourceAspect,1.)+.5;
 sceneUv.y=1.-sceneUv.y;
}
