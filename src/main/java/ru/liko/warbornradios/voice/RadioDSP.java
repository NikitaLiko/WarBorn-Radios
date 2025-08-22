package ru.liko.warbornradios.voice;

import java.util.Random;

public class RadioDSP {
    private static final Random RNG = new Random();
    private static final float SR = 48000f;

    /**
     * Very rough "radio" processing to add band-limiting and noise.
     * @param in 16-bit PCM at 48kHz mono
     * @param tx transmitter state
     * @param rx receiver state
     * @param snrDb estimated SNR in dB (>25 clean, 10–25 noisy, <10 collapse)
     * @return processed 16-bit PCM
     */
    public static short[] process(short[] in,
                                  WRBRadioVoicePlugin.RadioState tx,
                                  WRBRadioVoicePlugin.RadioState rx,
                                  double snrDb) {
        if (in == null) return new short[0];
        float[] f = shortsToFloats(in);

        // Basic AC coupling and band limiting for handheld radios
        filterHighPass(f, 220f);
        switch (rx.mode) {
            case FM -> filterLowPass(f, 2800f);
            case AM -> filterLowPass(f, 3500f);
            default -> filterLowPass(f, 3000f);
        }

        // Add noise based on SNR (simple white noise model)
        float noiseAmp = (float) Math.pow(10.0, -snrDb / 20.0) * 0.08f;
        if (noiseAmp > 0f) {
            for (int i = 0; i < f.length; i++) {
                f[i] += noiseAmp * (float) RNG.nextGaussian();
            }
        }

        // Gentle saturation for AM
        if (rx.mode == WRBRadioVoicePlugin.Mode.AM) {
            addAmSaturation(f, 0.3f);
        }

        // Soft squelch tail
        addSquelchTail(f);

        return floatsToShorts(f);
    }

    private static void addSquelchTail(float[] f) {
        int n = Math.min(200, f.length);
        for (int i = 0; i < n; i++) {
            f[i] += 0.15f * (float) RNG.nextGaussian();
        }
    }

    private static void addAmSaturation(float[] f, float drive) {
        for (int i = 0; i < f.length; i++) {
            f[i] = (float) Math.tanh(f[i] * (1f + drive));
        }
    }

    private static void filterHighPass(float[] f, float fc) {
        float rc = 1f / (2f * (float)Math.PI * fc);
        float dt = 1f / SR;
        float alpha = rc / (rc + dt);
        float y = 0f;
        float xPrev = 0f;
        for (int i = 0; i < f.length; i++) {
            float x = f[i];
            y = alpha * (y + x - xPrev);
            xPrev = x;
            f[i] = y;
        }
    }

    private static void filterLowPass(float[] f, float fc) {
        float rc = 1f / (2f * (float)Math.PI * fc);
        float dt = 1f / SR;
        float alpha = dt / (rc + dt);
        float y = 0f;
        for (int i = 0; i < f.length; i++) {
            y += alpha * (f[i] - y);
            f[i] = y;
        }
    }

    private static float[] shortsToFloats(short[] in) {
        float[] out = new float[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i] / 32768f;
        }
        return out;
    }

    private static short[] floatsToShorts(float[] in) {
        short[] out = new short[in.length];
        for (int i = 0; i < in.length; i++) {
            float v = Math.max(-1f, Math.min(1f, in[i]));
            out[i] = (short) Math.round(v * 32767f);
        }
        return out;
    }
}
