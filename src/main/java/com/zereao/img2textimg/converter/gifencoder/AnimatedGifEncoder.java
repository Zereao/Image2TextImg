package com.zereao.img2textimg.converter.gifencoder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.*;

/**
 * Class AnimatedGifEncoder - Encodes a GIF file consisting of one or
 * more frames.
 * <pre>
 * Example:
 *    AnimatedGifEncoder e = new AnimatedGifEncoder();
 *    e.start(outputFileName);
 *    e.setDelay(1000);   // 1 frame per sec
 *    e.addFrame(image1);
 *    e.addFrame(image2);
 *    e.finish();
 * </pre>
 * No copyright asserted on the source code of this class.  May be used
 * for any purpose, however, refer to the Unisys LZW patent for restrictions
 * on use of the associated LZWEncoder class.  Please forward any corrections
 * to kweiner@fmsware.com.
 *
 * @author Kevin Weiner, FM Software
 * @version 1.03 November 2003
 */
public class AnimatedGifEncoder {
    /**
     * 图片宽度
     */
    private int width;
    /**
     * 图片高度
     */
    private int height;
    /**
     * transparent color if given 透明颜色
     */
    private Color transparent = null;
    /**
     * transparent index in color table 颜色索引
     */
    private int transIndex;
    /**
     * no repeat
     */
    private int repeat = -1;
    /**
     * frame delay (hundredths) 帧延迟 百分之一
     */
    private int delay = 0;
    /**
     * ready to output frames 输出帧
     */
    private boolean started = false;

    private OutputStream out;
    /**
     * current frame 图片
     */
    private BufferedImage image;
    /**
     * BGR byte array from frame  帧数组
     */
    private byte[] pixels;
    /**
     * converted frame indexed to palette 转换成调色板
     */
    private byte[] indexedPixels;
    /**
     * number of bit planes
     */
    private int colorDepth;
    /**
     * RGB palette 平面数
     */
    private byte[] colorTab;
    /**
     * active palette entries 活动调色板条目
     */
    private boolean[] usedEntry = new boolean[256];
    /**
     * color table size (bits-1) 颜色表大小
     */
    private int palSize = 7;
    /**
     * disposal code (-1 = use default) 处置代码（- 1 =使用默认）
     */
    private int dispose = -1;
    /**
     * close stream when finished 关闭流
     */
    private boolean closeStream = false;

    private boolean firstFrame = true;
    /**
     * if false, get size from first frame
     */
    private boolean sizeSet = false;
    /**
     * default sample interval for quantizer
     */
    private int sample = 10;

    /**
     * Sets the delay time between each frame, or changes it
     * for subsequent frames (applies to last frame added).
     *
     * @param ms int delay time in milliseconds
     */
    public void setDelay(int ms) {
        delay = Math.round(ms / 10.0f);
    }

    /**
     * Sets the GIF frame disposal code for the last added frame
     * and any subsequent frames.  Default is 0 if no transparent
     * color has been set, otherwise 2.
     *
     * @param code int disposal code.
     */
    public void setDispose(int code) {
        if (code >= 0) {
            dispose = code;
        }
    }

    /**
     * Sets the number of times the set of GIF frames
     * should be played.  Default is 1; 0 means play
     * indefinitely.  Must be invoked before the first
     * image is added.
     *
     * @param iter int number of iterations.
     */
    public void setRepeat(int iter) {
        if (iter >= 0) {
            repeat = iter;
        }
    }

    /**
     * Sets the transparent color for the last added frame
     * and any subsequent frames.
     * Since all colors are subject to modification
     * in the quantization process, the color in the final
     * palette for each frame closest to the given color
     * becomes the transparent color for that frame.
     * May be set to null to indicate no transparent color.
     *
     * @param c Color to be treated as transparent on display.
     */
    public void setTransparent(Color c) {
        transparent = c;
    }

    /**
     * Adds next GIF frame.  The frame is not written immediately, but is
     * actually deferred until the next frame is received so that timing
     * data can be inserted.  Invoking <code>finish()</code> flushes all
     * frames.  If <code>setSize</code> was not invoked, the size of the
     * first image is used for all subsequent frames.
     *
     * @param im BufferedImage containing frame to write.
     * @return true if successful.
     */
    public boolean addFrame(BufferedImage im) {
        if ((im == null) || !started) {
            return false;
        }
        boolean ok = true;
        try {
            if (!sizeSet) {
                // use first frame's size
                this.setSize(im.getWidth(), im.getHeight());
            }
            image = im;
            this.getImagePixels(); // convert to correct format if necessary
            this.analyzePixels(); // build color table & map pixels
            if (firstFrame) {
                this.writeLSD(); // logical screen descriptior
                this.writePalette(); // global color table
                if (repeat >= 0) {
                    // use NS app extension to indicate reps
                    this.writeNetscapeExt();
                }
            }
            this.writeGraphicCtrlExt(); // write graphic control extension
            this.writeImageDesc(); // image descriptor
            if (!firstFrame) {
                this.writePalette(); // local color table
            }
            this.writePixels(); // encode and write pixel data
            firstFrame = false;
        } catch (IOException e) {
            ok = false;
        }
        return ok;
    }

