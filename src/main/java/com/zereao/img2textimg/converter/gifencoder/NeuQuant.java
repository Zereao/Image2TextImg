package com.zereao.img2textimg.converter.gifencoder;

/**
 * NeuQuant Neural-Net Quantization Algorithm
 * ------------------------------------------
 * <p>
 * Copyright (c) 1994 Anthony Dekker
 * <p>
 * NEUQUANT Neural-Net quantization algorithm by Anthony Dekker, 1994. See
 * "Kohonen neural networks for optimal colour quantization" in "Network:
 * Computation in Neural Systems" Vol. 5 (1994) pp 351-367. for a discussion of
 * the algorithm.
 * <p>
 * Any party obtaining a copy of these files from the author, directly or
 * indirectly, is granted, free of charge, a full and unrestricted irrevocable,
 * world-wide, paid up, royalty-free, nonexclusive right and license to deal in
 * this software and documentation files (the "Software"), including without
 * limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons who
 * receive copies from any such party to do so, with the only requirement being
 * that this copyright notice remain intact.
 * <p>
 * Ported to Java 12/00 K Weiner
 */
@SuppressWarnings("Duplicates")
public class NeuQuant {
    private static final int NET_SIZE = 256;

    private static final int PRIME_1 = 499;

    private static final int PRIME_2 = 491;

    private static final int PRIME_3 = 487;

    private static final int PRIME_4 = 503;

    private static final int MIN_PICTURE_BYTES = (3 * PRIME_4);

    private static final int MAX_NET_POS = (NET_SIZE - 1);

    private static final int NET_BIAS_SHIFT = 4;

    private static final int N_CYCLES = 100;

    private static final int INT_BIAS_SHIFT = 16;

    private static final int INT_BIAS = (1 << INT_BIAS_SHIFT);

    private static final int GAMMA_SHIFT = 10;

    private static final int GAMMA = (1 << GAMMA_SHIFT);

    private static final int BETA_SHIFT = 10;

    private static final int BETA = (INT_BIAS >> BETA_SHIFT);

    private static final int BETA_GAMMA = (INT_BIAS << (GAMMA_SHIFT - BETA_SHIFT));

    private static final int INIT_RAD = (NET_SIZE >> 3);

    private static final int RADIUS_BIAS_SHIFT = 6;

    private static final int RADIUS_BIAS = (1 << RADIUS_BIAS_SHIFT);

    private static final int INIT_RADIUS = (INIT_RAD * RADIUS_BIAS);

    private static final int RADIUS_DEC = 30;

    private static final int ALPHA_BIAS_SHIFT = 10;

    private static final int INIT_ALPHA = (1 << ALPHA_BIAS_SHIFT);

    private static final int RAD_BIAS_SHIFT = 8;

    private static final int RAD_BIAS = (1 << RAD_BIAS_SHIFT);

    private static final int ALPHA_RAD_BIAS_SHIFT = (ALPHA_BIAS_SHIFT + RAD_BIAS_SHIFT);

    private static final int ALPHA_RAD_BIAS = (1 << ALPHA_RAD_BIAS_SHIFT);

    private byte[] thepicture;

    private int lengthcount;

    private int samplefac;

    private int[][] network;

    private int[] netindex = new int[256];

    private int[] bias = new int[NET_SIZE];

    private int[] freq = new int[NET_SIZE];

    private int[] radpower = new int[INIT_RAD];

    NeuQuant(byte[] thepic, int len, int sample) {
        int i;
        int[] p;
        thepicture = thepic;
        lengthcount = len;
        samplefac = sample;
        network = new int[NET_SIZE][];
        for (i = 0; i < NET_SIZE; i++) {
            network[i] = new int[4];
            p = network[i];
            p[0] = p[1] = p[2] = (i << (NET_BIAS_SHIFT + 8)) / NET_SIZE;
            freq[i] = INT_BIAS / NET_SIZE;
            bias[i] = 0;
        }
    }

    private byte[] colorMap() {
        byte[] map = new byte[3 * NET_SIZE];
        int[] index = new int[NET_SIZE];
        for (int i = 0; i < NET_SIZE; i++) {
            index[network[i][3]] = i;
        }
        int k = 0;
        for (int i = 0; i < NET_SIZE; i++) {
            int j = index[i];
            map[k++] = (byte) (network[j][0]);
            map[k++] = (byte) (network[j][1]);
            map[k++] = (byte) (network[j][2]);
        }
        return map;
    }

