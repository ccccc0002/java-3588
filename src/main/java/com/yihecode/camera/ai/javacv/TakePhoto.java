package com.yihecode.camera.ai.javacv;

import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * 通过rtsp/rtmp/file等进行拍照取图
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Slf4j
@Component
public class TakePhoto {

    /**
     * 图片存储路径
     */
    @Value("${uploadDir}")
    private String uploadDir;

    /**
     * 拍照 - 返回文件名
     * @return
     */
    public String take(String rtspUrl) {
        FFmpegLogCallback.set();
        FFmpegFrameGrabber grabber = null;
        int frameNullCount = 0;
        try {
            //rtspUrl = "/Users/zhoumingxing/Downloads/video-h265.mkv";
            grabber = new FFmpegFrameGrabber(rtspUrl);
            grabber.setOption("time", "120000000");
            grabber.setOption("rtsp_transport", "tcp");
            grabber.setOption("rtsp_flags", "prefer_tcp");
            //grabber.setFormat("h265");

            grabber.startUnsafe();

            // 从第5帧开始选取，前面几帧可能是灰色图
            int frameIndex = new Random().nextInt((150 - 20) + 1) + 20;
            int i = 0;
            while(true) {
                Frame frame = grabber.grabImage();
                if(frame != null && frame.image != null) {
                    i++;
                    if(i >= frameIndex) {
                        try {
                            Java2DFrameConverter converter = new Java2DFrameConverter();
                            String fileName = IdUtil.randomUUID() + ".jpg";
                            BufferedImage bufferedImage = converter.getBufferedImage(frame);
                            ImageIO.write(bufferedImage, "jpg", new File(uploadDir + fileName));
                            return fileName;
                        } catch (IOException E) {
                            //
                        }
                    }
                } else {
                    frameNullCount++;
                }

                // 空帧，退出
                if(frameNullCount > 10) {
                    break;
                }
            }
        } catch (FFmpegFrameGrabber.Exception e1) {
            e1.printStackTrace();
        } catch (FrameGrabber.Exception e2) {
            e2.printStackTrace();
        } catch (Exception e3) {
            e3.printStackTrace();
        } finally {
            try {
                if(grabber != null) {
                    grabber.close();
                }
            } catch (Exception e) {

            }
        }
        return null;
    }

}
