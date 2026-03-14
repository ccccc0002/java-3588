package com.yihecode.camera.ai.web.api;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.yihecode.camera.ai.entity.*;
import com.yihecode.camera.ai.enums.MessageType;
import com.yihecode.camera.ai.enums.ReportType;
import com.yihecode.camera.ai.notify.sms.SendSmsUtil;
import com.yihecode.camera.ai.notify.wework.WeWorkRobotSendUtils;
import com.yihecode.camera.ai.service.*;
import com.yihecode.camera.ai.utils.JsonResult;
import com.yihecode.camera.ai.utils.JsonResultUtils;
import com.yihecode.camera.ai.vo.Message;
import com.yihecode.camera.ai.vo.ReportMessage;
import com.yihecode.camera.ai.websocket.MessageWebsocket;
import com.yihecode.camera.ai.websocket.ReportWebsocket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾妤犵偛顦甸弫宥夊礋椤撶姷鍘梻浣侯攰閹活亪姊介崟顖氱柧婵犻潧顑嗛悡蹇撯攽閻愰潧浜炬繛鍛崌閺屸剝鎷呴崨濠庢＆闂佸搫鐭夌紞浣割嚕椤曗偓瀹曟帒顫濋璺ㄥ笡缂傚倸鍊烽懗鑸垫叏閸偆绠惧┑鐘叉搐閽冪喖鏌￠崶鈺佹灁缂佺娀绠栭弻锝夊箛闂堟稑顫┑鐘灪鐢€愁潖閾忓湱鐭欐繛鍡樺劤閸撻亶姊洪崷顓х劸妞ゎ厾鍏橀悰顔跨疀濞戞ê绐涘銈嗙墬缁嬫垿寮搁崒鐐粹拺闁告稑锕ユ径鍕煕閹垮嫮鐣电€规洦鍨堕崺鈧い鎺戝閳锋帡鏌涚仦鍓ф噯闁稿繐鏈妵鍕閻欏懓鍚銈冨灪瀹€绋款嚕娴犲鏁囬柣鎰劋閿涙淇婇悙顏勨偓鏍偋濡ゅ啫鍨濈€广儱顧€缂嶆牠鏌￠崶鈺佹灁缂佲檧鍋撻梻鍌氬€搁悧濠勭矙閹惧瓨娅犻柟鎵閻撴洟鎮楅敐搴′簼閻忓繋鍗抽弻鐔风暋閻楀牆娈楅悗瑙勬礃閿曘垽骞冨▎鎿冩晢闁稿本绨介妷鈺傗拻濞达絽婀卞﹢浠嬫煕閳轰礁顏€规洝顫夌€靛ジ骞栭鐔告珨婵＄偑鍊栭幐鐐叏閹绢喖鍨傛繝闈涱儐閸嬶綁鏌涢妷鎴濆暞椤ユ繈姊洪崫鍕殜闁稿鎹囬弻鐔风暋閻楀牆娈楀Δ鐘靛仒缁舵岸鐛幒妤€骞㈡俊鐐村劤椤ユ艾鈹戦悩鍨毄濠殿喗娼欑叅闁挎洖鍊归崑鍌涚箾閸℃ɑ灏伴柣鎾寸懄閵囧嫰骞樼捄鐑樼€荤紓浣诡殕鐢帡鍩為幋锔绘晩闁绘挸绨堕崑鎾诲锤濡も偓閽冪喓鈧箍鍎遍悧婊冾瀶閵娾晜鈷戠紒瀣硶缁犺尙绱掔€ｎ偅灏甸柛鎺撳浮瀹曞ジ鎮㈤搹瑙勫殞闂備線鈧偛鑻晶鎾煟濞戝崬鏋熺€垫澘瀚伴獮鍥敇閻斿摜褰ㄩ梻鍌欑閹测€趁洪敃鍌氱婵炲棙鎼╅弫渚€鐓崶銊р姇闁抽攱鍨块弻鐔虹矙閸噮鍔夊銈冨劚濡鍩為幋锔绘晩闁告繂瀚ч崑鎾诲即閵忕姷鍘洪梺瑙勫礃椤曆囨煁閸ヮ剚鐓涢柛銉㈡櫅閺嬫棃�? *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Slf4j
@Controller
@RequestMapping({"/api/report"})
public class ReportApiController {

    //
    @Autowired
    private CameraService cameraService;

    //
    @Autowired
    private AlgorithmService algorithmService;

    //
    @Autowired
    private ReportPeriodService reportPeriodService;

    //
    @Autowired
    private ConfigService configService;

    //
    @Autowired
    private ReportPushService reportPushService;

    @Autowired
    private ReportPushTargetService reportPushTargetService;

    //
    @Autowired
    private ReportService reportService;

    @Autowired
    private MessageWebsocket websocket;

