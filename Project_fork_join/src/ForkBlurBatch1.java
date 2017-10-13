/*
* Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions
* are met:
*
*   - Redistributions of source code must retain the above copyright
*     notice, this list of conditions and the following disclaimer.
*
*   - Redistributions in binary form must reproduce the above copyright
*     notice, this list of conditions and the following disclaimer in the
*     documentation and/or other materials provided with the distribution.
*
*   - Neither the name of Oracle or the names of its
*     contributors may be used to endorse or promote products derived
*     from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
* IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
* THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
* PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
* CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
* EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
* PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
* PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
* LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
* NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
* SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * ForkBlur implements a simple horizontal image blur. It averages pixels in the
 * source array and writes them to a destination array. The sThreshold value
 * determines whether the blurring will be performed directly or split into two
 * tasks.
 *
 * This is not the recommended way to blur images; it is only intended to
 * illustrate the use of the Fork/Join framework.
 */
public class ForkBlurBatch1 extends RecursiveAction {

    private int[] mSource;
    private int mStart;
    private int mLength;
    private int[] mDestination;
    private int mBlurWidth = 15; // Processing window size, should be odd. todo: why this should be odd?

    public ForkBlurBatch1(int[] src, int start, int length, int[] dst) {
        mSource = src;
        mStart = start;
        mLength = length;
        mDestination = dst;
    }

    // Average pixels from source, write results into destination.
    protected void computeDirectly() {
        int sidePixels = (mBlurWidth - 1) / 2;
        for (int index = mStart; index < mStart + mLength; index++) {
            // Calculate average.
            float rt = 0, gt = 0, bt = 0;
            for (int mi = -sidePixels; mi <= sidePixels; mi++) {
                int mindex = Math.min(Math.max(mi + index, 0), mSource.length - 1);
                int pixel = mSource[mindex];
                rt += (float) ((pixel & 0x00ff0000) >> 16) / mBlurWidth;
                gt += (float) ((pixel & 0x0000ff00) >> 8) / mBlurWidth;
                bt += (float) ((pixel & 0x000000ff) >> 0) / mBlurWidth;
            }

            // Re-assemble destination pixel.
            int dpixel = (0xff000000) | (((int) rt) << 16) | (((int) gt) << 8) | (((int) bt) << 0);
            mDestination[index] = dpixel;
        }
    }
    protected void computeDirectly2() {
        int sidePixels = (mBlurWidth - 1) / 2;
        for (int i = mStart; i < mStart + mLength; i++) {
            int pixelDiffSum = 0;
            for(int j = mStart; j < mStart + mLength; j++){
                if (i != j){
                    pixelDiffSum += Math.abs(mSource[i] - mDestination[j]);
                }
            }
            mDestination[i] = pixelDiffSum / (mLength - 1);
        }
    }
    protected static int sThreshold = 10000;

    @Override
    protected void compute() {
        if (mLength < sThreshold) {
            computeDirectly2();
            return;
        }
        int split = mLength / 2;
        invokeAll(new ForkBlurBatch1(mSource, mStart, split, mDestination),
                new ForkBlurBatch1(mSource, mStart + split, mLength - split, mDestination));
    }

    // Plumbing follows.
    public static void main(String[] args) throws Exception {
        long startTime = System.currentTimeMillis();
        System.out.println("==============================================================");
        System.out.println("# Task 2.1: Hardware configuration.");
        String osName= System.getProperty("os.name");
        System.out.println("Operating system Name: "+ osName);
        String osType= System.getProperty("os.arch");
        System.out.println("Operating system type: "+ osType);
        System.out.println("Processor: " + System.getenv("PROCESSOR_IDENTIFIER"));
        System.out.println("Number of available processors: " + Runtime.getRuntime().availableProcessors());
        System.out.println("==============================================================");

//        String srcName = args[0];
        File imageFilesDir = new File("..\\data\\images");
        File[] listOfFiles = imageFilesDir.listFiles();
//        String srcName = "..\\data\\images\\image_1.jpg";
        String dstDir = "..\\data\\blur-images";

        for(File srcFile: listOfFiles){
            String srcName = srcFile.getName();
            BufferedImage image = ImageIO.read(srcFile);
            System.out.println("Source image: " + srcName);

            System.out.println("## Task 2.2: Blur image for threshold: " + sThreshold);
            BufferedImage blurredImage = blur(image);
            String dstName = srcName.replace(".jpg", "") ;
            String[] dstNameArr = dstName.split("\\\\");
            dstName = dstNameArr[dstNameArr.length-1] + "-blur.jpg";
            String dstFilePath = dstDir + "\\" + dstName;
            File dstFile = new File(dstFilePath);
            ImageIO.write(blurredImage, "jpg", dstFile);
            System.out.println("Output image: " + dstName);

        }
        long endTime = System.currentTimeMillis();
        System.out.println("=======================================================");
        System.out.println("# Performance:");
        System.out.println("Blured: " + listOfFiles.length + " images.");
        System.out.println("Time taken: " + ((endTime - startTime)/1000) + "s");
        System.out.println("=======================================================");
    }

    public static BufferedImage blur(BufferedImage srcImage) {
        int w = srcImage.getWidth();
        int h = srcImage.getHeight();
        System.out.println("#Task1.1 ImageHolder width: " + w);
        System.out.println("#Task1.2 ImageHolder height: " + h);
        int[] src = srcImage.getRGB(0, 0, w, h, null, 0, w);
        System.out.println("#Task1.3 Number of pixels: " + src.length);
        int[] dst = new int[src.length];
        int processors = Runtime.getRuntime().availableProcessors();
        System.out.println("#Task1.4 Threshold for splitting the computation: " + sThreshold);
        System.out.println("#Task1.5 Number of available processors: " + processors);
        ForkBlurBatch1 fb = new ForkBlurBatch1(src, 0, src.length, dst);
        ForkJoinPool pool = new ForkJoinPool();
        long startTime = System.currentTimeMillis();
        pool.invoke(fb);
        long endTime = System.currentTimeMillis();
        System.out.println("#Task1.6 Computation time of blurring one image: " + (endTime - startTime)/1000.0 + "s");
        BufferedImage dstImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        dstImage.setRGB(0, 0, w, h, dst, 0, w);
        return dstImage;
    }
}

