package com.yihecode.camera.ai.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.yihecode.camera.ai.entity.Algorithm;
import com.yihecode.camera.ai.entity.Suanfa;
import com.yihecode.camera.ai.service.AlgorithmService;
import com.yihecode.camera.ai.service.CameraAlgorithmService;
import com.yihecode.camera.ai.service.OperationLogService;
import com.yihecode.camera.ai.service.RoleAccessService;
import com.yihecode.camera.ai.utils.*;
import com.yihecode.camera.ai.web.api.StreamApiController;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.*;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 算法管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@SaCheckLogin
@Controller
@RequestMapping({"/algorithm"})
public class AlgorithmController {
    private static final Logger log = LoggerFactory.getLogger(StreamApiController.class);

    //
    @Autowired
    private AlgorithmService algorithmService;

    //
    @Autowired
    private CameraAlgorithmService cameraAlgorithmService;

    @Autowired
    private RoleAccessService roleAccessService;

    @Autowired
    private OperationLogService operationLogService;

    /**
     * 打开算法列表页面
     * @return
     */
    @GetMapping({"", "/"})
    public String index() {
        return "algorithm/index";
    }

    /**
     * 打开算法表单页面
     * @param id
     * @param modelMap
     * @return
     */
    @GetMapping({"/form"})
    public String form(Long id, ModelMap modelMap) {
        if (id == null) {
            return "algorithm/form";
        }
        modelMap.addAttribute("algorithm", this.algorithmService.getById(id));
        return "algorithm/form";
    }

    /**
     * 查询算法详情
     * @param id
     * @return
     */
    @PostMapping({"/detail"})
    @ResponseBody
    public JsonResult detail(Long id) {
        Algorithm algorithm = algorithmService.getById(id);
        if(algorithm == null) {
            return JsonResultUtils.fail("找不到数据");
        }
        return JsonResultUtils.success(algorithm);
    }

