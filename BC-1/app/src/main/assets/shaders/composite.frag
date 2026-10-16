#version 300 es
precision highp float;
in vec2 vUV;
out vec4 fragColor;
uniform sampler2D uInputA;
uniform sampler2D uInputB;
uniform sampler2D uBlurredA;
uniform float uProgress;
uniform float uIntensity;
uniform float uOpacity;
uniform int uTransition;
uniform int uEffect;
uniform int uEasing;
uniform vec2 uInputTexel;

float rnd(vec2 p) { return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
float transitionMask(vec2 uv,float p) {
    if(p<=0.0) return 0.0;
    if(p>=1.0) return 1.0;
    if(uTransition==1) return 1.0-smoothstep(p-.012,p+.012,uv.x);
    if(uTransition==2) {
        float threshold=rnd(floor(uv*vec2(12.0,7.0)));
        return smoothstep(threshold-.035,threshold+.035,p);
    }
    if(uTransition==3) {
        // Correct radial distance for the 16:9 composition.
        float d=length((uv-.5)*vec2(16.0/9.0,1.0));
        return 1.0-smoothstep(p*1.03-.012,p*1.03+.012,d);
    }
    return p;
}
vec4 effectA(vec2 uv, vec4 b) {
    float k=clamp(uIntensity,0.0,1.0);
    vec2 coord=uv;
    if(uEffect==8 && k>0.0) {
        vec2 cells=vec2(1024.0,576.0)/mix(1.0,48.0,k);
        coord=(floor(uv*cells)+.5)/cells;
    }
    vec4 a=texture(uInputA,coord);
    if(uEffect==1) a=mix(a,texture(uBlurredA,uv),k);
    if(uEffect==2) a.rgb+=max(texture(uBlurredA,uv).rgb-.16,vec3(0.0))*k*1.7;
    if(uEffect==3) {
        float dist=distance(a.rgb,vec3(.10,.84,.29));
        float tolerance=mix(.08,.42,k);
        a.a*=smoothstep(tolerance-.045,tolerance+.045,dist);
    }
    if(uEffect==4) a.rgb=mix(a.rgb,vec3(dot(a.rgb,vec3(.2126,.7152,.0722))),k);
    if(uEffect==5) a.a*=k;
    if(uEffect==6) a.rgb*=1.0-k*smoothstep(.25,.95,length((uv-.5)*vec2(1.6,1.0)));
    if(uEffect==7) {
        vec3 nearColor=texture(uInputA,uv+vec2(uInputTexel.x,0.0)).rgb
            +texture(uInputA,uv-vec2(uInputTexel.x,0.0)).rgb
            +texture(uInputA,uv+vec2(0.0,uInputTexel.y)).rgb
            +texture(uInputA,uv-vec2(0.0,uInputTexel.y)).rgb;
        a.rgb+=(a.rgb*4.0-nearColor)*k;
    }
    if(uEffect==9) a.rgb*=exp2((k-.5)*4.0);
    if(uEffect==10) a.rgb=mix(vec3(dot(a.rgb,vec3(.2126,.7152,.0722))),a.rgb,k*2.0);
    if(uEffect==11) a.rgb=mix(a.rgb,(a.rgb-.5)*1.08+.5+vec3(-.06,.015,.09),k);
    a.rgb=clamp(a.rgb,0.0,1.0);
    return vec4(mix(b.rgb,a.rgb,a.a*clamp(uOpacity,0.0,1.0)),1.0);
}
void main() {
    float p=clamp(uProgress,0.0,1.0);
    if(uEasing==1) p=p*p*(3.0-2.0*p);
    vec2 auv=vUV, buv=vUV;
    if(uTransition==4) {
        auv.x+=p;
        buv.x+=p-1.0;
    }
    if(uTransition==5) {
        auv=(auv-.5)/(1.0+.35*p)+.5;
        buv=(buv-.5)/(1.35-.35*p)+.5;
    }
    vec4 b=texture(uInputB,buv);
    vec4 a=effectA(auv,b);
    float mask=transitionMask(vUV,p);
    if(uTransition==4) mask=p<=0.0?0.0:p>=1.0?1.0:step(1.0-p,vUV.x);
    vec3 color=mix(a.rgb,b.rgb,mask);
    if(uTransition==6) color=p<.5?a.rgb*(1.0-2.0*p):b.rgb*(2.0*p-1.0);
    fragColor=vec4(color,1.0);
}
