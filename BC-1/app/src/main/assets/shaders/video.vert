#version 300 es
in vec4 aPosition;
out vec2 vUV;
void main() { gl_Position=aPosition; vUV=aPosition.xy*.5+.5; }
