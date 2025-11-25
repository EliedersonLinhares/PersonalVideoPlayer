package com.esl.videoplayer.audio;

import com.esl.videoplayer.Video.VideoPanel;

import javax.swing.*;
import java.nio.ShortBuffer;

public class AudioLoudnessManager {

    private float globalAudioGain = 0.2f; // 50% do volume (ajustável de 0.1 a 1.0)
    // Variaveis para uso no sistema de normalização de volume em audios
    private boolean audioNormalizationEnabled = false; // DESATIVADO por padrão
    private float targetLoudness = -18.0f; // MAIS BAIXO: -18 LUFS (era -14)
    private float normalizationGain = 1.0f; // Ganho calculado para normalização
    private float maxPeakLevel = 0.0f; // Pico máximo detectado
    private float averageRMS = 0.0f; // RMS médio
    private int normalizationSampleCount = 0;
    private final int NORMALIZATION_ANALYSIS_SAMPLES = 100; // Amostras antes de aplicar
    private boolean normalizationCalculated = false;

    // Buffer circular para análise contínua
    private float[] rmsHistory = new float[50];
    private int rmsHistoryIndex = 0;

    // Variáveis para spectrum analyzer
    private float[] spectrumData = new float[64]; // 64 barras de frequência
    private final Object spectrumLock = new Object();

    public float calculateNormalizationGain(float currentDbFS, float targetDbFS) {
        // Calcular diferença em dB
        float dbDifference = targetDbFS - currentDbFS;

        // Converter dB para ganho linear
        float gain = (float) Math.pow(10.0, dbDifference / 20.0);

        // LIMITES MAIS CONSERVADORES para evitar volume alto
        // Máximo +6dB de ganho, mínimo -30dB de atenuação
        gain = Math.max(0.03f, Math.min(2.0f, gain));

        // APLICAR GANHO GLOBAL ADICIONAL
        gain *= globalAudioGain;

        return gain;
    }
    // ==================== MÉTODO PARA APLICAR NORMALIZAÇÃO ====================

    public short[] applyNormalization(short[] samples, float gain) {
        short[] normalized = new short[samples.length];

        for (int i = 0; i < samples.length; i++) {
            float sample = samples[i] * gain;

            // Soft clipping para evitar distorção severa
            if (Math.abs(sample) > 32767) {
                sample = softClip(sample);
            }

            normalized[i] = (short) Math.max(-32768, Math.min(32767, sample));
        }

        return normalized;
    }

    // Soft clipping suave para evitar distorção
    public float softClip(float sample) {
        float threshold = 32767 * 0.9f; // 90% do máximo

        if (Math.abs(sample) <= threshold) {
            return sample;
        }

        // Aplicar função de clipping suave (tanh)
        float sign = Math.signum(sample);
        float abs = Math.abs(sample);
        float normalized = abs / 32768.0f;
        float clipped = (float) Math.tanh(normalized * 1.5) * 32767;

        return sign * clipped;
    }

// ==================== MÉTODOS PÚBLICOS PARA CONTROLE ====================

    public void setAudioNormalizationEnabled(boolean enabled) {
        this.audioNormalizationEnabled = enabled;
        System.out.println("Normalização de áudio: " + (enabled ? "ATIVADA" : "DESATIVADA"));

        if (!enabled) {
            normalizationGain = 1.0f;
        }
    }

    public void setTargetLoudness(float targetDbFS, AudioLoudnessAnalyzer loudnessAnalyzer) {
        this.targetLoudness = Math.max(-30.0f, Math.min(0.0f, targetDbFS));
        System.out.println("Target loudness definido: " + this.targetLoudness + " dBFS");

        // Recalcular ganho se já analisado
        if (normalizationCalculated) {
            float currentDbFS = loudnessAnalyzer.getDbFS();
            normalizationGain = calculateNormalizationGain(currentDbFS, this.targetLoudness);
        }
    }

    public boolean isAudioNormalizationEnabled() {
        return audioNormalizationEnabled;
    }

    public float getCurrentNormalizationGain() {
        return normalizationGain;
    }

    public String getNormalizationInfo() {
        if (!audioNormalizationEnabled) {
            return "Normalização: Desativada | Volume: " + (int) (globalAudioGain * 100) + "%";
        }

        if (!normalizationCalculated) {
            return "Normalização: Analisando... (" + normalizationSampleCount + "/" + NORMALIZATION_ANALYSIS_SAMPLES + ")";
        }

        float gainDb = 20.0f * (float) Math.log10(normalizationGain);
        return String.format("Normalização: %.1f dB | Volume: %d%% | Pico: %.1f dBFS",
                gainDb,
                (int) (globalAudioGain * 100),
                20 * Math.log10(maxPeakLevel));
    }

