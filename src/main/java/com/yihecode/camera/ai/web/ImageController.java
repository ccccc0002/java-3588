package com.yihecode.camera.ai.web;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;

/**
 * 图片管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Controller
@RequestMapping({"/image"})
public class ImageController {

    /**
     * 图片目录
     */
    @Value("${uploadDir}")
    private String uploadDir;

    /**
     * 上传图片
     * @param file
     * @return
     */
    @PostMapping({"/upload"})
    @ResponseBody
    public JsonResult doUpload(@RequestParam("file") MultipartFile file) {
        if (file == null) {
            return JsonResultUtils.fail("请选择文件");
        }
        String extName = FileUtil.extName(file.getOriginalFilename().toLowerCase());
        if (StrUtil.isBlank(extName)) {
            return JsonResultUtils.fail("不支持的图片格式");
        }
        String extName2 = extName.toLowerCase();
        if (!"jpg".equals(extName2) && !"jpeg".equals(extName2) && !"png".equals(extName2)) {
            return JsonResultUtils.fail(extName2 + " 不支持的图片格式");
        }
        try {
            String newFileName = IdUtil.fastSimpleUUID() + "." + extName2;
            file.transferTo(new File(this.uploadDir + newFileName));
            return JsonResultUtils.success(newFileName);
        } catch (Exception e) {
            e.printStackTrace();
            return JsonResultUtils.fail("图片上传异常，请稍后重试");
        }
    }

    /**
     * 图片输出流, web展示图片
     * @param fileName
     * @param response
     * @throws Exception
     */
    @GetMapping({"/stream"})
    public void getImageAsByteArray(String fileName, HttpServletResponse response) throws Exception {
        if (!StrUtil.isBlank(fileName)) {
            try {
                BufferedInputStream in = new BufferedInputStream(new FileInputStream(new File(this.uploadDir + fileName)));
                response.setContentType("image/jpeg");
                IOUtils.copy(in, response.getOutputStream());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}