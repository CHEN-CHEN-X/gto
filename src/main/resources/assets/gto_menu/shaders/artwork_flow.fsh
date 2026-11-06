#version 150 core
uniform sampler2D uArtwork;
uniform vec2 uResolution;
uniform float uSourceAspect;
uniform float uOrbitAngle;
uniform float uFlowStrength;
in vec2 vUv;
out vec4 fragColor;
const float PI=3.141592653589793;
mat2 rotation(float a) { return mat2(cos(a),sin(a),-sin(a),cos(a)); }
vec3 artwork(vec2 p) {
    vec2 uv=p/vec2(uSourceAspect,1.0)+0.5;
    if(any(lessThan(uv,vec2(0.0)))||any(greaterThan(uv,vec2(1.0))))return vec3(0.0);
    return texture(uArtwork,vec2(uv.x,1.0-uv.y)).rgb;
}
// A seamless material strip sampled from the unobscured part of the source.
// The overlap is spatial, attached to the ring; there is no temporal crossfade
// or short cycle returning features to their start positions.
vec3 diskMaterial(float radius,float angle) {
    float u=fract(angle*3.0/(2.0*PI));
    float arc=2.0; // clean near-side arc, avoiding the lensed upper image
    float overlap=0.18;
    float local=u*(arc-overlap);
    float a=-2.57+local;
    vec2 q=vec2(cos(a),sin(a))*radius*vec2(1.0,0.30);
    vec3 col=artwork(rotation(0.26)*q+vec2(0.0,-0.015));
    if(local<overlap){
        float b=a+arc-overlap;
        vec2 qb=vec2(cos(b),sin(b))*radius*vec2(1.0,0.30);
        col=mix(artwork(rotation(0.26)*qb+vec2(0.0,-0.015)),col,smoothstep(0.0,overlap,local));
    }
    return col*(0.90+0.10*sin(angle+0.7));
}
vec3 ringMaterial(float radius,float angle) {
    float u=fract(angle*3.0/(2.0*PI));
    // Endpoints selected between source glyphs by matching radial profiles.
    float overlap=0.04;
    float period=1.8369173;
    float local=u*period;
    float a=0.7199248+local;
    vec2 center=vec2(0.0,0.015);
    vec3 col=artwork(center+radius*vec2(cos(a),sin(a)));
    if(local<overlap){
        float b=a+period;
        col=mix(artwork(center+radius*vec2(cos(b),sin(b))),col,smoothstep(0.0,overlap,local));
    }
    return col*(0.90+0.10*sin(angle+0.7));
}
vec3 crossingMaterial(vec2 disk,float angle) {
    // The lensed foreground stripe is fixed in shape, but its source digits
    // travel across it continuously. The seam is spatial, never a timer reset.
    float local=fract((disk.x+0.28)/0.56-angle/PI)*0.56;
    vec2 q=vec2(local-0.28,disk.y);
    vec3 col=artwork(rotation(0.26)*q+vec2(0.0,-0.015));
    if(local<0.035){
        q.x+=0.56;
        col=mix(artwork(rotation(0.26)*q+vec2(0.0,-0.015)),col,smoothstep(0.0,0.035,local));
    }
    return col;
}
void main() {
    float aspect=uResolution.x/uResolution.y;
    vec2 p=(vUv-0.5)*vec2(aspect,1.0)/min(1.0,aspect/uSourceAspect);
    vec3 original=artwork(p);
    if(uFlowStrength<=0.0){fragColor=vec4(original,1.0);return;}
    vec2 center=vec2(0.0,0.015);
    vec2 d=p-center;
    float r=length(d);
    vec2 disk=rotation(-0.26)*(p-vec2(0.0,-0.015));
    vec2 orbit=disk/vec2(1.0,0.30);
    float diskRadius=length(orbit);
    float foreground=1.0-smoothstep(-0.11,-0.035,disk.y);
    float corona=smoothstep(0.17,0.215,r)*(1.0-smoothstep(0.27,0.32,r));
    corona*=1.0-foreground;
    vec3 diskColor=diskMaterial(diskRadius,atan(orbit.y,orbit.x)-uOrbitAngle);
    vec3 ringColor=ringMaterial(r,atan(d.y,d.x)-uOrbitAngle*2.0);
    // Keep the lens image and its occlusion fixed. The outer disk and data
    // corona are independent rotating materials, not a warped scene image.
    float diskMask=smoothstep(0.55,0.72,diskRadius)*(1.0-smoothstep(0.91,1.02,diskRadius));
    diskMask*=smoothstep(0.27,0.34,r);
    // Previously this entire near/inner region fell through to `original`,
    // leaving a visible bank of frozen digits. Give it its own full orbit.
    // Sample a clear foreground material radius instead of sampling the
    // black occluded center of the source image at small disk radii.
    // Use the same source-material rotation as the approved outer rings.
    // Scale both material coordinates together; do not clamp many rows to
    // one source radius (which previously produced radial stretching).
    vec3 innerColor = diskMaterial(diskRadius*1.4,
                                  atan(orbit.y,orbit.x)-uOrbitAngle*2.0);
    float innerMask = smoothstep(0.12,0.25,diskRadius)
                    * (1.0-smoothstep(0.62,0.75,diskRadius));
    // Animate foreground rings across the lower shadow while keeping the
    // event horizon itself and the already-approved corona mask intact.
    innerMask *= max(foreground,smoothstep(0.19,0.24,r));
    vec3 innerBase = mix(original,innerColor,innerMask);
    vec3 flowing=mix(innerBase,diskColor,diskMask);
    flowing=mix(flowing,ringColor,corona);
    float hole=(1.0-smoothstep(0.16,0.205,r))*(1.0-foreground);
    vec3 color=mix(flowing,original,hole);
    float crossing=(1.0-smoothstep(0.009,0.022,abs(disk.y+0.0425)))
                  *(1.0-smoothstep(0.18,0.25,abs(disk.x)));
    color=mix(color,crossingMaterial(disk,uOrbitAngle),crossing);
    fragColor=vec4(color,1.0);
}