    // Aplicar preset
    public void applyLoudnessPreset(LoudnessPreset preset, AudioLoudnessAnalyzer loudnessAnalyzer) {
        setTargetLoudness(preset.getDbFS(),loudnessAnalyzer);
        System.out.println("Preset aplicado: " + preset.getDescription());
    }

    // NOVO: Controle de ganho global (volume master do áudio)
    public void setGlobalAudioGain(float gain) {
        this.globalAudioGain = Math.max(0.01f, Math.min(1.0f, gain));
        System.out.println("Ganho global de áudio: " + (int) (this.globalAudioGain * 100) + "%");
    }


    // Método auxiliar para processar samples de áudio (para áudio apenas)
    public byte[] processAudioSamples(ShortBuffer channelSamples, AudioLoudnessAnalyzer loudnessAnalyzer, VideoPanel videoPanel,
                                      boolean isAudioOnly, int audioChannels, float volume) {
        try {
            // Copiar samples para array
            short[] audioSamplesArray = new short[channelSamples.remaining()];
            channelSamples.mark();
            channelSamples.get(audioSamplesArray);
            channelSamples.reset();

            // ===== ANÁLISE E NORMALIZAÇÃO =====
            if (isAudioNormalizationEnabled() && isAudioOnly) {
                // Fase 1: Análise inicial (primeiros N samples)
                if (!isNormalizationCalculated() && getNormalizationSampleCount() < getNORMALIZATION_ANALYSIS_SAMPLES()) {
                    loudnessAnalyzer.analyzeSamples(audioSamplesArray);
                   setNormalizationSampleCount(getNormalizationSampleCount() + 1);

                    // IMPORTANTE: APLICAR globalAudioGain MESMO DURANTE ANÁLISE
                    audioSamplesArray = applySimpleGain(audioSamplesArray,  getGlobalAudioGain());

                    // Após análise inicial, calcular ganho
                    if (getNormalizationSampleCount() >= getNORMALIZATION_ANALYSIS_SAMPLES()) {
                        float currentDbFS = loudnessAnalyzer.getDbFS();
                        setMaxPeakLevel( loudnessAnalyzer.getPeakLevel());

                        System.out.println("=== Análise de Áudio Concluída ===");
                        System.out.println("RMS médio: " + currentDbFS + " dBFS");
                        System.out.println("Pico máximo: " + (20 * Math.log10(getMaxPeakLevel()) + " dBFS"));

                        // Calcular ganho de normalização
                        // normalizationGain = calculateNormalizationGain(currentDbFS, targetLoudness);
                        setNormalizationGain(
                                calculateNormalizationGain(currentDbFS, getTargetLoudness()));

                        System.out.println("Ganho de normalização: " + (20 * Math.log10(getNormalizationGain())) + " dB");
                        System.out.println("Fator multiplicador: " + getNormalizationGain());
                        System.out.println("==================================");

                        setNormalizationCalculated(true);
                        loudnessAnalyzer.reset();
                    }
                }

                // Fase 2: Aplicar normalização (com ajuste dinâmico contínuo)
                else if (isNormalizationCalculated()) {
                    // Análise contínua para ajuste dinâmico
                    loudnessAnalyzer.analyzeSamples(audioSamplesArray);

                    // Atualizar histórico RMS
                    rmsHistory[rmsHistoryIndex] = loudnessAnalyzer.getRMS();
                    rmsHistoryIndex = (rmsHistoryIndex + 1) % rmsHistory.length;

                    // Calcular RMS médio do histórico
                    float sumRMS = 0.0f;
                    for (float rms : rmsHistory) {
                        sumRMS += rms;
                    }
                    setAverageRMS(sumRMS / rmsHistory.length);

                    // Ajuste dinâmico suave do ganho (evita mudanças bruscas)
                    float currentDbFS = loudnessAnalyzer.getDbFS();
                    float targetGain = calculateNormalizationGain(currentDbFS, getTargetLoudness());

                    // Interpolação suave (70% antigo, 30% novo)
                   setNormalizationGain(getNormalizationGain() * 0.7f + targetGain * 0.3f);

                    loudnessAnalyzer.reset();

                    // Aplicar normalização aos samples
                    audioSamplesArray = applyNormalization(audioSamplesArray,getNormalizationGain());
                }
            } else {
                // SE NORMALIZAÇÃO DESATIVADA, APLICAR globalAudioGain DIRETAMENTE
                audioSamplesArray = applySimpleGain(audioSamplesArray,getGlobalAudioGain());
            }

            // ===== CALCULAR SPECTRUM (usando samples normalizados) =====
            if (isAudioOnly) {
                float[] spectrum = calculateFFT(audioSamplesArray, audioSamplesArray.length);
                synchronized (spectrumLock) {
                    spectrumData = spectrum;
                }

                SwingUtilities.invokeLater(() -> {
                    videoPanel.updateSpectrum(spectrumData);
                });
            }

            // ===== PROCESSAR PARA REPRODUÇÃO =====
            if (audioChannels > 2) {
                // Downmix multicanal
                channelSamples.rewind();
                int totalSamples = channelSamples.remaining();
                int framesCount = totalSamples / audioChannels;
                byte[] audioBytes = new byte[framesCount * 2 * 2];

                for (int i = 0; i < framesCount; i++) {
                    int baseIndex = i * audioChannels;
                    float left = 0, right = 0;

                    if (audioChannels == 6) {
                        short fl = channelSamples.get(baseIndex + 0);
                        short fr = channelSamples.get(baseIndex + 1);
                        short center = channelSamples.get(baseIndex + 2);
                        short lfe = channelSamples.get(baseIndex + 3);
                        short rl = channelSamples.get(baseIndex + 4);
                        short rr = channelSamples.get(baseIndex + 5);

                        left = fl + (center * 0.707f) + (rl * 0.707f) + (lfe * 0.5f);
                        right = fr + (center * 0.707f) + (rr * 0.707f) + (lfe * 0.5f);
                    } else {
                        int leftSum = 0, rightSum = 0;
                        int leftCount = 0, rightCount = 0;

                        for (int ch = 0; ch < audioChannels; ch++) {
                            short sample = channelSamples.get(baseIndex + ch);
                            if (ch % 2 == 0) {
                                leftSum += sample;
                                leftCount++;
                            } else {
                                rightSum += sample;
                                rightCount++;
                            }
                        }

                        left = (leftCount > 0 ? leftSum / leftCount : 0);
                        right = (rightCount > 0 ? rightSum / rightCount : 0);
                    }

                    // APENAS volume do slider (globalAudioGain já aplicado acima)
                    left *= volume;
                    right *= volume;
                    left = Math.max(-32768, Math.min(32767, left));
                    right = Math.max(-32768, Math.min(32767, right));

                    short leftShort = (short) left;
                    short rightShort = (short) right;

                    audioBytes[i * 4] = (byte) ((leftShort >> 8) & 0xFF);
                    audioBytes[i * 4 + 1] = (byte) (leftShort & 0xFF);
                    audioBytes[i * 4 + 2] = (byte) ((rightShort >> 8) & 0xFF);
                    audioBytes[i * 4 + 3] = (byte) (rightShort & 0xFF);
                }

                return audioBytes;

            } else {
                // Mono/Stereo (usar samples já normalizados com globalAudioGain)
                byte[] audioBytes = new byte[audioSamplesArray.length * 2];
                for (int i = 0; i < audioSamplesArray.length; i++) {
                    // APENAS volume do slider (globalAudioGain já aplicado acima)
                    short s = (short) (audioSamplesArray[i] * volume);
                    audioBytes[i * 2] = (byte) ((s >> 8) & 0xFF);
                    audioBytes[i * 2 + 1] = (byte) (s & 0xFF);
                }
                return audioBytes;
            }
        } catch (Exception e) {
            System.err.println("Erro ao processar samples: " + e.getMessage());
            return null;
        }
    }