    /**
     * 查询算法远程文件列表
     * @param suanfa
     * @return
     */
    @GetMapping({"/search"})
    @ResponseBody
    public JsonResult search(String suanfa) {
        //获取接口返回的文件列表
        if(suanfa ==null || suanfa.length() ==0){
            return JsonResultUtils.fail("请输入算法英文后搜索");
        }
        List<Suanfa> dataJson = getHttp(suanfa);
        //循环这个dataJson判断下载进度
        List<Suanfa> dataLocal = searchLocal(suanfa);
        List<Suanfa> newRes = new ArrayList<>();
        for(Suanfa suanfaRemote:dataJson){
            Suanfa suanfaTemNew = new Suanfa();
            suanfaTemNew.setFileName(suanfaRemote.getFileName());
            suanfaTemNew.setFileSize(suanfaRemote.getFileSize());
            suanfaTemNew.setFileSizeOriginal(suanfaRemote.getFileSizeOriginal());
            if(dataLocal.size()>0) {
                for (Suanfa suanfaTemTem : dataLocal) {
                    if (suanfaTemTem.getFileName().equals(suanfaTemNew.getFileName())) {
                        //计算百分比
                        BigDecimal localBig = new BigDecimal(suanfaTemTem.getFileSizeOriginal());
                        BigDecimal remoteBig = new BigDecimal(suanfaTemNew.getFileSizeOriginal());
                        if(!suanfaTemTem.getMd5Str().equals(suanfaRemote.getMd5Str())){
                            suanfaTemNew.setMd5Str("有更新");
                            DecimalFormat df = new DecimalFormat("#.00");
                            if (remoteBig.compareTo(new BigDecimal(0)) == 1) {
                                if(localBig.compareTo(remoteBig)==1){
                                    suanfaTemNew.setProcess("100.00");
                                }else {
                                    suanfaTemNew.setProcess(df.format(localBig.divide(remoteBig, 4, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100))));
                                }
                            } else {
                                suanfaTemNew.setProcess("100.00");
                            }
                        }else{
                            suanfaTemNew.setMd5Str("一致");
                            suanfaTemNew.setProcess("100.00");
                        }

                        break;
                    }
                }
            }else{
                suanfaTemNew.setProcess("0.00");
            }
            newRes.add(suanfaTemNew);

        }
        return JsonResultUtils.success(newRes);
    }


    private List<Suanfa> getHttp(String suanfa){
        String httpUrl = "http://101.200.212.176:8080/api/list/"+suanfa;
        //String httpUrl = "http://localhost:8080/api/list/"+suanfa;
        String response = HttpUtil.get(httpUrl);
        List<Suanfa> dataJson = new ArrayList();
        if(response == null) {
            return dataJson;
        }
        JSONObject resultJson = JSONObject.parseObject(response);

        if(resultJson.containsKey("data")) {
            if (resultJson.getString("data") != null) {
                dataJson = JSON.parseArray(resultJson.getString("data"), Suanfa.class);
            }
        }
        return dataJson;
    }
    /**
     * 查询算法本地文件列表和大小
     * @param suanfa
     * @return
     */

    private List<Suanfa> searchLocal(String suanfa) {
        //String path = "C:\\Users\\guoliang\\Downloads\\models\\"+suanfa+"\\tem";
        String path = "/data/models/"+suanfa+"/tem";
        File directoryPath = new File(path);
        //所有文件和目录的列表
        //如果拼接的目录不存在就返回不存在
        if(directoryPath.isDirectory() && directoryPath.exists()){
            //System.out.println(directoryPath.list());
        }else{
            //System.out.println("算法目录不存在:"+path);
            //return  JsonResultUtils.fail("算法目录不存在");
        }

        String[] contents = directoryPath.list();
        List<Suanfa> list = new ArrayList<>();
        //如果文件为空就返回文件为空
        if(contents==null || contents.length<= 0){
            return list;
        }

        //Date time[] = new Date[contents.length];
        String[] fileSize = new String[contents.length];
        Long[] fileSizeOriginal = new Long[contents.length];

        for(int i=0; i<contents.length; i++) {
           // File file = new File("C:\\Users\\guoliang\\Downloads\\models\\"+suanfa+"\\tem\\"+contents[i]);
            File file = new File("/data/models/"+suanfa+"/tem/"+contents[i]);
            fileSize[i] = changeFileFormat(file.length());
            fileSizeOriginal[i] =  file.length();
            Suanfa suanfa1 = new Suanfa();
            suanfa1.setFileSizeOriginal(fileSizeOriginal[i]);
            suanfa1.setFileName(contents[i]);
            suanfa1.setFileSize(fileSize[i]);
            suanfa1.setMd5Str(Md5FileUtils.getMD5(file));
            list.add(suanfa1);
        }

        return list;
    }

    private static String changeFileFormat(Long flow) {
        BigDecimal flows = new BigDecimal(flow);
        if (flows.compareTo(new BigDecimal(0)) > 0 && flows.compareTo(new BigDecimal(1024)) < 0) {//小于1M
            return flows.toString() + "B";
        } else if(flows.compareTo(new BigDecimal(1024)) >= 0 && flows.compareTo(new BigDecimal(1048576)) < 0){
            BigDecimal result = flows.divide(new BigDecimal(1024),2,BigDecimal.ROUND_HALF_UP);
            return  result.toString() + "KB";
        } else if(flows.compareTo(new BigDecimal(1048576)) >= 0 && flows.compareTo(new BigDecimal(1073741824)) < 0){
            BigDecimal result = flows.divide(new BigDecimal(1048576),2,BigDecimal.ROUND_HALF_UP);
            return  result.toString() + "MB";
        } else if(flows.compareTo(new BigDecimal(1073741824)) >= 0 && flows.compareTo(new BigDecimal("1099511627776")) < 0){
            BigDecimal result = flows.divide(new BigDecimal(1073741824),2,BigDecimal.ROUND_HALF_UP);
            return  result.toString() + "GB";
        } else if(flows.compareTo(new BigDecimal("1099511627776")) >= 0 && flows.compareTo(new BigDecimal("1125899906842624")) < 0){
            BigDecimal result = flows.divide(new BigDecimal("1099511627776"),2,BigDecimal.ROUND_HALF_UP);
            return  result.toString() + "TB";
        } else if(flows.compareTo(new BigDecimal("1125899906842624")) >= 0){
            BigDecimal result = flows.divide(new BigDecimal("1125899906842624"),2,BigDecimal.ROUND_HALF_UP);
            return  result.toString() + "PB";
        }else {
            return "0";
        }
    }

    /**
     * 查询算法远程文件列表download
     * @param suanfa
     * @return
     */
    @GetMapping({"/download"})
    @ResponseBody
    public JsonResult download(String suanfa) throws IOException {


        FtpUtils apacheFtpClient = new FtpUtils("101.200.212.176", 21, "admin", "admin");

        String dir = "/"+suanfa;
        //String localDir = "C:\\Users\\guoliang\\Downloads\\models\\"+suanfa;
        String localDir = "/data/models/"+suanfa;
        //本地目录不存在就创建目录
        File fileTmp = new File(localDir); //以某路径实例化一个File对象
        if (!fileTmp.exists()){ //如果不存在
            fileTmp.mkdirs(); //创建目录
        }
        //先下载MD5文件核对是否更新

        List<Suanfa> suanfas = getHttp(suanfa);


        File[] files = fileTmp.listFiles();
//        if(files.length>0) {
//            //现有的文件备份到目录
//            DateFormat df1 = new SimpleDateFormat("yyyyMMddhhmmss");
//            //String LocalOldPath = localDir+"\\back_"+df1.format(new Date());
////            String LocalOldPath = localDir + "/back_" + df1.format(new Date());
////            File fileTmpOld = new File(LocalOldPath); //以某路径实例化一个File对象
////            if (!fileTmpOld.exists()) { //如果不存在
////                fileTmpOld.mkdirs(); //创建目录
////            }
//
//            for (File file2 : files) {
//                //Files.copy(file2.toPath(), Paths.get(LocalOldPath + "\\" + file2.getName()));
//                if(file2.isDirectory()){
//                    continue;
//                }
//                Files.copy(file2.toPath(), Paths.get(LocalOldPath + "/" + file2.getName()));
//            }
//        }


        //下载文件到临时的tem目录，旧文件备份到当前back_2023051111目录
        //String LocalTemPath = localDir+"\\tem";
        String LocalTemPath = localDir+"/tem";
        File fileTmpTmp = new File(LocalTemPath); //以某路径实例化一个File对象
        if (!fileTmpTmp.exists()){ //如果不存在
            fileTmpTmp.mkdirs(); //创建目录
        }
        //http接口返回来所有文件列表和ftp所有文件列表
        File[] filesNew = fileTmpTmp.listFiles();
        String[] listFile = new String[suanfas.size()];
        for(int i=0;i<suanfas.size();i++){
            //检测MD5一致就不再重新下载
            Boolean ifNotContinue = false;
            for (File file3 : filesNew) {
                if (file3.getName().equals(suanfas.get(i).getFileName())) {
                    if(suanfas.get(i).getMd5Str().equals(Md5FileUtils.getMD5(file3))){
                        ifNotContinue = true;
                        break;
                    }
                }
            }
            if(ifNotContinue){
                continue;
            }

            listFile[i] = suanfas.get(i).getFileName();
        }
        String[] listAll = apacheFtpClient.getFileNameList(dir+"/");
        //检测能否读取到列表内容
        if(listAll.length==0){
            return JsonResultUtils.fail("目录内没有读取权限,请联系算法FTP服务器管理员");
        }
        Pair<Boolean, String> pair1 = downloadFileList(listFile,dir,LocalTemPath);


        //检测tem目录下载完整了再覆盖算法按文件，如果下载不完整提示下载失败

        Boolean md5Check = true;
        for (File file3 : filesNew) {
            //检测files3的md5值和接口返回的一致就执行覆盖和移动，如果不一致就重新下载3次

            for (Suanfa suanfa1 : suanfas) {
                if (file3.getName().equals(suanfa1.getFileName())) {
                    //比较结果
                    log.info("本地MD5:"+file3.getName()+" md5:"+ Md5FileUtils.getMD5(file3)+" HTTP接口返回的文件MD5:"+suanfa1.getFileName()+" md5:"+suanfa1.getMd5Str());
                    if (!Md5FileUtils.getMD5(file3).equals(suanfa1.getMd5Str())) {
                        md5Check = false;

                    }
                }
            }
        }
        //第一次下载失败
        Boolean md5Check2 = true;
        if(!md5Check){
            pair1 = downloadFileList(listFile,dir,LocalTemPath);
            File[] filesNew2 = fileTmpTmp.listFiles();
            log.info(pair1.toString());
            log.info("第一次下载失败");
            for (File file3 : filesNew2) {
                //检测files3的md5值和接口返回的一致就执行覆盖和移动，如果不一致就重新下载3次

                for (Suanfa suanfa1 : suanfas) {
                    if (file3.getName().equals(suanfa1.getFileName())) {
                        log.info("本地MD5:"+file3.getName()+" md5:"+ Md5FileUtils.getMD5(file3)+" HTTP接口返回的文件MD5:"+suanfa1.getFileName()+" md5:"+suanfa1.getMd5Str());
                        if (!Md5FileUtils.getMD5(file3).equals(suanfa1.getMd5Str())) {
                            md5Check2 = false;

                        }
                    }
                }
            }
        }

        //第2次下载失败
        Boolean md5Check3 = true;
        if(!md5Check2){
            pair1 = downloadFileList(listFile,dir,LocalTemPath);
            File[] filesNew3 = fileTmpTmp.listFiles();
            log.info(pair1.toString());
            log.info("第一次下载失败");
            for (File file3 : filesNew3) {
                //检测files3的md5值和接口返回的一致就执行覆盖和移动，如果不一致就重新下载3次
                log.info(file3.toString());
                log.info("第一次下载失败");
                for (Suanfa suanfa1 : suanfas) {
                    if (file3.getName().equals(suanfa1.getFileName())) {
                        log.info("本地MD5:"+file3.getName()+" md5:"+ Md5FileUtils.getMD5(file3)+" HTTP接口返回的文件MD5:"+suanfa1.getFileName()+" md5:"+suanfa1.getMd5Str());
                        if (!Md5FileUtils.getMD5(file3).equals(suanfa1.getMd5Str())) {
                            md5Check3 = false;
                        }
                    }
                }
            }
        }

        //第3次下载失败
        Boolean md5Check4 = true;
        if(!md5Check3){
            pair1 = downloadFileList(listFile,dir,LocalTemPath);
            File[] filesNew4 = fileTmpTmp.listFiles();
            log.info(pair1.toString());
            log.info("第一次下载失败");
            for (File file3 : filesNew4) {
                //检测files3的md5值和接口返回的一致就执行覆盖和移动，如果不一致就重新下载3次
                log.info(file3.toString());
                log.info("第一次下载失败");
                for (Suanfa suanfa1 : suanfas) {
                    if (file3.getName().equals(suanfa1.getFileName())) {
                        log.info("本地MD5:"+file3.getName()+" md5:"+ Md5FileUtils.getMD5(file3)+" HTTP接口返回的文件MD5:"+suanfa1.getFileName()+" md5:"+suanfa1.getMd5Str());
                        if (!Md5FileUtils.getMD5(file3).equals(suanfa1.getMd5Str())) {
                            md5Check4 = false;
                        }
                    }
                }
            }
        }

        if( md5Check && md5Check2 && md5Check3 && md5Check4){
            //四次下载都核对正确或者说至少一次下载正确而且核对MD5都对
            for (File file3 : filesNew) {
                //Path file4 = Paths.get(localDir + "\\" + file3.getName());
                Path file4 = Paths.get(localDir + "/" + file3.getName());
                if(Files.exists(file4)){
                    Files.delete(file4);
                }

                //Files.copy(file3.toPath(), Paths.get(localDir + "\\" + file3.getName()));
                Files.copy(file3.toPath(), Paths.get(localDir + "/" + file3.getName()));
                //不删除上次下载结果，每次下载直接覆盖
                //Files.delete(file3.toPath());
            }

            return JsonResultUtils.success("下载中");
        }else{
            return JsonResultUtils.fail("MD5核对失败，正在尝试重新下载");
        }
    }
    //重新下载文件
    private Pair<Boolean, String> downloadFileList(String[] listFile,String dir,String LocalTemPath) throws IOException {
        Pair<Boolean, String> pair1 = null;
        FtpUtils apacheFtpClient = new FtpUtils("101.200.212.176", 21, "admin", "admin");

        for(String file:listFile) {
            //Path file5 = Paths.get(LocalTemPath + "\\" + file);
            Path file5 = Paths.get(LocalTemPath + "/" + file);
            if(Files.exists(file5)){
                Files.delete(file5);
            }
            pair1 = apacheFtpClient.downloadFile(dir, LocalTemPath,file);

        }
        return pair1;
    }

    /**
     * 查询数据列表
     * @return
     */
    @PostMapping({"/listData"})
    @ResponseBody
    public PageResult listData() {
        List<Algorithm> algorithmList = this.algorithmService.list();
        if (algorithmList == null) {
            algorithmList = new ArrayList<>();
        }
        return PageResultUtils.success(null, algorithmList);
    }

    /**
     * 保存数据
     * @param algorithm
     * @return
     */
    @PostMapping({"/save"})
    @ResponseBody
    public JsonResult save(Algorithm algorithm) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            operationLogService.record("algorithm:save", "algorithmId=" + (algorithm == null ? null : algorithm.getId()), false, "permission denied", "");
            return JsonResultUtils.fail("permission denied");
        }
        if (StrUtil.isBlank(algorithm.getName())) {
            return JsonResultUtils.fail("请输入算法名称");
        }
        if (StrUtil.isBlank(algorithm.getNameEn())) {
            return JsonResultUtils.fail("请输入算法英文名称");
        }
        //检测英文名称不能重复（自己除外，删除的除外）
        List<Algorithm> listNameEn = algorithmService.listNameEn(algorithm.getNameEn());
        Boolean isExist = false;
        if(!listNameEn.isEmpty()) {
            for (Algorithm algorithm1 : listNameEn){
                if(!algorithm1.getId().equals(algorithm.getId()) && algorithm1.getNameEn().equals(algorithm.getNameEn())){
                    isExist = true;
                }
            }
        }
        if(isExist){
            return JsonResultUtils.fail("算法英文名称已存在，请勿重复添加");
        }

        // set default value
        if(algorithm.getId() == null) {
            algorithm.setFrequency(1000);
            algorithm.setIntervalTime(100);
            algorithm.setStaticsFlag(0); // 不关联显示 deng
        }

        if (algorithm.getId() == null) {
            algorithm.setCreatedAt(new Date());
        }
        if (StrUtil.isBlank(algorithm.getParams())) {
            algorithm.setParams("");
        }
        //保存模型的路徑
        algorithm.setModelPath("/data/models/"+algorithm.getNameEn());
        algorithm.setUpdatedAt(new Date());
        this.algorithmService.saveOrUpdate(algorithm);
        operationLogService.record("algorithm:save", "algorithmId=" + algorithm.getId(), true, "algorithm saved", algorithm.getNameEn());
        return JsonResultUtils.success();
    }

    /**
     * 删除数据
     * @param id
     * @return
     */
    @PostMapping({"/delete"})
    @ResponseBody
    public JsonResult delete(Long id) {
        if (!roleAccessService.canWriteSystem(currentAccountId())) {
            operationLogService.record("algorithm:delete", "algorithmId=" + id, false, "permission denied", "");
            return JsonResultUtils.fail("permission denied");
        }
        if (!this.cameraAlgorithmService.listByAlgorithm(id).isEmpty()) {
            return JsonResultUtils.fail(409, "algorithm is still bound to one or more cameras");
        }
        this.algorithmService.removeById(id);
        operationLogService.record("algorithm:delete", "algorithmId=" + id, true, "algorithm deleted", "");
        return JsonResultUtils.success();
    }

    private Long currentAccountId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    private void downloadFile(String remoteFilePath, String localFilePath) {
        URL urlfile = null;
        HttpURLConnection httpUrl = null;
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        File f = new File(localFilePath);
        try {
            urlfile = new URL(remoteFilePath);
            httpUrl = (HttpURLConnection) urlfile.openConnection();
            httpUrl.connect();
            bis = new BufferedInputStream(httpUrl.getInputStream());
            bos = new BufferedOutputStream(new FileOutputStream(f));
            int len = 2048;
            byte[] b = new byte[len];
            while ((len = bis.read(b)) != -1) {
                bos.write(b, 0, len);
            }
            bos.flush();
            bis.close();
            httpUrl.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                bis.close();
                bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}
