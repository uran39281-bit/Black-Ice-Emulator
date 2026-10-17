#version 300 es
precision highp float;
in vec2 vUV;
out vec4 fragColor;
uniform sampler2D uInput;
uniform vec2 uTexel;
uniform float uAspect,uIntensity,uOpacity,uProgress;
uniform vec4 uTransform,uChroma;
// x: signed width scale sign, y: signed height factor (aspect, sign = flip), z/w: tan(skew x/y).
uniform vec4 uShape;
uniform vec2 uAnchor;
uniform vec3 uKeyColor;
uniform int uEffect,uTransition,uRole,uTop;
float rnd(vec2 p) { return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
float mask(vec2 uv,float p) {
    if(p<=0.0) return 0.0;
    if(p>=1.0) return 1.0;
    if(uTransition==1) return 1.0-smoothstep(p-.012,p+.012,uv.x);
    if(uTransition==2) { float t=rnd(floor(uv*vec2(12.0,7.0))); return smoothstep(t-.035,t+.035,p); }
    if(uTransition==3) {
        float radius=length(vec2(uAspect,1.0))*.5;
        return 1.0-smoothstep(p*radius-.012,p*radius+.012,length((uv-.5)*vec2(uAspect,1.0)));
    }
    if(uTransition==4) return step(1.0-p,uv.x);
    return p;
}
void main() {
    float k=uIntensity,p=uProgress;
    // Inverse of s = T + a + R·S·K·(l − a); see TransformMath for the forward mapping.
    vec2 anchor=uAnchor*.5*vec2(uAspect,1.0);
    vec2 q=(vUV-.5-uTransform.xy*.5)*vec2(uAspect,1.0)-anchor;
    float cs=cos(uTransform.w),sn=sin(uTransform.w);
    q=mat2(cs,-sn,sn,cs)*q/(max(.05,uTransform.z)*uShape.xy);
    q=vec2(q.x-uShape.z*q.y,q.y-uShape.w*q.x)/(1.0-uShape.z*uShape.w)+anchor;
    vec2 uv=q/vec2(uAspect,1.0)+.5;
    float inside=step(0.0,uv.x)*step(uv.x,1.0)*step(0.0,uv.y)*step(uv.y,1.0);
    if(uRole>0 && uTransition==4) uv.x+=uRole==2?p:p-1.0;
    if(uRole>0 && uTransition==5) uv=(uv-.5)/(uRole==2?1.0+.35*p:1.35-.35*p)+.5;
    vec2 coord=uv;
    if(uEffect==8 && k>0.0) { vec2 cells=(1.0/uTexel)/mix(1.0,48.0,k); coord=(floor(uv*cells)+.5)/cells; }
    vec4 a=texture(uInput,coord);
    // A fixed 13-tap Gaussian approximation bounds bandwidth on moving frames.
    if(uEffect==1 || uEffect==2) {
        vec2 d=uTexel*mix(1.0,14.0,k);
        vec3 b=a.rgb*.20;
        b+=(texture(uInput,uv+vec2(d.x,0)).rgb+texture(uInput,uv-vec2(d.x,0)).rgb
           +texture(uInput,uv+vec2(0,d.y)).rgb+texture(uInput,uv-vec2(0,d.y)).rgb)*.12;
        b+=(texture(uInput,uv+d).rgb+texture(uInput,uv-d).rgb
           +texture(uInput,uv+vec2(d.x,-d.y)).rgb+texture(uInput,uv+vec2(-d.x,d.y)).rgb)*.06;
        b+=(texture(uInput,uv+vec2(2.0*d.x,0)).rgb+texture(uInput,uv-vec2(2.0*d.x,0)).rgb
           +texture(uInput,uv+vec2(0,2.0*d.y)).rgb+texture(uInput,uv-vec2(0,2.0*d.y)).rgb)*.02;
        a.rgb=uEffect==1?mix(a.rgb,b,k):a.rgb+max(b-.16,vec3(0))*k*1.7;
    }
    if(uEffect==3) {float tol=mix(.08,.42,k); a.a*=smoothstep(tol-.045,tol+.045,distance(a.rgb,vec3(.10,.84,.29)));}
    if(uEffect==4) a.rgb=mix(a.rgb,vec3(dot(a.rgb,vec3(.2126,.7152,.0722))),k);
    if(uEffect==5) a.a*=k;
    if(uEffect==6) a.rgb*=1.0-k*smoothstep(.25,.95,length((uv-.5)*vec2(uAspect,1)));
    if(uEffect==7) {
        vec3 n=texture(uInput,uv+vec2(uTexel.x,0)).rgb+texture(uInput,uv-vec2(uTexel.x,0)).rgb
              +texture(uInput,uv+vec2(0,uTexel.y)).rgb+texture(uInput,uv-vec2(0,uTexel.y)).rgb;
        a.rgb+=(a.rgb*4.0-n)*k;
    }
    if(uEffect==9) a.rgb*=exp2((k-.5)*4.0);
    if(uEffect==10) a.rgb=mix(vec3(dot(a.rgb,vec3(.2126,.7152,.0722))),a.rgb,k*2.0);
    if(uEffect==11) a.rgb=mix(a.rgb,(a.rgb-.5)*1.08+.5+vec3(-.06,.015,.09),k);
    if(uChroma.x>.5){
        float chromaDistance=length((a.rgb-dot(a.rgb,vec3(.2126,.7152,.0722)))-(uKeyColor-dot(uKeyColor,vec3(.2126,.7152,.0722))));
        float matte=smoothstep(uChroma.y-uChroma.z,uChroma.y+uChroma.z,chromaDistance);
        a.a*=matte;
        vec3 spill=max(uKeyColor-vec3(dot(uKeyColor,vec3(.333333))),vec3(0));
        a.rgb-=spill*uChroma.w*(1.0-smoothstep(uChroma.y,uChroma.y+uChroma.z*4.0,chromaDistance));
    }
    a.a*=inside;
    if(uRole>0) {
        if(uTransition==6) {
            float fade=uRole==2?max(0.0,1.0-2.0*p):max(0.0,2.0*p-1.0);
            a.rgb*=fade;
            // Hard switch at the black midpoint; no double fade in alpha compositing.
            if(uTop==1) a.a*=uRole==2?(p<.5?1.0:0.0):(p<.5?0.0:1.0);
        } else if(uTop==1) {float m=mask(vUV,p); a.a*=uRole==2?1.0-m:m;}
    }
    fragColor=vec4(clamp(a.rgb,0.0,1.0),a.a*uOpacity);
}
