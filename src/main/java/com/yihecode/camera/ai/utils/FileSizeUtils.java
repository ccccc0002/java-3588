package com.yihecode.camera.ai.utils;

/**
 * 描述：对文件size进行格式化显示为B,KB,MB,GB等
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
public class FileSizeUtils {

    /**
     * 格式化文件大小
     * @param filesize
     * @return
     */
    public static String formatSize(long filesize) {
        if(filesize < 1024) {
            return filesize + "B";
        } else if((filesize / 1024) < 1024) {
            return Long.valueOf(filesize / 1024) + "KB";
        } else if((filesize / 1024 / 1024) < 1024) {
            return Long.valueOf(filesize / 1024 / 1024) + "MB";
        } else {
            return Long.valueOf(filesize / 1024 / 1024 / 1024) + "GB";
        }
    }

}