    /**
     * Flushes any pending data and closes output file.
     * If writing to an OutputStream, the stream is not
     * closed.
     */
    public boolean finish() {
        if (!started) {
            return false;
        }
        boolean ok = true;
        started = false;
        try {
            // gif trailer
            out.write(0x3b);
            out.flush();
            if (closeStream) {
                out.close();
            }
        } catch (IOException e) {
            ok = false;
        }
        // reset for subsequent use
        transIndex = 0;
        out = null;
        image = null;
        pixels = null;
        indexedPixels = null;
        colorTab = null;
        closeStream = false;
        firstFrame = true;
        return ok;
    }

    /**
     * Sets frame rate in frames per second.  Equivalent to
     * <code>setDelay(1000/fps)</code>.
     *
     * @param fps float frame rate (frames per second)
     */
    public void setFrameRate(float fps) {
        if (fps != 0f) {
            delay = Math.round(100f / fps);
        }
    }

    /**
     * Sets quality of color quantization (conversion of images
     * to the maximum 256 colors allowed by the GIF specification).
     * Lower values (minimum = 1) produce better colors, but slow
     * processing significantly.  10 is the default, and produces
     * good color mapping at reasonable speeds.  Values greater
     * than 20 do not yield significant improvements in speed.
     *
     * @param quality int greater than 0.
     */
    public void setQuality(int quality) {
        if (quality < 1) {
            quality = 1;
        }
        sample = quality;
    }

    /**
     * Sets the GIF frame size.  The default size is the
     * size of the first frame added if this method is
     * not invoked.
     *
     * @param w int frame width.
     * @param h int frame width.
     */
    private void setSize(int w, int h) {
        if (started && !firstFrame) {
            return;
        }
        width = w;
        height = h;
        if (width < 1) {
            width = 320;
        }
        if (height < 1) {
            height = 240;
        }
        sizeSet = true;
    }

    /**
     * Initiates GIF file creation on the given stream.  The stream
     * is not closed automatically.
     *
     * @param os OutputStream on which GIF images are written.
     * @return false if initial write failed.
     */
    private boolean start(OutputStream os) {
        if (os == null) {
            return false;
        }
        boolean ok = true;
        closeStream = false;
        out = os;
        try {
            // header
            this.writeString("GIF89a");
        } catch (IOException e) {
            ok = false;
        }
        return started = ok;
    }

    /**
     * Initiates writing of a GIF file with the specified name.
     *
     * @param file String containing output file name.
     * @return false if open or initial write failed.
     */
    @SuppressWarnings("Duplicates")
    public boolean start(String file) {
        boolean ok;
        try {
            out = new BufferedOutputStream(new FileOutputStream(file));
            ok = start(out);
            closeStream = true;
        } catch (IOException e) {
            ok = false;
        }
        return started = ok;
    }

    /**
     * Initiates writing of a GIF file with the specified name.
     *
     * @param file String containing output file name.
     * @return false if open or initial write failed.
     */
    @SuppressWarnings("Duplicates")
    public boolean start(File file) {
        boolean ok;
        try {
            out = new BufferedOutputStream(new FileOutputStream(file));
            ok = start(out);
            closeStream = true;
        } catch (IOException e) {
            ok = false;
        }
        return started = ok;
    }

    /**
     * Analyzes image colors and creates color map.
     */
    private void analyzePixels() {
        int len = pixels.length;
        int nPix = len / 3;
        indexedPixels = new byte[nPix];
        NeuQuant nq = new NeuQuant(pixels, len, sample);
        // initialize quantizer
        // create reduced palette
        colorTab = nq.process();
        // convert map from BGR to RGB
        for (int i = 0; i < colorTab.length; i += 3) {
            byte temp = colorTab[i];
            colorTab[i] = colorTab[i + 2];
            colorTab[i + 2] = temp;
            usedEntry[i / 3] = false;
        }
        // map image pixels to new palette
        int k = 0;
        for (int i = 0; i < nPix; i++) {
            int index =
                    nq.map(pixels[k++] & 0xff,
                            pixels[k++] & 0xff,
                            pixels[k++] & 0xff);
            usedEntry[index] = true;
            indexedPixels[i] = (byte) index;
        }
        pixels = null;
        colorDepth = 8;
        palSize = 7;
        // get closest match to transparent color if specified
        if (transparent != null) {
            transIndex = this.findClosest(transparent);
        }
    }