    private void inxbuild() {
        int i, j, smallpos, smallval;
        int[] p;
        int[] q;
        int previouscol, startpos;
        previouscol = 0;
        startpos = 0;
        for (i = 0; i < NET_SIZE; i++) {
            p = network[i];
            smallpos = i;
            smallval = p[1];
            for (j = i + 1; j < NET_SIZE; j++) {
                q = network[j];
                if (q[1] < smallval) {
                    smallpos = j;
                    smallval = q[1];
                }
            }
            q = network[smallpos];
            if (i != smallpos) {
                j = q[0];
                q[0] = p[0];
                p[0] = j;
                j = q[1];
                q[1] = p[1];
                p[1] = j;
                j = q[2];
                q[2] = p[2];
                p[2] = j;
                j = q[3];
                q[3] = p[3];
                p[3] = j;
            }
            if (smallval != previouscol) {
                netindex[previouscol] = (startpos + i) >> 1;
                for (j = previouscol + 1; j < smallval; j++) {
                    netindex[j] = i;
                }
                previouscol = smallval;
                startpos = i;
            }
        }
        netindex[previouscol] = (startpos + MAX_NET_POS) >> 1;
        for (j = previouscol + 1; j < 256; j++) {
            netindex[j] = MAX_NET_POS;
        }
    }

    private void learn() {
        int i, j, b, g, r;
        int radius, rad, alpha, step, delta, samplepixels;
        byte[] p;
        int pix, lim;
        if (lengthcount < MIN_PICTURE_BYTES) {
            samplefac = 1;
        }
        /**
         * biased by 10 bits
         */
        int alphadec = 30 + ((samplefac - 1) / 3);
        p = thepicture;
        pix = 0;
        lim = lengthcount;
        samplepixels = lengthcount / (3 * samplefac);
        delta = samplepixels / N_CYCLES;
        alpha = INIT_ALPHA;
        radius = INIT_RADIUS;

        rad = radius >> RADIUS_BIAS_SHIFT;
        for (i = 0; i < rad; i++) {
            radpower[i] = alpha * (((rad * rad - i * i) * RAD_BIAS) / (rad * rad));
        }
        // fprintf(stderr,"beginning 1D learning: initial radius=%d\n", rad);
        if (lengthcount < MIN_PICTURE_BYTES) {
            step = 3;
        } else if ((lengthcount % PRIME_1) != 0) {
            step = 3 * PRIME_1;
        } else {
            if ((lengthcount % PRIME_2) != 0) {
                step = 3 * PRIME_2;
            } else {
                if ((lengthcount % PRIME_3) != 0) {
                    step = 3 * PRIME_3;
                } else {
                    step = 3 * PRIME_4;
                }
            }
        }

        i = 0;
        while (i < samplepixels) {
            b = (p[pix] & 0xff) << NET_BIAS_SHIFT;
            g = (p[pix + 1] & 0xff) << NET_BIAS_SHIFT;
            r = (p[pix + 2] & 0xff) << NET_BIAS_SHIFT;
            j = this.contest(b, g, r);
            this.altersingle(alpha, j, b, g, r);
            if (rad != 0) {
                this.alterneigh(rad, j, b, g, r);
            }
            pix += step;
            if (pix >= lim) {
                pix -= lengthcount;
            }
            i++;
            if (delta == 0) {
                delta = 1;
            }
            if (i % delta == 0) {
                alpha -= alpha / alphadec;
                radius -= radius / RADIUS_DEC;
                rad = radius >> RADIUS_BIAS_SHIFT;
                if (rad <= 1) {
                    rad = 0;
                }
                for (j = 0; j < rad; j++) {
                    radpower[j] = alpha * (((rad * rad - j * j) * RAD_BIAS) / (rad * rad));
                }
            }
        }
    }

