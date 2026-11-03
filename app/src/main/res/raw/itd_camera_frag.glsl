// 1:1 с Telegram'овской dual<0.5 веткой — pure passthrough OES sampler.
// Rounded corners НЕ в shader, а через ViewOutlineProvider на ItdCameraView.
#extension GL_OES_EGL_image_external : require
precision mediump float;

varying vec2 uv;
uniform samplerExternalOES sTexture;

void main() {
    gl_FragColor = texture2D(sTexture, uv);
}