    /**
     * Returns index of palette color closest to c
     */
    private int findClosest(Color c) {
        if (colorTab == null) {
            return -1;
        }
        int r = c.getRed();
        int g = c.getGreen();
        int b = c.getBlue();
        int minpos = 0;
        int dmin = 256 * 256 * 256;
        int len = colorTab.length;
        for (int i = 0; i < len; ) {
            int dr = r - (colorTab[i++] & 0xff);
            int dg = g - (colorTab[i++] & 0xff);
            int db = b - (colorTab[i] & 0xff);
            int d = dr * dr + dg * dg + db * db;
            int index = i / 3;
            if (usedEntry[index] && (d < dmin)) {
                dmin = d;
                minpos = index;
            }
            i++;
        }
        return minpos;
    }

    /**
     * Extracts image pixels into byte array "pixels"
     */
    private void getImagePixels() {
        int w = image.getWidth();
        int h = image.getHeight();
        int type = image.getType();
        if (w != width || h != height || type != BufferedImage.TYPE_3BYTE_BGR) {
            // create new image with right size/format
            BufferedImage temp = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D graphics = temp.createGraphics();
            graphics.drawImage(image, 0, 0, null);
            image = temp;
        }
        pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
    }

    /**
     * Writes Graphic Control Extension
     */
    private void writeGraphicCtrlExt() throws IOException {
        // extension introducer
        out.write(0x21);
        // GCE label
        out.write(0xf9);
        // data block size
        out.write(4);
        int transp, disp;
        if (transparent == null) {
            transp = 0;
            // dispose = no action
            disp = 0;
        } else {
            transp = 1;
            // force clear if using transparent color
            disp = 2;
        }
        if (dispose >= 0) {
            // user override
            disp = dispose & 7;
        }
        disp <<= 2;
        // packed fields
        out.write(disp | transp);
        // delay x 1/100 sec
        this.writeShort(delay);
        // transparent color index
        out.write(transIndex);
        // block terminator
        out.write(0);
    }

    /**
     * Writes Image Descriptor
     */
    private void writeImageDesc() throws IOException {

        out.write(0x2c); // image separator
        // image position x,y = 0,0
        this.writeShort(0);
        this.writeShort(0);
        // image size
        this.writeShort(width);
        this.writeShort(height);
        // packed fields
        if (firstFrame) {
            // no LCT  - GCT is used for first (or only) frame
            out.write(0);
        } else {
            // specify normal LCT
            out.write(0x80 | palSize);
        }
    }

    /**
     * Writes Logical Screen Descriptor
     */
    private void writeLSD() throws IOException {
        // logical screen size
        this.writeShort(width);
        this.writeShort(height);
        // packed fields
        out.write((0x80 | 0x70 | palSize));
        // background color index
        out.write(0);
        // pixel aspect ratio - assume 1:1
        out.write(0);
    }

    /**
     * Writes Netscape application extension to define
     * repeat count.
     */
    private void writeNetscapeExt() throws IOException {
        // extension introducer
        out.write(0x21);
        // app extension label
        out.write(0xff);
        // block size
        out.write(11);
        // app id + auth code
        this.writeString("NETSCAPE" + "2.0");
        // sub-block size
        out.write(3);
        // loop sub-block id
        out.write(1);
        // loop count (extra iterations, 0=repeat forever)
        this.writeShort(repeat);
        // block terminator
        out.write(0);
    }

    /**
     * Writes color table
     */
    private void writePalette() throws IOException {
        out.write(colorTab, 0, colorTab.length);
        int n = (3 * 256) - colorTab.length;
        for (int i = 0; i < n; i++) {
            out.write(0);
        }
    }

    /**
     * Encodes and writes pixel data
     */
    private void writePixels() throws IOException {
        new LZWEncoder(width, height, indexedPixels, colorDepth).encode(out);
    }

    /**
     * Write 16-bit value to output stream, LSB first
     */
    private void writeShort(int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }

    /**
     * Writes string to output stream
     */
    private void writeString(String s) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            out.write((byte) s.charAt(i));
        }
    }
}
