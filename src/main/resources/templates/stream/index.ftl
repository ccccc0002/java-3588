<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>视频流管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>
        .stream-panel { background-color: #28333E; background-color: #79C48C; background-color: #49bcf7; background: rgb(217, 236, 255); display: flex; justify-content: center; align-items: center; font-size: 16px; color: #49bcf7; }
        .stream-header { display: flex; flex-direction: row; justify-content: flex-start; align-items: center; }
        .stream-nav { display: flex; flex-direction: row; justify-content: flex-start; align-items: center; }
        .stream-nav-title { padding-right: 15px; font-weight: bold; font-size: 16px; margin-top: -3px; }
        .stream-nav-list { flex: 1; }

        .mb5 { margin-bottom: 5px; }
        .video-wrapper { display: flex; flex-direction: row; }
        .video-list-wrapper { flex: 1; }
        .video-alarm-wrapper { width: 280px; padding: 10px; margin-left: 10px; border: 1px solid #49bcf7; }
        .video-alarm-tit { border-bottom: 1px solid #49bcf7; padding-bottom: 5px; }
        .video-alarm-counter { font-weight: bold; font-size: 16px; padding: 10px 0px; text-align: center; }
        .video-alarm-counter span { padding: 0px 10px; }

        #alarm-ul-list li p span { color: #333; font-size: 16px; }
        .alltitle { font-size: 18px; color: #49bcf7; position: relative; padding-left: 12px; margin-bottom: 10px; }
        .alltitle:before { width: 5px; height: 20px; top: 2px; position: absolute; content: ""; background: #49bcf7; border-radius: 20px; left: 0; }
        .rel { position: relative; }
        .cv { position: absolute; top: 5px; left: 5px; border: 0px solid red; }
        .stop-btn { position: absolute; right: 10px; bottom: 10px; color: #F56C6C; }
        .stop-btn a { color: #F56C6C; }
        .stop-btn a i { font-size: 20px; }
        .frame { position: absolute; left: 0; top: 0; width: 0; height: 0; border: 1px solid red; }
        .statics-info { margin: 0; padding: 0;}
        .statics-header { padding: 10px 0px 10px 15px; display: flex; flex-direction: row; justify-content: space-between; align-items: center; }
        .statics-title { font-size: 16px; font-weight: bold; }
        .statics-items { padding: 0px 15px 20px 15px; display: flex; flex-direction: row; justify-content: space-between;}

        .alarm-box { margin: 0; padding: 10px; }
        .alarm-img { width: 100%; height: 100%; margin-bottom: 10px; position: relative; }
        .alarm-tit { font-size: 15px; color: #999; margin: 0; padding: 0; }
        .alarm-mask { position: absolute; border: 1px solid red; }
        .alarm_counter { text-decoration: underline; font-size: 20px; color: #f43838; }

        .main-card { display: flex; flex-direction: row; }
        .video-card { flex: 1; }
        .alarm-card { width: 300px; padding: 10px; }

        .alarm-container{width:90%;padding: 20px 20px;float: left;}
        .alarm-left{width:45%;float:left;}
        .alarm-right{width:45%;float:left;}
        .alarm-right .alarm-line{line-height: 36px;font-size: 18px}
    </style>
</head>
<body class="pear-container">
<div class="layui-row layui-col-space10">
    <div class="layui-card">
        <div class="main-card">
            <div class="video-card">
                <div class="statics-info">
                    <div class="statics-header">
                        <div class="statics-title">选取视频统计</div>
                        <div>
                            <a href="javascript:void(0)" class="layui-btn layui-btn-normal" onclick="handleStatics();">统计配置</a>
                        </div>
                    </div>
                    <div class="statics-items" id="statics-items">
                    </div>
                </div>

                <div class="stream-header">
                    <div class="stream-nav">
                        <div class="stream-nav-title">视频路数</div>
                        <div class="layui-btn-group mb5 stream-nav-list" id="btns">
                            <button type="button" class="layui-btn layui-btn-primary" onclick="show(1);">一屏</button>
                            <button type="button" class="layui-btn layui-btn-primary" onclick="show(4);">四屏</button>
                            <button type="button" class="layui-btn layui-btn-primary" onclick="show(6);">六屏</button>
                            <button type="button" class="layui-btn layui-btn-primary" onclick="show(9);">九屏</button>
                            <button type="button" class="layui-btn layui-btn-primary" onclick="showPlayConfig();">配置播放</button>
                        </div>
                    </div>
                </div>

                <div class="video-list-wrapper" id="video-list-wrapper" style="height: calc(100vh - 162px);">
                    <div class="layui-row layui-col-space10" id="video-list">
                    </div>
                </div>
            </div>
            <div class="alarm-card">
                <div class="video-alarm-tit alltitle">实时数据</div>
                <div class="video-alarm-counter">今日告警数量: <a href="javascript:void(0);" onclick="handleAlarmList();" class="alarm_counter"><span id="alarm-counter">${(counter)!'0'}</span></a></div>
                <div class="video-alarm-scroll-list" style="height: calc(100vh - 120px); overflow: auto;">
                    <div class="wrap" style="height: 0px;" id="alarm-list">
                        <ul id="alarm-ul-list">
                            <#if reportList??>
                                <#list reportList as item>
                                    <li>
                                        <div class="alarm-box">
                                            <div class="alarm-img" id="alarm_${(item.id)!''}">
                                                <img src="/report/stream?id=${(item.id)!''}" style="width: 100%; height: 100%;" params='${(item.params)!''}' onload="handleImgLoad(this, '${(item.id)!''}');" />
                                            </div>
                                            <div class="alarm-tit">告警类型：${(item.algorithmName)!''}</div>
                                            <div class="alarm-tit">所属摄像机：${(item.cameraName)!''}</div>
                                            <div class="alarm-tit">区域名称：${(item.wareName)!''}</div>
                                            <div class="alarm-tit">告警时间：${(item.alarmTime)!''}</div>
                                        </div>
                                    </li>
                                </#list>
                            </#if>
                        </ul>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
</body>
<script type="text/html" id="statics-item-tpl">
    {{each datas }}
    <div>{{ $value.name }}: <span id="statics_{{ $value.id }}">{{ $value.staticsFlagVal }}</span></div>
    {{/each}}
</script>
<script type="text/html" id="alarm-item-tpl">
    <li>
        <div class="alarm-box">
            <div class="alarm-img" id="alarm_{{ id }}">
                <img src="/report/stream?id={{ id }}" style="width: 100%; height: 100%;" params='{{ params }}' onload="handleImgLoad(this, '{{ id }}');" />
            </div>
            <div class="alarm-tit">告警类型：{{ algorithmName }}</div>
            <div class="alarm-tit">所属摄像机：{{ cameraName }}</div>
            <div class="alarm-tit">区域名称：{{ wareName }}</div>
            <div class="alarm-tit">告警时间：{{ alarmTime }}</div>
        </div>
    </li>
</script>

<script type="text/html" id="alarm-alert-tpl">
    <div  class="alarm-container" style="margin:10px 10px">
        <div class="alarm-left" style="width:100%;">
            <img src="/report/stream?id={{ id }}" style="width:100%;" params="{{ params }}" onload="handleImgLoad(this, '{{ id }}');" />
        </div>
        <div  class="alarm-right">
            <div class="alarm-line">告警类型:{{ algorithmName }}</div>
            <div class="alarm-line">所属摄像机:{{ cameraName }}</div>
            <div class="alarm-line">告警时间:{{ alarmTime }}</div>
        </div>
    </div>

</script>


<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script src="/static/js/flv.min.js"></script>
<script src="/static/js/jquery.3.6.1.min.js"></script>
<script src="/static/js/template-web.js"></script>
<script>
    layui.use(['jquery', 'util', 'loading', 'popup'], function() {
        let $ = layui.jquery;
        let loading = layui.loading;
        let popup = layui.popup;

        var cols = ${showNum!'1'};
        var seeWidth = 0;
        var seeHeight = 0;
        var wsTimer = null;
        var playerMap = new Map(); // key=containerId value=player
        var cameraMap = new Map(); // key=cameraId value=containerId
        var containerMap = new Map(); // key=containerId value=cameraId
        var frameMap = new Map(); // key=containerId value=framedecoded

        // socket
        window.initSocket = function() {
            ws = new WebSocket('${wsUrl!''}/report/${uid!''}');
            //ws = new WebSocket('ws://tjzh1.natapp1.cc/report/abc')
            ws.onopen = function () {
                wsTimer = setInterval(function() {
                    ws.send({'r': new Date().getTime()});
                }, 10000);
            };

            //
            ws.onmessage = function(event) {
                var res = JSON.parse(event.data);
                if(res['type'] == 'REPORT') {
                    window.paintCanvas(res);
                } else if(res['type'] == 'REPORT_SHOW') {
                    window.addAlarm(res);
                    window.addAlarmAlert(res);
                }
            };

            //
            ws.onclose = function() {
                ws = null;
                clearInterval(wsTimer);
                wsTimer = null;
                setTimeout(function() {
                    window.initSocket();
                }, 5000);
            }
        }

        //
        window.paintCanvas = function(json) {
            //
            var $cameraId = json['cameraId'];
            if(!cameraMap.has($cameraId)) {
                return;
            }
            //
            var $containerId = cameraMap.get($cameraId);
            if($containerId == null || $containerId == 'undefined') {
                return ;
            }
            //
            var boxw = $('#wrapper_' + $containerId).width();
            var boxh = $('#wrapper_' + $containerId).height();
            var videow = $('#' + $containerId).width();
            var videoh = $('#' + $containerId).height();
            var vw = document.getElementById($containerId).videoWidth;
            //var vh = document.getElementById($containerId).videoHeight;
            if(vw == 0) {
                return ;
            }

            var extY = 0;
            var extX = 0;
            var xratio = 0;
            if((boxw - videow) > 5) { // 视频宽度不够， 左右留白
                extX = parseInt((boxw - videow) / 2) + 1;
            } else { // 高度不够，上下留白
                extY = parseInt((boxh - videoh) / 2) + 1;
            }
            extX = extX + ((cols < 6) ? 5 : 2);
            extY = extY + ((cols < 6) ? 5 : 2);

            // 显示比例
            xratio = (videow / (vw * 2)).toFixed(2);

            var wrapper = $('#wrapper_' + $containerId);
            wrapper.find('.frame').remove();
            var ratio = wrapper.attr('ratio');
            var json = JSON.parse(json['params']);
            var len = json.length;
            for(var i = 0; i < len; i++) {
                var position = json[i]['position'];
                var tlx = parseInt(position[0] * xratio + extX);
                var tly = parseInt(position[1] * xratio + extY);
                var tlw = parseInt((position[2] - position[0]) * xratio);
                var tlh = parseInt((position[3] - position[1]) * xratio);
                var s = '<div class="frame" style="display: block; left: ' + tlx + 'px; top: ' + tly + 'px; width: ' + tlw + 'px; height: ' + tlh + 'px;">' + json[i]['type'] + '</div>';
                wrapper.append(s);
            }
            setTimeout(function() {
                var wrapper = $('#wrapper_' + $containerId);
                wrapper.find('.frame').remove();
            }, 300)
        }

        // 增加告警列表
        window.resolveAlarmLabels = function(json) {
            var labelsZh = json['alertLabelsZh'];
            if(Array.isArray(labelsZh) && labelsZh.length > 0) {
                return labelsZh;
            }

            var labels = json['alertLabels'];
            if(Array.isArray(labels) && labels.length > 0) {
                return labels;
            }

            return [];
        }
        window.resolveAlarmType = function(json) {
            var labels = window.resolveAlarmLabels(json);
            var count = parseInt(json['alertCount'], 10);
            var algorithmName = json['algorithmName'] || '';
            if(labels.length > 0) {
                algorithmName = labels.join(' / ');
            }
            if(!isNaN(count) && count > 1) {
                return algorithmName + ' x' + count;
            }
            return algorithmName;
        }
        window.buildAlarmTemplateData = function(json) {
            return {
                'cameraName': json['cameraName'],
                'algorithmName': window.resolveAlarmType(json),
                'wareName': json['wareName'],
                'alarmTime': json['alarmTime'],
                'params': json['params'],
                'id': json['id']
            };
        }
        window.addAlarm = function(json) {
            // 增加统计数量
            var $algorithmId = json['algorithmId'];
            if($('#statics_' + $algorithmId).length > 0) {
                var oldVal = parseInt($('#statics_' + $algorithmId).text());
                $('#statics_' + $algorithmId).text(oldVal + 1);
            }

            // 增加总数量
            var $alarmCounter = $('#alarm-counter').text();
            var cameraCounterOldVal = parseInt($alarmCounter);
            $('#alarm-counter').text(cameraCounterOldVal + 1);

            //
            var $templateData = window.buildAlarmTemplateData(json);
            var $liLen = $('#alarm-ul-list li').length;
            if($liLen >= 3) { // 显示4个
                $('#alarm-ul-list li:last-child').remove();
            }
            var $html = template('alarm-item-tpl', $templateData);
            $('#alarm-ul-list').prepend($html);
        }
        // 增加告警弹窗
        window.addAlarmAlert = function(json) {
            var $algorithmId = json['algorithmId'];
            //
            var $templateData = window.buildAlarmTemplateData(json);

            var $html = template('alarm-item-tpl', $templateData);

            layer.closeAll();
            layer.open({
                type: 1,
                title: '实时告警',
                shade: 0.1,
                time: 3000,
                area: ['50%', '80%'],
                content: $html
            });
        }
        // 窗口展示
        window.show = function(_cols) {
            // 关闭所有视频
            playerMap.forEach((player, containerId) => {
                window.closeVideo(containerId);
            });
            // 展示列数
            cols = _cols;
            $('#video-list').html('');
            if(cols == 1) { // 单个视频，固定当前页面的高度
                var box_height = $('#video-list-wrapper').height();
                var box_width = $(document).width() - 340;
                var rand = window.randomStr();
                $('#video-list').append('<div id="wrapper_' + rand + '" style="width: ' + box_width + 'px; height: ' + box_height + 'px;" class="rel"><a href="javascript:void(0);" onclick="openForm(\'' + rand + '\');"><div class="stream-panel lo' + rand + '"><span id="cl_' + rand + '">点击选择摄像头</span><video id="' + rand + '" style="max-width: ' + box_width +'px; max-height: ' + box_height + 'px; display: none; object-fit: contain;" muted></video></div></a><canvas id="cv_' + rand + '" class="cv" height="0" width="0" ratio="0"></canvas><div class="stop-btn"><a href="javascript:void(0);" onclick="handleCloseByHand(\'' + rand + '\');"><i class="layui-icon layui-icon-radio"></i></div></div>');
                $('.stream-panel').css('width', box_width + 'px').css('height', box_height + 'px');
            } else {
                var num = cols / 2;
                var wid = parseInt(seeWidth / num); // 每个都是正方形
                //
                var clazz = 'layui-col-xs12 layui-col-sm12 layui-col-md6 rel'
                if(cols == 6) {
                    clazz = 'layui-col-xs12 layui-col-sm12 layui-col-md4 rel';
                } else if(cols == 9) {
                    clazz = 'layui-col-xs12 layui-col-sm12 layui-col-md4 rel';
                    num = 6 / 2;
                    wid = parseInt(seeWidth / num);
                }

                //
                var box_height = $('#video-list-wrapper').height();
                var box_width = $(document).width() - 340;
                var box_sub_width = 0;
                var box_sub_height = 0;
                if(cols == 4) {
                    box_sub_width = parseInt((box_width - 10) / 2);
                    box_sub_height = parseInt((box_height - 14) / 2);
                } else if(cols == 6) {
                    box_sub_width = parseInt((box_width - 20) / 3);
                    box_sub_height = parseInt((box_height - 14) / 2);
                } else {
                    box_sub_width = parseInt((box_width - 20) / 3);
                    box_sub_height = parseInt((box_height - 22) / 3);
                }

                //
                var colNum = (cols == 4) ? 2 : 3;
                var rowNum = (cols == 4 || cols == 6) ? 2 : 3;
                for(var i = 0; i < cols; i++) {
                    var rand = window.randomStr();
                    $('#video-list').append('<div id="wrapper_' + rand + '" class="' + clazz + '" style=""><a href="javascript:void(0);" onclick="openForm(\'' + rand + '\');"><div class="stream-panel lo' + rand + '"><span id="cl_' + rand + '">点击选择摄像头</span><video id="' + rand + '" style="max-width: ' + box_sub_width +'px; max-height: ' + box_sub_height + 'px; display: none; object-fit: contain;" muted></video></div></a><canvas id="cv_' + rand + '" class="cv" height="0" width="0" ratio="0"></canvas><div class="stop-btn"><a href="javascript:void(0);" onclick="handleCloseByHand(\'' + rand + '\');"><i class="layui-icon layui-icon-radio"></i></div><div id="frame_' + rand + '"></div></div>');
                    $('.stream-panel').css('width', box_sub_width + 'px').css('height', box_sub_height + 'px');
                }
            }

            //
            var vdh = $('#video-list').height();
            $('#alarm-list').css('height', (vdh - 80) + 'px')
        }

        // 选择摄像头
        window.openForm = function(id) {
            layer.open({
                type: 2,
                title: '选择播放摄像头',
                shade: 0.1,
                area: ['90%', '90%'],
                content: '/stream/form'
            });
        }

        // 配置播放列表
        window.showPlayConfig =  function() {
            layer.open({
                type: 2,
                title: '配置播放摄像头',
                shade: 0.1,
                area: ['90%', '90%'],
                content: '/stream/select_play'
            });
        }

        // Play URL resolver for mixed legacy/zlm response payloads.
        window.resolvePlayUrl = function(data, cameraId) {
            if(typeof data === 'object' && data) {
                if(data.playUrl) {
                    return data.playUrl;
                }
                if(data.videoPort) {
                    return '${streamUrl!''}:' + data.videoPort + '/live/' + cameraId + '.flv';
                }
                return '';
            }
            if(typeof data === 'string' && data) {
                if(data.indexOf('rtsp://') === 0) {
                    return '${streamUrl!''}/live?url=' + data;
                }
                return data;
            }
            return '';
        }

        // 选择视频地址
        window.selectRtsp = function(containerId, cameraId) {
            loading.block({
                type: 2,
                elem: '.lo' + containerId,
                msg: ''
            });

            //
            if(cameraMap.has(cameraId)) {
                popup.warning('摄像头不允许重复播放');
                loading.blockRemove('.lo' + containerId, 1000);
                return ;
            }

            // 异步取地址
            // $.post('/camera/rtspUrl', {'id': cameraId}, function(res) {
            // $.post('/camera/play', {'cameraId': cameraId, 'videoPlay': 1}, function(res) {
            $.post('/camera/selectPlay', {'cameraId': cameraId}, function(res) {
                if(res.code == 0) {
                    var playUrl = window.resolvePlayUrl(res.data, cameraId);
                    if(!playUrl) {
                        loading.blockRemove('.lo' + containerId, 1000);
                        popup.failure('未获取到可用播放地址');
                        return;
                    }
                    try {
                        //
                        window.handleClose(containerId);
                        //
                        var videoElement = document.getElementById(containerId);
                        var flvPlayer = flvjs.createPlayer({
                            <#--url: '${streamUrl!''}' + cameraId + '.flv',-->
                            url: playUrl,
                            type: 'flv',
                            enableWorker: true,
                            isLive: true,
                            hasAudio: false,
                            hasVideo: true,
                            enableStashBuffer: false,
                            stashInitialSize: 128
                        });
                        flvPlayer.attachMediaElement(videoElement);
                        flvPlayer.load();
                        flvPlayer.pause();

                        //
                        playerMap.set(containerId, flvPlayer);
                        cameraMap.set(cameraId, containerId);
                        containerMap.set(containerId, cameraId);
                        //
                        videoElement.addEventListener('canplay', function (e) {
                            var originalW = e.target.videoWidth;
                            var originalH = e.target.videoHeight;
                            // if(originalW < originalH) { // 竖屏
                            //     $('#' + containerId).css('width', 'auto');
                            // } else { // 横屏
                            //     $('#' + containerId).css('height', 'auto');
                            // }
                            // $('#' + containerId).css('height', 'auto');
                            // $('#' + containerId).css('width', 'auto');
                            var boxw = $('#wrapper_' + containerId).width();
                            var ratio = (boxw > originalW) ? (originalW / boxw).toFixed(2) : (boxw / originalW).toFixed(2);
                            $('#wrapper_' + containerId).attr('ratio', ratio);
                        });

                        flvPlayer.on(flvjs.Events.ERROR, (err, errdet) => {
                            loading.blockRemove(".lo" + containerId, 1000);
                            // 参数 err 是一级异常，errdet 是二级异常
                            if (err == flvjs.ErrorTypes.MEDIA_ERROR) {
                                popup.failure('不支持的视频流类型.');
                            }
                            if (err == flvjs.ErrorTypes.NETWORK_ERROR) {
                                popup.failure('网络异常，请稍后重试.');
                                //window.closeVideo(containerId);
                            }
                            if(err == flvjs.ErrorTypes.OTHER_ERROR) {
                                popup.failure('摄像头异常，请稍后重试.');
                                //window.closeVideo(containerId);
                            }
                            //
                            //$.post('/camera/play', {'cameraId': cameraId, 'videoPlay': 0}, function(res) {});
                            //
                            window.handleClose(containerId);
                        });

                        flvPlayer.on(flvjs.Events.METADATA_ARRIVED, () => {
                            //alert('arrived')
                            $('#' + containerId).css('display', 'block');
                            loading.blockRemove('.lo' + containerId, 1000);
                            $('#cl_' + containerId).css('display', 'none');
                            flvPlayer.play();
                        });

                        // 卡住重连
                        flvPlayer.on("statistics_info", function (res) {
                            var currDecodedFrame = res.decodedFrames;
                            //
                            var cacheDecodedFrame = frameMap.get(containerId);
                            if(cacheDecodedFrame == null || cacheDecodedFrame == '' || cacheDecodedFrame == 'undefined') {
                                frameMap.set(containerId, (currDecodedFrame + '_1'));
                                return ;
                            }
                            //
                            var lastDecodedFrameStr = cacheDecodedFrame.split('_')[0];
                            var lastDecodedFrameNum = cacheDecodedFrame.split('_')[1];
                            if(parseInt(lastDecodedFrameStr) == currDecodedFrame) {
                                if(parseInt(lastDecodedFrameNum) >= 200) { // 超过20次，重启视频
                                    //
                                    window.handleClose(containerId);
                                    //
                                    window.selectRtsp(containerId, cameraId);
                                } else {
                                    frameMap.set(containerId, currDecodedFrame + '_' + (parseInt(lastDecodedFrameNum) + 1));
                                }
                            } else {
                                frameMap.set(containerId, currDecodedFrame + '_1');
                            }
                        });
                    } catch (e) {
                        loading.blockRemove('.lo' + containerId, 1000);
                        popup.failure('摄像头异常，请稍后重试.');
                    }
                } else {
                    loading.blockRemove('.lo' + containerId, 1000);
                    popup.failure(res.msg);
                }
            });
        }

        // 异常关闭视频
        window.closeVideo = function(containerId) {
            console.log('关闭播放器', containerId)
            var $player = playerMap.get(containerId);
            if($player == null) {
                //console.log('player del is null')
            } else {
                //
                try {
                    $player.pause();
                } catch (e) {
                    //
                }
                //
                try {
                    $player.detachMediaElement();
                } catch (e) {
                    //
                }
                //
                try {
                    $player.unload();
                } catch (e) {
                    //
                }
                //
                try {
                    $player.destroy();
                } catch (e) {
                    //
                }
                //
                $player = null;
            }
            playerMap.delete(containerId);
            frameMap.delete(containerId);
            var $cameraId = containerMap.get(containerId)
            if($cameraId) {
                cameraMap.delete($cameraId);
            }
            containerMap.delete(containerId);
            //$('#cl_' + containerId).css('display', 'block');
        }

        // 点击关闭按钮
        window.handleClose = function(containerId) {
            var cv = document.getElementById('cv_' + containerId);
            cv.setAttribute('width', '0px');
            cv.setAttribute('height', '0px');
            $('#' + containerId).css('display', 'none');
            $('#cl_' + containerId).css('display', 'block');
            window.closeVideo(containerId);
        }

        // 点击关闭
        window.handleCloseByHand = function(containerId) {
            // var cameraId = containerMap.get(containerId);
            // if(cameraId) {
            //     $.post('/camera/play', {'cameraId': cameraId, 'videoPlay': 0}, function(res) {});
            // }
            //
            var cv = document.getElementById('cv_' + containerId);
            cv.setAttribute('width', '0px');
            cv.setAttribute('height', '0px');
            $('#' + containerId).css('display', 'none');
            $('#cl_' + containerId).css('display', 'block');
            window.closeVideo(containerId);
        }

        // 随机字符串
        window.randomStr = function(len) {
            var charStr = 'abacdefghjklmnopqrstuvwxyzABCDEFGHJKLMNOPQRSTUVWXYZ0123456789';
            var len = charStr.length;
            var str = '';
            for (var i = 0; i < len; i++) {
                str += charStr.charAt(Math.floor(Math.random() * len));
            }
            return str;
        }

        // 统计配置管理
        window.handleStatics = function() {
            layer.open({
                type: 2,
                title: '统计配置',
                shade: 0.1,
                area: ['70%', '70%'],
                content: '/stream/formConfig'
            });
        }

        //
        window.handleStaticsTpl = function() {
            $.post('/stream/statics/algorithms', {}, function(res) {
                //console.log(res)
                if(res.code == 0) {
                    var html = template('statics-item-tpl', { 'datas': res.data });
                    //console.log(html)
                    $('#statics-items').html(html);
                }
            });
        }

        //
        window.handleStaticsData = function() {
            $.post('/stream/statics/counter', {}, function(res) {
                if(res.code == 0) {
                    $.each(res.data, function(item) {
                        //alert(item);
                    });
                }
            });
        }

        //
        window.handleImgLoad = function(obj, alarmBoxId) {
            var sWidth = $(obj).width();
            var nWidth = obj.naturalWidth;
            var ratio = (sWidth / nWidth).toFixed(2);
            var json = JSON.parse($(obj).attr('params'));
            var len = json.length;
            for(var i = 0; i < len; i++) {
                var type = json[i]['type'];
                var confidence = json[i]['confidence'];
                var position = json[i]['position'];
                var startX = position[0];
                var startY = position[1];
                var width = position[2] - position[0];
                var height = position[3] - position[1];
                $('#alarm_' + alarmBoxId).append('<div class="alarm-mask" style="left: ' + (startX * ratio) + 'px; top: ' + (startY * ratio) + 'px; width: ' + (width * ratio) + 'px; height: ' + (height * ratio) + 'px;">' + type + '/' + confidence + '</div>');
            }
        }

        // 跳转告警列表
        window.handleAlarmList = function() {
            parent.layui.admin.addTab('alarm_list', '告警列表', '/report/list_card');
        }

        // 追帧处理
        window.frameDelta = function() {
            for (var player of playerMap.values()) {
                if(player && player.buffered && player.buffered.length > 0) {
                    let end = player.buffered.end(0); //获取当前buffered值(缓冲区末尾)
                    let delta = end - player.currentTime; //获取buffered与当前播放位置的差值
                    //console.log('delta', delta, 'player', player)
                    if (delta >= 1) { // 延迟过大，通过跳帧的方式更新视频
                        player.currentTime = player.buffered.end(0) - 0.3;
                    }
                }
            }
        }

        //
        window.handleCameraActives = function() {
            $.post('/camera/actives', {}, function(res) {
                if(res.code == 0) {
                    var actives = res.data;
                    var size = actives.length;
                    //
                    if(size > 4) {
                        size = 4;
                    }
                    //
                    var wrappers = [];
                    $('#video-list > div').each(function() {
                        wrappers.push($(this).attr('id').replace('wrapper_', ''));
                    });
                    //
                    for(var i = 0; i < size; i++) {
                        var active = actives[i];
                        window.selectRtsp(wrappers[i], active['id']);
                    }
                }
            })
        }

        // 每10秒同步当前页面摄像头播放状态
        window.handleVideoPlay = function() {
            var itr = cameraMap.keys();
            var cameraIds = [];
            for(var i = 0; i < cameraMap.size; i++) {
                cameraIds.push(itr.next().value);
            }
            //console.log(JSON.stringify(cameraIds));
            $.post('/camera/refreshVideoPlay', {'cameraIds': cameraIds.join(',')}, function(res) {});
        }

        // 初始化
        $(document).ready(function() {
            seeWidth = $('#video-list-wrapper').width();
            seeHeight = $(document).height() - 70;
            //
            window.show(cols);
            //
            window.handleStaticsTpl();
            //
            window.initSocket();
            //
            setTimeout(function() {
                //
                window.handleCameraActives();
            }, 1500);
            //
            setInterval(function() {
                window.frameDelta();
            }, 2000);
            // 每10秒同步当前页面摄像头播放状态
            setInterval(function() {
                window.handleVideoPlay();
            }, 10000);
            console.log(1111111111);
            layer.open({
                type: 1,
                content: '<label>摄像头点位 违规信息 时间 图片</label>' //注意，如果str是object，那么需要字符拼接。
            });
            console.log(2222222222);
        });
    })
</script>
</html>