    // NOVO MÉTODO: Aplicar ganho simples sem normalização
    private short[] applySimpleGain(short[] samples, float gain) {
        short[] adjusted = new short[samples.length];

        for (int i = 0; i < samples.length; i++) {
            float sample = samples[i] * gain;

            // Soft clipping para evitar distorção
            if (Math.abs(sample) > 32767) {
                sample = softClip(sample);
            }

            adjusted[i] = (short) Math.max(-32768, Math.min(32767, sample));
        }

        return adjusted;
    }

// ==================== MÉTODO: Calcular FFT ====================

    private float[] calculateFFT(short[] audioSamples, int sampleCount) {
        // Usar apenas potência de 2 para FFT
        int fftSize = 512;
        if (sampleCount < fftSize) {
            fftSize = 256;
        }

        // Preparar dados para FFT
        float[] real = new float[fftSize];
        float[] imag = new float[fftSize];

        // Copiar samples e aplicar janela de Hamming
        for (int i = 0; i < Math.min(sampleCount, fftSize); i++) {
            float hamming = (float) (0.54 - 0.46 * Math.cos(2 * Math.PI * i / (fftSize - 1)));
            real[i] = audioSamples[i] * hamming;
            imag[i] = 0;
        }

        // FFT simples (Cooley-Tukey)
        fft(real, imag);

        // Calcular magnitudes e agrupar em 64 barras
        float[] spectrum = new float[64];
        int samplesPerBar = (fftSize / 2) / 64;

        for (int i = 0; i < 64; i++) {
            float sum = 0;
            for (int j = 0; j < samplesPerBar; j++) {
                int index = i * samplesPerBar + j;
                if (index < fftSize / 2) {
                    float magnitude = (float) Math.sqrt(real[index] * real[index] + imag[index] * imag[index]);
                    sum += magnitude;
                }
            }

            // Normalizar e aplicar escala logarítmica
            float avg = sum / samplesPerBar;
            spectrum[i] = (float) (Math.log10(1 + avg) / 5.0); // Escala log
            spectrum[i] = Math.min(1.0f, spectrum[i]); // Limitar a 1.0
        }

        return spectrum;
    }