    public int map(int b, int g, int r) {
        int i, j, dist, a, bestd;
        int[] p;
        int best;
        bestd = 1000;
        best = -1;
        i = netindex[g];
        j = i - 1;
        while ((i < NET_SIZE) || (j >= 0)) {
            if (i < NET_SIZE) {
                p = network[i];
                dist = p[1] - g;
                if (dist >= bestd) {
                    i = NET_SIZE;
                } else {
                    i++;
                    if (dist < 0) {
                        dist = -dist;
                    }
                    a = p[0] - b;
                    if (a < 0) {
                        a = -a;
                    }
                    dist += a;
                    if (dist < bestd) {
                        a = p[2] - r;
                        if (a < 0) {
                            a = -a;
                        }
                        dist += a;
                        if (dist < bestd) {
                            bestd = dist;
                            best = p[3];
                        }
                    }
                }
            }
            if (j >= 0) {
                p = network[j];
                dist = g - p[1];
                if (dist >= bestd) {
                    j = -1;
                } else {
                    j--;
                    if (dist < 0) {
                        dist = -dist;
                    }
                    a = p[0] - b;
                    if (a < 0) {
                        a = -a;
                    }
                    dist += a;
                    if (dist < bestd) {
                        a = p[2] - r;
                        if (a < 0) {
                            a = -a;
                        }
                        dist += a;
                        if (dist < bestd) {
                            bestd = dist;
                            best = p[3];
                        }
                    }
                }
            }
        }
        return (best);
    }

    byte[] process() {
        this.learn();
        this.unbiasnet();
        this.inxbuild();
        return this.colorMap();
    }

    private void unbiasnet() {
        int i;
        for (i = 0; i < NET_SIZE; i++) {
            network[i][0] >>= NET_BIAS_SHIFT;
            network[i][1] >>= NET_BIAS_SHIFT;
            network[i][2] >>= NET_BIAS_SHIFT;
            network[i][3] = i;
        }
    }

    private void alterneigh(int rad, int i, int b, int g, int r) {
        int j, k, lo, hi, a, m;
        int[] p;
        lo = i - rad;
        if (lo < -1) {
            lo = -1;
        }
        hi = i + rad;
        if (hi > NET_SIZE) {
            hi = NET_SIZE;
        }
        j = i + 1;
        k = i - 1;
        m = 1;
        while ((j < hi) || (k > lo)) {
            a = radpower[m++];
            if (j < hi) {
                p = network[j++];
                p[0] -= (a * (p[0] - b)) / ALPHA_RAD_BIAS;
                p[1] -= (a * (p[1] - g)) / ALPHA_RAD_BIAS;
                p[2] -= (a * (p[2] - r)) / ALPHA_RAD_BIAS;
            }
            if (k > lo) {
                p = network[k--];
                p[0] -= (a * (p[0] - b)) / ALPHA_RAD_BIAS;
                p[1] -= (a * (p[1] - g)) / ALPHA_RAD_BIAS;
                p[2] -= (a * (p[2] - r)) / ALPHA_RAD_BIAS;
            }
        }
    }

    private void altersingle(int alpha, int i, int b, int g, int r) {
        int[] n = network[i];
        n[0] -= (alpha * (n[0] - b)) / INIT_ALPHA;
        n[1] -= (alpha * (n[1] - g)) / INIT_ALPHA;
        n[2] -= (alpha * (n[2] - r)) / INIT_ALPHA;
    }

    private int contest(int b, int g, int r) {
        /* finds closest neuron (min dist) and updates freq */
        /* finds best neuron (min dist-bias) and returns position */
        /* for frequently chosen neurons, freq[i] is high and bias[i] is negative */
        /* bias[i] = GAMMA*((1/NET_SIZE)-freq[i]) */

        int i, dist, a, biasdist, betafreq;
        int bestpos, bestbiaspos, bestd, bestbiasd;
        int[] n;
        bestd = ~(1 << 31);
        bestbiasd = bestd;
        bestpos = -1;
        bestbiaspos = bestpos;
        for (i = 0; i < NET_SIZE; i++) {
            n = network[i];
            dist = n[0] - b;
            if (dist < 0) {
                dist = -dist;
            }
            a = n[1] - g;
            if (a < 0) {
                a = -a;
            }
            dist += a;
            a = n[2] - r;
            if (a < 0) {
                a = -a;
            }
            dist += a;
            if (dist < bestd) {
                bestd = dist;
                bestpos = i;
            }
            biasdist = dist - ((bias[i]) >> (INT_BIAS_SHIFT - NET_BIAS_SHIFT));
            if (biasdist < bestbiasd) {
                bestbiasd = biasdist;
                bestbiaspos = i;
            }
            betafreq = (freq[i] >> BETA_SHIFT);
            freq[i] -= betafreq;
            bias[i] += (betafreq << GAMMA_SHIFT);
        }
        freq[bestpos] += BETA;
        bias[bestpos] -= BETA_GAMMA;
        return bestbiaspos;
    }
}