    @Autowired
    private ReportWebsocket reportWebsocket;

    @Autowired
    private WareHouseService wareHouseService;

    @Autowired
    private SmsPhoneService smsPhoneService;

    /**
     *
     * @param cameraId
     * @param algorithmId
     * @param fileName
     * @param params
     * @return
     */
    @RequestMapping({"", "/"})
    @ResponseBody
    public JsonResult report(@RequestParam(value = "camera_id", required = false) Long cameraId,
                             @RequestParam(value = "algorithm_id", required = false) Long algorithmId,
                             @RequestParam(value = "file_name", required = false) String fileName,
                             @RequestParam(value = "params", required = false) String params,
                             HttpServletRequest request) {
        try {
            if(cameraId == null || algorithmId == null) {
                return JsonResultUtils.fail("camera_id or algorithm_id is null");
            }

            //
            Camera camera = cameraService.getById(cameraId);
            if(camera == null) {
                return JsonResultUtils.fail("the camera is not found.");
            }

            //
            Algorithm algorithm = this.algorithmService.getById(algorithmId);
            if(algorithm == null) {
                return JsonResultUtils.fail("the algorithm is not found.");
            }

            // best-effort websocket broadcast
            try {
                ReportMessage reportMessage = new ReportMessage();
                reportMessage.setType("REPORT");
                reportMessage.setCameraId(String.valueOf(cameraId));
                reportMessage.setParams(params);
                reportWebsocket.sendToAll(JSON.toJSONString(reportMessage));
            } catch (Exception e) {
                // ignore websocket send failure
            }
            // report period check
            boolean inPeriod = false;
            Integer period = Integer.valueOf(DateUtil.format(new Date(), "HHmm"));
            List<ReportPeriod> reportPeriodList = reportPeriodService.listData(cameraId, algorithmId);
            if(reportPeriodList.isEmpty()) {
                inPeriod = true;
            } else {
                for(ReportPeriod reportPeriod : reportPeriodList) {
                    if(reportPeriod.getStartTime() <= period && period <= reportPeriod.getEndTime()) {
                        inPeriod = true;
                        break;
                    }
                }
            }
            // skip when out of period
            if(!inPeriod) {
                log.info("report push skipped {}, {}, {}, {}", camera.getName(), algorithm.getName(), fileName, params);
                return JsonResultUtils.success("out of alarm period");
            }

            //
            Report report = new Report();
            report.setCameraId(cameraId);
            report.setAlgorithmId(algorithmId);
            report.setType(ReportType.AI.getType());
            report.setFileName(fileName);
            report.setParams(params);
            report.setCreatedAt(new Date());
            report.setCreatedMills(System.currentTimeMillis());
            report.setAuditResult(0);
            report.setAuditState(0);
            int display = 0;
            // camera interval throttle
            Float intervalTime = camera.getIntervalTime();
            if(intervalTime != null && intervalTime > 0) {
                Report last = this.reportService.findLast(cameraId, algorithmId);
                if (last != null) {
                    if ((System.currentTimeMillis() - last.getCreatedMills()) < intervalTime * 1000) {
                        display = 1;
                    }
                }
            }

            //
            if(display == 0) {
                // save visible report
                report.setDisplay(display);
                this.reportService.save(report);
                // resolve warehouse name
                String wareHouseName = "-";
                Long wareHouseId = camera.getWareHouseId();
                if(wareHouseId != null && wareHouseId != 0) {
                    WareHouse wareHouse = wareHouseService.getById(wareHouseId);
                    if(wareHouse != null) {
                        wareHouseName = wareHouse.getName();
                    }
                }
                ReportMessage reportMessage = new ReportMessage();
                reportMessage.setType("REPORT_SHOW");
                reportMessage.setCameraId(String.valueOf(cameraId));
                reportMessage.setAlgorithmId(String.valueOf(algorithm.getId()));
                reportMessage.setParams(params);
                reportMessage.setCameraName(camera.getName());
                reportMessage.setAlgorithmName(algorithm.getName());
                reportMessage.setAlarmTime(DateUtil.format(new Date(), "yyyy-MM-dd MM:ss"));
                reportMessage.setWareName(wareHouseName);
                reportMessage.setId(String.valueOf(report.getId()));
                reportWebsocket.sendToAll(JSON.toJSONString(reportMessage));

                //
                Message messageVo = new Message();
                messageVo.setType(MessageType.REPORT.getType());
                messageVo.setContent(camera.getName() + " alarm: " + algorithm.getName());
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("reportId", report.getId());
                dataMap.put("cameraName", camera.getName());
                messageVo.setData(dataMap);

                ObjectMapper objectMapper = new ObjectMapper();
                SimpleModule simpleModule = new SimpleModule();
                simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
                simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
                objectMapper.registerModule(simpleModule);
                String voJson = objectMapper.writeValueAsString(messageVo);
                websocket.sendToAll(voJson);

                // 闂傚倸鍊搁崐鎼佸磹瀹勬噴褰掑炊椤掑﹦绋忔繝銏ｆ硾椤戝洭銆呴幓鎹楀綊鎮╁顔煎壈缂備讲鍋撳鑸靛姈閻撴瑩寮堕崼婵嗏挃闁伙綁浜堕弻锝夊箳閹寸姳绮甸梺闈涙搐鐎氫即鐛€ｎ喗鏅查柛娑卞幖鍟搁梻鍌欒兌椤牏鎹㈤幇鏉垮珘妞ゆ帒瀚闂佸憡娲﹂崹鎵不閿濆棛绡€闂傚牊绋掗幖鎰亜閿旇娅囩紒杈ㄦ尰閹峰懘宕滈幓鎺戝婵＄偑鍊ч梽鍕熆濮椻偓椤㈡岸濡烽埡浣侯槹濡炪倖鎸荤粙鎴炵妤ｅ啯鐓ユ繝闈涙椤庢霉濠婂懎浠遍柡?
                List<Map<String, Object>> pushTargets = reportPushTargetService.listEnabledTargets();
                if(pushTargets != null && !pushTargets.isEmpty()) {
                    try {
                        JSONObject reportMap = new JSONObject();
                        reportMap.put("cmpn_cd", "TLB");
                        reportMap.put("camera_id", String.valueOf(cameraId));
                        reportMap.put("camera_name", camera.getName());
                        reportMap.put("algorithm_id", String.valueOf(algorithmId));
                        reportMap.put("algorithm_name", algorithm.getName());
                        reportMap.put("level", "F");
                        reportMap.put("img_path", report.getFileName());
                        reportMap.put("img_ext", FileUtil.extName(report.getFileName()));
                        reportMap.put("img_name", FileUtil.getName(report.getFileName()));
                        reportMap.put("alarm_dt", DateUtil.format(new Date(), "MM/dd HH:mm"));
                        reportMap.put("report_id", String.valueOf(report.getId()));
                        reportMap.put("params", params);
                        reportMap.put("webUrl", configService.getByValTag("webUrl"));

                        for (Map<String, Object> target : pushTargets) {
                            String targetUrl = trimText(target.get("url"));
                            if (StrUtil.isBlank(targetUrl)) {
                                continue;
                            }
                            boolean includeImage = toBool(target.get("include_image"), false);
                            String bearerToken = trimText(target.get("bearer_token"));
                            reportPushService.request(targetUrl, reportMap, includeImage, fileName, bearerToken);
                        }
                    } catch (Exception e) {
                        log.warn("push report to http targets failed, reportId={}, ex={}", report.getId(), e.getMessage());
                    }
                } else {
                    log.info("report push skipped {}, {}, {}, {}", camera.getName(), algorithm.getName(), fileName, params);
                }

                String smsEnable = configService.getByValTag("smsEnable");
                if(StrUtil.isNotBlank(smsEnable) && ("true".equalsIgnoreCase(smsEnable) || "1".equals(smsEnable))) {
                    String mobiles = smsPhoneService.listPhoneStr("test");
                    if (StrUtil.isNotBlank(mobiles)) {
                        String smsApiKey = configService.getByValTag("sms_api_key");
                        String smsTplId = configService.getByValTag("sms_tpl_id");
                        String smsApiUrl = configService.getByValTag("sms_api_url");
                        SendSmsUtil.send(mobiles, camera.getName(), algorithm.getName(), smsApiKey, smsTplId, smsApiUrl);
                    }
                }
                // push to wework robot
                String weworkEnable = configService.getByValTag("weworkEnable");
                if(StrUtil.isNotBlank(weworkEnable) && "true".equals(weworkEnable)) {
                    String weworkUrl = configService.getByValTag("weworkUrl");
                    String webUrl = configService.getByValTag("webUrl");
                    if(StrUtil.isNotBlank(weworkUrl) && StrUtil.isNotBlank(webUrl)) {
                        String clickUrl = webUrl + "/report/detail?id=" + report.getId();
                        String picUrl = webUrl + "/report/stream?id=" + report.getId();
                        WeWorkRobotSendUtils.sendTextAndImage(weworkUrl, camera.getName() + "#" + algorithm.getName() + "#ALERT, " + DateUtil.format(new Date(), "MM/dd HH:mm"), clickUrl, picUrl);
                    }
                }
            }
            return JsonResultUtils.success();
        } catch (Exception e) {
            log.error("report api failed: {}", e.getMessage());
            return JsonResultUtils.fail(e.getMessage());
        }
    }

    private String trimText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private boolean toBool(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }
}