    // FFT Cooley-Tukey (in-place)
    private void fft(float[] real, float[] imag) {
        int n = real.length;

        // Bit reversal
        int j = 0;
        for (int i = 0; i < n - 1; i++) {
            if (i < j) {
                float tempR = real[i];
                float tempI = imag[i];
                real[i] = real[j];
                imag[i] = imag[j];
                real[j] = tempR;
                imag[j] = tempI;
            }
            int k = n / 2;
            while (k <= j) {
                j -= k;
                k /= 2;
            }
            j += k;
        }

        // Butterfly computations
        for (int len = 2; len <= n; len *= 2) {
            float angle = (float) (-2 * Math.PI / len);
            float wlenR = (float) Math.cos(angle);
            float wlenI = (float) Math.sin(angle);

            for (int i = 0; i < n; i += len) {
                float wR = 1.0f;
                float wI = 0.0f;

                for (int h = 0; h < len / 2; h++) {
                    float uR = real[i + h];
                    float uI = imag[i + h];
                    float tR = wR * real[i + h + len / 2] - wI * imag[i + h + len / 2];
                    float tI = wR * imag[i + h + len / 2] + wI * real[i + h + len / 2];

                    real[i + h] = uR + tR;
                    imag[i + h] = uI + tI;
                    real[i + h + len / 2] = uR - tR;
                    imag[i + h + len / 2] = uI - tI;

                    float tempR = wR;
                    wR = wR * wlenR - wI * wlenI;
                    wI = tempR * wlenI + wI * wlenR;
                }
            }
        }
    }


    public float getGlobalAudioGain() {
        return globalAudioGain;
    }

    public float getTargetLoudness() {
        return targetLoudness;
    }
    public void setTargetLoudness(float targetLoudness) {
        this.targetLoudness = targetLoudness;
    }
    public float getNormalizationGain() {
        return normalizationGain;
    }
    public void setNormalizationGain(float normalizationGain) {
        this.normalizationGain = normalizationGain;
    }
    public float getMaxPeakLevel() {
        return maxPeakLevel;
    }

    public void setMaxPeakLevel(float maxPeakLevel) {
        this.maxPeakLevel = maxPeakLevel;
    }

    public float getAverageRMS() {
        return averageRMS;
    }

    public void setAverageRMS(float averageRMS) {
        this.averageRMS = averageRMS;
    }

    public int getNormalizationSampleCount() {
        return normalizationSampleCount;
    }

    public void setNormalizationSampleCount(int normalizationSampleCount) {
        this.normalizationSampleCount = normalizationSampleCount;
    }

    public int getNORMALIZATION_ANALYSIS_SAMPLES() {
        return NORMALIZATION_ANALYSIS_SAMPLES;
    }

    public boolean isNormalizationCalculated() {
        return normalizationCalculated;
    }

    public void setNormalizationCalculated(boolean normalizationCalculated) {
        this.normalizationCalculated = normalizationCalculated;
    }
    public float[] getRmsHistory() {
        return rmsHistory;
    }

    public void setRmsHistory(float[] rmsHistory) {
        this.rmsHistory = rmsHistory;
    }

    public int getRmsHistoryIndex() {
        return rmsHistoryIndex;
    }

    public void setRmsHistoryIndex(int rmsHistoryIndex) {
        this.rmsHistoryIndex = rmsHistoryIndex;
    }
    public float[] getSpectrumData() {
        return spectrumData;
    }

    public void setSpectrumData(float[] spectrumData) {
        this.spectrumData = spectrumData;
    }

    public Object getSpectrumLock() {
        return spectrumLock;
    }
}
