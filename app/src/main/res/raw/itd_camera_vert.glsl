// 1:1 с Telegram'овским camera_vert.glsl (dual<0.5 path — без crossfade/dual).
// uMVPMatrix — rotation вокруг Z для компенсации sensorOrientation.
// uSTMatrix — transform-matrix от SurfaceTexture (включает hardware-flip etc).
attribute vec4 aPosition;
attribute vec4 aTextureCoord;

uniform mat4 uMVPMatrix;
uniform mat4 uSTMatrix;

varying vec2 uv;

void main() {
    gl_Position = uMVPMatrix * aPosition;
    uv = (uSTMatrix * aTextureCoord).xy;
}
