<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>AI 监控驾驶舱</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>
        :root {
            --bg: #061528;
            --card: rgba(12, 34, 66, .88);
            --line: rgba(76, 155, 255, .45);
            --text: #e8f2ff;
            --sub: #9fb9dd;
        }

        html, body {
            width: 100%;
            height: 100%;
            margin: 0;
            background: radial-gradient(120% 120% at 0% 0%, #123c77 0%, var(--bg) 48%, #041122 100%);
            color: var(--text);
            overflow: hidden;
        }

        .pear-container { background: transparent; padding: 10px; }
        .main-shell { height: calc(100vh - 20px); display: flex; flex-direction: column; gap: 10px; }
        .card-shell { border: 1px solid var(--line); border-radius: 12px; background: linear-gradient(180deg, rgba(20, 53, 98, .75), var(--card)); }
        .main-card { display: flex; flex-direction: row; min-height: 0; flex: 1; }
        .video-card { flex: 1; min-width: 0; display: flex; flex-direction: column; min-height: 0; }
        .alarm-card { width: 340px; padding: 10px; min-height: 0; display: flex; flex-direction: column; }

        .statics-info { margin: 0; padding: 10px 12px 6px; }
        .statics-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
        .statics-title { font-size: 18px; font-weight: 700; color: #dbe9ff; letter-spacing: .5px; }
        .statics-subtitle { margin-top: 3px; font-size: 12px; color: var(--sub); }
        .title-wrap { display: flex; flex-direction: column; }
        .statics-items { display: flex; flex-wrap: wrap; gap: 8px; color: var(--sub); font-size: 12px; }
        .statics-items > div { padding: 2px 10px; border-radius: 999px; border: 1px solid rgba(95, 173, 255, .4); background: rgba(15, 62, 125, .45); color: #deecff; }
        .quick-kpis { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 8px; margin-bottom: 10px; }
        .quick-kpi {
            border: 1px solid rgba(95, 173, 255, .35);
            border-radius: 10px;
            background: linear-gradient(180deg, rgba(30, 84, 162, .38), rgba(7, 25, 51, .5));
            padding: 8px 10px;
            transition: transform .2s ease, border-color .2s ease, box-shadow .2s ease;
        }
        .quick-kpi:hover {
            transform: translateY(-2px);
            border-color: rgba(155, 215, 255, .75);
            box-shadow: 0 8px 20px rgba(0, 0, 0, .28);
        }
        .quick-kpi-label { font-size: 12px; color: var(--sub); line-height: 1.15; }
        .quick-kpi-value { margin-top: 3px; font-size: 22px; line-height: 1.1; font-weight: 700; color: #eff7ff; }
        .quick-kpi-value.ok { color: #56e2d9; }
        .quick-kpi-value.warn { color: #ffd27c; }

        .stream-header { display: flex; padding: 0 12px; margin-bottom: 8px; align-items: center; justify-content: space-between; }
        .stream-nav { display: flex; align-items: center; }
        .stream-nav-title { font-size: 14px; color: var(--sub); margin-right: 10px; margin-top: 5px; }
        .stream-nav-list { display: flex; gap: 8px; flex-wrap: wrap; }
        .stream-nav-list .layui-btn {
            border-radius: 8px;
            border-color: rgba(96, 176, 255, .55);
            color: #dceaff;
            background: rgba(19, 59, 118, .5);
            transition: all .2s ease;
        }
        .stream-nav-list .layui-btn:hover { border-color: rgba(162, 223, 255, .85); color: #fff; }
        .stream-nav-list .layui-btn.active { background: linear-gradient(135deg, #2b7dff, #00bcff); border-color: #b7e2ff; color: #fff; }

        .video-list-wrapper { flex: 1; min-height: 300px; padding: 0 12px 10px; }
        #video-list { height: 100%; display: grid; gap: 8px; }
        #video-list > div { min-width: 0; min-height: 0; }

        .rel > a { display: block; width: 100%; height: 100%; text-decoration: none; }
        .stream-panel {
            width: 100%;
            height: 100%;
            background: #040e1d;
            display: flex;
            justify-content: center;
            align-items: center;
            font-size: 14px;
            color: #7f98bf;
            position: relative;
            overflow: hidden;
        }
        .stream-panel video { position: absolute; left: 0; top: 0; width: 100%; height: 100%; }
        .rel { position: relative; border: 1px solid rgba(95, 173, 255, .35); border-radius: 10px; overflow: hidden; }
        .cv { position: absolute; top: 0; left: 0; border: 0; }
        .stop-btn { position: absolute; right: 8px; bottom: 8px; color: #ffadb5; z-index: 3; }
        .stop-btn a { color: #ffadb5; }
        .stop-btn a i { font-size: 18px; }
        .frame { position: absolute; left: 0; top: 0; width: 0; height: 0; border: 2px solid #ff6e78; background: rgba(255, 87, 96, .23); color: #fff; font-size: 11px; padding: 1px 3px; line-height: 1.1; white-space: nowrap; pointer-events: none; }

        .video-alarm-tit { color: #dbe9ff; font-size: 15px; font-weight: 700; margin-bottom: 8px; }
        .video-alarm-counter { color: var(--sub); font-size: 12px; margin-bottom: 8px; }
        .alarm_counter { text-decoration: underline; font-size: 18px; color: #ffd67d; margin-left: 4px; }
        .video-alarm-scroll-list { min-height: 0; flex: 1; overflow: hidden; }
        #alarm-list { height: 100%; overflow-y: auto; }

        .alarm-box {
            margin: 0;
            padding: 8px;
            border-radius: 10px;
            border: 1px solid rgba(95, 173, 255, .35);
            background: rgba(7, 22, 45, .75);
            transition: transform .2s ease, border-color .2s ease;
        }
        .alarm-box:hover {
            transform: translateY(-1px);
            border-color: rgba(152, 214, 255, .8);
        }
        .alarm-img { width: 100%; height: 110px; margin-bottom: 6px; position: relative; border-radius: 8px; overflow: hidden; background: #000; }
        .alarm-tit { font-size: 12px; color: #c5d9f7; margin: 0; padding: 0; line-height: 1.6; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .alarm-mask { position: absolute; border: 1px solid #ff6a75; background: rgba(255, 79, 88, .25); color: #fff; font-size: 11px; line-height: 1.1; padding: 1px 2px; white-space: nowrap; }

        .chart-row { height: 240px; display: grid; gap: 10px; grid-template-columns: repeat(3, minmax(0, 1fr)); }
        .chart-card {
            padding: 8px 10px;
            display: flex;
            flex-direction: column;
            border: 1px solid var(--line);
            border-radius: 12px;
            background: linear-gradient(180deg, rgba(20, 53, 98, .75), var(--card));
            animation: fadeInUp .35s ease both;
        }
        .chart-title { font-size: 14px; margin-bottom: 6px; display: flex; justify-content: space-between; align-items: center; color: #d7e9ff; }
        .pill { font-size: 12px; padding: 2px 8px; border-radius: 999px; border: 1px solid rgba(120, 194, 255, .4); background: rgba(15, 66, 125, .4); }
        .pill.warn { border-color: rgba(247, 185, 85, .6); color: #ffe5ab; background: rgba(140, 85, 15, .45); }
        .chart-box { flex: 1; min-height: 0; }

        @keyframes fadeInUp {
            0% { opacity: 0; transform: translateY(6px); }
            100% { opacity: 1; transform: translateY(0); }
        }

        @media screen and (max-width: 1100px) {
            html, body { overflow: auto; }
            .main-shell { height: auto; min-height: calc(100vh - 20px); }
            .main-card { flex-direction: column; }
            .alarm-card { width: auto; }
            .chart-row { grid-template-columns: 1fr; grid-auto-rows: 260px; height: auto; }
            .video-list-wrapper { min-height: 420px; }
            .quick-kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); }
        }
    </style>
</head>
<body class="pear-container">
<div class="main-shell">
    <div class="card-shell">
        <div class="main-card">
            <div class="video-card">
                <div class="statics-info">
                    <div class="statics-header">
                        <div class="title-wrap">
                            <div class="statics-title">AI 监控总览驾驶舱</div>
                            <div class="statics-subtitle">实时预览 · 告警联动 · 趋势统计</div>
                        </div>
                        <div>
                            <a href="javascript:void(0)" class="layui-btn layui-btn-normal" onclick="showPlayConfig();">播放配置</a>
                            <a href="javascript:void(0)" class="layui-btn layui-btn-normal" onclick="handleStatics();">统计配置</a>
                        </div>
                    </div>
                    <div class="quick-kpis">
                        <div class="quick-kpi">
                            <div class="quick-kpi-label">今日告警</div>
                            <div class="quick-kpi-value warn" id="kpi_today">${(counter)!'0'}</div>
                        </div>
                        <div class="quick-kpi">
                            <div class="quick-kpi-label">在线摄像头</div>
                            <div class="quick-kpi-value ok" id="kpi_online">-</div>
                        </div>
                        <div class="quick-kpi">
                            <div class="quick-kpi-label">摄像头总数</div>
                            <div class="quick-kpi-value" id="kpi_total">-</div>
                        </div>
                        <div class="quick-kpi">
                            <div class="quick-kpi-label">算法数量</div>
                            <div class="quick-kpi-value" id="kpi_algo">-</div>
                        </div>
                        <div class="quick-kpi">
                            <div class="quick-kpi-label">模型数量</div>
                            <div class="quick-kpi-value" id="kpi_model">-</div>
                        </div>
                    </div>
                    <div class="statics-items" id="statics-items"></div>
                </div>

                <div class="stream-header">
                    <div class="stream-nav">
                        <div class="stream-nav-title">实时预览窗口</div>
                        <div class="layui-btn-group mb5 stream-nav-list" id="btns">
                            <button type="button" class="layui-btn layui-btn-primary active" id="grid_1" onclick="show(1);">1 宫格</button>
                            <button type="button" class="layui-btn layui-btn-primary" id="grid_4" onclick="show(4);">4 宫格</button>
                            <button type="button" class="layui-btn layui-btn-primary" id="grid_9" onclick="show(9);">9 宫格</button>
                            <button type="button" class="layui-btn layui-btn-primary" id="grid_16" onclick="show(16);">16 宫格</button>
                        </div>
                    </div>
                    <div class="stream-nav-title">更新时间: <span id="dashboard_time">--</span></div>
                </div>

                <div class="video-list-wrapper" id="video-list-wrapper">
                    <div id="video-list"></div>
                </div>
            </div>
            <div class="alarm-card">
                <div class="video-alarm-tit">最新告警</div>
                <div class="video-alarm-counter">今日告警总数: <a href="javascript:void(0);" onclick="handleAlarmList();" class="alarm_counter"><span id="alarm-counter">${(counter)!'0'}</span></a></div>
                <div class="video-alarm-scroll-list">
                    <div class="wrap" id="alarm-list">
                        <ul id="alarm-ul-list">
                            <#if reportList??>
                                <#list reportList as item>
                                    <li onclick="openAlarmDetail('${(item.id)!''}');" id="alarm_item_${(item.id)!''}"
                                        data-camera="${(item.cameraName)!''}"
                                        data-algorithm="${(item.algorithmName)!''}"
                                        data-ware="${(item.wareName)!''}"
                                        data-time="${(item.alarmTime)!''}"
                                        data-params='${(item.params)!''}'>
                                        <div class="alarm-box">
                                            <div class="alarm-img" id="alarm_${(item.id)!''}">
                                                <img src="/report/stream?id=${(item.id)!''}" style="width: 100%; height: 100%;" params='${(item.params)!''}' onload="handleImgLoad(this, 'alarm_${(item.id)!''}');" />
                                            </div>
                                            <div class="alarm-tit">算法: ${(item.algorithmName)!''}</div>
                                            <div class="alarm-tit">摄像头: ${(item.cameraName)!''}</div>
                                            <div class="alarm-tit">区域: ${(item.wareName)!''}</div>
                                            <div class="alarm-tit">时间: ${(item.alarmTime)!''}</div>
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

    <div class="chart-row">
        <div class="chart-card">
            <div class="chart-title"><span>7 日告警趋势</span><span class="pill" id="telemetry-pill">telemetry: -</span></div>
            <div id="chart-trend" class="chart-box"></div>
        </div>
        <div class="chart-card">
            <div class="chart-title">告警分类占比</div>
            <div id="chart-pie" class="chart-box"></div>
        </div>
        <div class="chart-card">
            <div class="chart-title">摄像头告警排名 TOP6</div>
            <div id="chart-ranking" class="chart-box"></div>
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
    <li onclick="openAlarmDetail('{{ id }}');" id="alarm_item_{{ id }}"
        data-camera="{{ cameraName }}"
        data-algorithm="{{ algorithmName }}"
        data-ware="{{ wareName }}"
        data-time="{{ alarmTime }}"
        data-params='{{ params }}'>
        <div class="alarm-box">
            <div class="alarm-img" id="alarm_{{ id }}">
                <img src="/report/stream?id={{ id }}" style="width: 100%; height: 100%;" params='{{ params }}' onload="handleImgLoad(this, 'alarm_{{ id }}');" />
            </div>
            <div class="alarm-tit">算法: {{ algorithmName }}</div>
            <div class="alarm-tit">摄像头: {{ cameraName }}</div>
            <div class="alarm-tit">区域: {{ wareName }}</div>
            <div class="alarm-tit">时间: {{ alarmTime }}</div>
        </div>
    </li>
</script>
<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script src="/static/component/pear/module/echarts.js"></script>
<script src="/static/js/flv.min.js"></script>
<script src="/static/js/jquery.3.6.1.min.js"></script>
<script src="/static/js/template-web.js"></script>
<script>
layui.use(['jquery', 'loading', 'popup'], function() {
    let $ = layui.jquery;
    let loading = layui.loading;
    let popup = layui.popup;

    var cols = 1;
    var ws = null;
    var wsTimer = null;
    var playerMap = new Map();
    var cameraMap = new Map();
    var containerMap = new Map();
    var frameMap = new Map();

    var trendChart = null;
    var pieChart = null;
    var rankingChart = null;

    function parseArray(raw) {
        if (!raw) return [];
        if (Array.isArray(raw)) return raw;
        try {
            var data = JSON.parse(raw);
            return Array.isArray(data) ? data : [];
        } catch (e) {
            return [];
        }
    }

    function validGrid(n) {
        return n === 1 || n === 4 || n === 9 || n === 16;
    }

    function dimOf(n) {
        if (n <= 1) return 1;
        if (n <= 4) return 2;
        if (n <= 9) return 3;
        return 4;
    }

    function randomStr(len) {
        var chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
        var out = '';
        var n = len || 10;
        for (var i = 0; i < n; i++) {
            out += chars.charAt(Math.floor(Math.random() * chars.length));
        }
        return out;
    }

    function safeText(v, d) {
        if (v === null || v === undefined || v === '') return d || '';
        return String(v);
    }

    function setGridActive(n) {
        $('#btns .layui-btn').removeClass('active');
        $('#grid_' + n).addClass('active');
    }

    function getGridContainerIds() {
        var ids = [];
        $('#video-list > div').each(function() {
            var wrapperId = $(this).attr('id') || '';
            if (wrapperId.indexOf('wrapper_') === 0) {
                ids.push(wrapperId.replace('wrapper_', ''));
            }
        });
        return ids;
    }

    function getPlayingCameraIdsByGridOrder() {
        var cameraIds = [];
        var containerIds = getGridContainerIds();
        for (var i = 0; i < containerIds.length; i++) {
            var cameraId = containerMap.get(containerIds[i]);
            if (cameraId) cameraIds.push(cameraId);
        }
        return cameraIds;
    }

    function restorePlayingByCameraIds(cameraIds) {
        if (!Array.isArray(cameraIds) || cameraIds.length === 0) return;
        var containerIds = getGridContainerIds();
        var size = Math.min(containerIds.length, cameraIds.length);
        for (var i = 0; i < size; i++) {
            window.selectRtsp(containerIds[i], cameraIds[i]);
        }
    }

    function updateGridGeometry() {
        var dim = dimOf(cols);
        var vh = $('#video-list-wrapper').height();
        var gap = 8;
        var cellH = parseInt((vh - (dim - 1) * gap) / dim, 10);
        if (isNaN(cellH) || cellH < 120) cellH = 120;
        $('#video-list').css('grid-template-columns', 'repeat(' + dim + ', minmax(0, 1fr))');
        $('#video-list').css('grid-auto-rows', cellH + 'px');
    }

    function tickClock() {
        var now = new Date();
        var pad = function(v) { return v < 10 ? '0' + v : '' + v; };
        var text = now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate()) + ' ' + pad(now.getHours()) + ':' + pad(now.getMinutes()) + ':' + pad(now.getSeconds());
        $('#dashboard_time').text(text);
    }

    function renderAlarmDetail(data, autoClose) {
        var boxId = 'alarm_detail_' + randomStr(8);
        var html = '' +
            '<div class="detail-wrap">' +
            '  <div class="detail-img" id="' + boxId + '"><img src="/report/stream?id=' + data.id + '" params=\'' + data.params + '\' onload="handleImgLoad(this, \'" + boxId + "\');"/></div>' +
            '  <div class="detail-grid">' +
            '    <div class="detail-item">算法: ' + safeText(data.algorithmName, '-') + '</div>' +
            '    <div class="detail-item">摄像头: ' + safeText(data.cameraName, '-') + '</div>' +
            '    <div class="detail-item">区域: ' + safeText(data.wareName, '-') + '</div>' +
            '    <div class="detail-item">时间: ' + safeText(data.alarmTime, '-') + '</div>' +
            '  </div>' +
            '</div>';
        layer.open({
            type: 1,
            title: autoClose ? '实时告警' : '告警详情',
            shade: 0.15,
            area: ['720px', '82%'],
            time: autoClose ? 3000 : 0,
            content: html
        });
    }

    window.openAlarmDetail = function(id) {
        var item = $('#alarm_item_' + id);
        if (!item.length) return;
        renderAlarmDetail({
            id: id,
            params: item.attr('data-params') || '[]',
            cameraName: item.attr('data-camera') || '-',
            algorithmName: item.attr('data-algorithm') || '-',
            wareName: item.attr('data-ware') || '-',
            alarmTime: item.attr('data-time') || '-'
        }, false);
    };

    window.resolveAlarmLabels = function(json) {
        var zh = json.alertLabelsZh;
        if (Array.isArray(zh) && zh.length > 0) return zh;
        var labels = json.alertLabels;
        if (Array.isArray(labels) && labels.length > 0) return labels;
        return [];
    };

    window.resolveAlarmType = function(json) {
        var labels = window.resolveAlarmLabels(json);
        var count = parseInt(json.alertCount, 10);
        var name = json.algorithmName || '';
        if (labels.length > 0) name = labels.join(' / ');
        if (!isNaN(count) && count > 1) return name + ' x' + count;
        return name;
    };

    window.buildAlarmTemplateData = function(json) {
        return {
            cameraName: json.cameraName || '-',
            algorithmName: window.resolveAlarmType(json) || '-',
            wareName: json.wareName || '-',
            alarmTime: json.alarmTime || '-',
            params: json.params || '[]',
            id: json.id || ''
        };
    };

    window.addAlarm = function(json) {
        var algorithmId = json.algorithmId;
        if ($('#statics_' + algorithmId).length > 0) {
            var oldVal = parseInt($('#statics_' + algorithmId).text(), 10);
            if (isNaN(oldVal)) oldVal = 0;
            $('#statics_' + algorithmId).text(oldVal + 1);
        }

        var oldCounter = parseInt($('#alarm-counter').text(), 10);
        if (isNaN(oldCounter)) oldCounter = 0;
        $('#alarm-counter').text(oldCounter + 1);
        $('#kpi_today').text(oldCounter + 1);

        var data = window.buildAlarmTemplateData(json);
        var html = template('alarm-item-tpl', data);
        $('#alarm-ul-list').prepend(html);
        if ($('#alarm-ul-list li').length > 20) {
            $('#alarm-ul-list li:last-child').remove();
        }
    };

    window.addAlarmAlert = function(json) {
        renderAlarmDetail(window.buildAlarmTemplateData(json), true);
    };

    window.initSocket = function() {
        ws = new WebSocket('${wsUrl!''}/report/${uid!''}');
        ws.onopen = function() {
            wsTimer = setInterval(function() {
                try { ws.send('ping'); } catch (e) {}
            }, 10000);
        };

        ws.onmessage = function(event) {
            var res = JSON.parse(event.data);
            if (res.type === 'REPORT') {
                window.paintCanvas(res);
            } else if (res.type === 'REPORT_SHOW') {
                window.addAlarm(res);
                window.addAlarmAlert(res);
            }
        };

        ws.onclose = function() {
            ws = null;
            clearInterval(wsTimer);
            wsTimer = null;
            setTimeout(function() { window.initSocket(); }, 5000);
        };
    };

    window.paintCanvas = function(payload) {
        var cameraId = payload.cameraId;
        if (!cameraMap.has(cameraId)) return;

        var containerId = cameraMap.get(cameraId);
        if (!containerId) return;

        var boxw = $('#wrapper_' + containerId).width();
        var boxh = $('#wrapper_' + containerId).height();
        var videow = $('#' + containerId).width();
        var videoh = $('#' + containerId).height();
        var video = document.getElementById(containerId);
        if (!video || !video.videoWidth) return;

        var extY = 0;
        var extX = 0;
        if ((boxw - videow) > 5) {
            extX = parseInt((boxw - videow) / 2, 10) + 1;
        } else {
            extY = parseInt((boxh - videoh) / 2, 10) + 1;
        }

        extX = extX + ((cols < 6) ? 5 : 2);
        extY = extY + ((cols < 6) ? 5 : 2);

        var xratio = (videow / (video.videoWidth * 2)).toFixed(2);
        var wrapper = $('#wrapper_' + containerId);
        wrapper.find('.frame').remove();

        var arr = parseArray(payload.params);
        for (var i = 0; i < arr.length; i++) {
            var p = arr[i].position;
            if (!p || p.length < 4) continue;
            var tlx = parseInt(p[0] * xratio + extX, 10);
            var tly = parseInt(p[1] * xratio + extY, 10);
            var tlw = parseInt((p[2] - p[0]) * xratio, 10);
            var tlh = parseInt((p[3] - p[1]) * xratio, 10);
            var s = '<div class="frame" style="display:block;left:' + tlx + 'px;top:' + tly + 'px;width:' + tlw + 'px;height:' + tlh + 'px;">' + safeText(arr[i].type, 'target') + '</div>';
            wrapper.append(s);
        }

        setTimeout(function() {
            wrapper.find('.frame').remove();
        }, 300);
    };

    window.show = function(nextCols) {
        if (!validGrid(nextCols)) nextCols = 1;

        var oldPlayingCameraIds = getPlayingCameraIdsByGridOrder();

        playerMap.forEach(function(player, containerId) {
            window.closeVideo(containerId);
        });

        cols = nextCols;
        setGridActive(cols);

        $('#video-list').empty();
        updateGridGeometry();

        for (var i = 0; i < cols; i++) {
            var id = randomStr(10);
            var html = '' +
                '<div id="wrapper_' + id + '" class="rel">' +
                '  <a href="javascript:void(0);" onclick="openForm(\'' + id + '\');">' +
                '    <div class="stream-panel lo' + id + '">' +
                '      <span id="cl_' + id + '">点击选择摄像头</span>' +
                '      <video id="' + id + '" style="display:none;width:100%;height:100%;object-fit:contain;" muted></video>' +
                '    </div>' +
                '  </a>' +
                '  <div class="stop-btn"><a href="javascript:void(0);" onclick="handleCloseByHand(\'' + id + '\');"><i class="layui-icon layui-icon-close-fill"></i></a></div>' +
                '</div>';
            $('#video-list').append(html);
        }

        if (oldPlayingCameraIds.length > 0) {
            setTimeout(function() {
                restorePlayingByCameraIds(oldPlayingCameraIds);
            }, 60);
        }
    };

    window.openForm = function(id) {
        layer.open({
            type: 2,
            title: '选择摄像头',
            shade: 0.1,
            area: ['50%', '50%'],
            content: '/stream/form?id=' + id
        });
    };

    window.showPlayConfig = function() {
        layer.open({
            type: 2,
            title: '配置播放列表',
            shade: 0.1,
            area: ['80%', '80%'],
            content: '/stream/select_play'
        });
    };

    window.resolvePlayUrl = function(data, cameraId) {
        if (typeof data === 'object' && data) {
            if (data.playUrl) return data.playUrl;
            if (data.videoPort) return '${streamUrl!''}:' + data.videoPort + '/live/' + cameraId + '.flv';
            return '';
        }
        if (typeof data === 'string' && data) {
            if (data.indexOf('rtsp://') === 0) return '${streamUrl!''}/live?url=' + data;
            return data;
        }
        return '';
    };

    window.selectRtsp = function(containerId, cameraId) {
        loading.block({ type: 2, elem: '.lo' + containerId, msg: '' });

        if (cameraMap.has(cameraId)) {
            popup.warning('摄像头不允许重复播放');
            loading.blockRemove('.lo' + containerId, 1000);
            return;
        }

        $.post('/camera/selectPlay', { cameraId: cameraId }, function(res) {
            if (res.code !== 0) {
                loading.blockRemove('.lo' + containerId, 1000);
                popup.failure(res.msg);
                return;
            }

            var playUrl = window.resolvePlayUrl(res.data, cameraId);
            if (!playUrl) {
                loading.blockRemove('.lo' + containerId, 1000);
                popup.failure('未获取到可用播放地址');
                return;
            }

            try {
                window.handleClose(containerId);
                var videoElement = document.getElementById(containerId);
                var flvPlayer = flvjs.createPlayer({
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

                playerMap.set(containerId, flvPlayer);
                cameraMap.set(cameraId, containerId);
                containerMap.set(containerId, cameraId);

                flvPlayer.on(flvjs.Events.ERROR, function(err) {
                    loading.blockRemove('.lo' + containerId, 1000);
                    if (err === flvjs.ErrorTypes.MEDIA_ERROR) popup.failure('不支持的视频流格式');
                    if (err === flvjs.ErrorTypes.NETWORK_ERROR) popup.failure('网络异常，请稍后重试');
                    if (err === flvjs.ErrorTypes.OTHER_ERROR) popup.failure('摄像头异常，请稍后重试');
                    window.handleClose(containerId);
                });

                flvPlayer.on(flvjs.Events.METADATA_ARRIVED, function() {
                    $('#' + containerId).show();
                    loading.blockRemove('.lo' + containerId, 1000);
                    $('#cl_' + containerId).hide();
                    flvPlayer.play();
                });

                flvPlayer.on('statistics_info', function(s) {
                    var curr = s.decodedFrames;
                    var cache = frameMap.get(containerId);
                    if (!cache) {
                        frameMap.set(containerId, curr + '_1');
                        return;
                    }
                    var old = parseInt(cache.split('_')[0], 10);
                    var n = parseInt(cache.split('_')[1], 10);
                    if (old === curr) {
                        if (n >= 20) {
                            window.handleClose(containerId);
                            window.selectRtsp(containerId, cameraId);
                        } else {
                            frameMap.set(containerId, curr + '_' + (n + 1));
                        }
                    } else {
                        frameMap.set(containerId, curr + '_1');
                    }
                });
            } catch (e) {
                loading.blockRemove('.lo' + containerId, 1000);
                popup.failure('摄像头异常，请稍后重试');
            }
        });
    };

    window.closeVideo = function(containerId) {
        var p = playerMap.get(containerId);
        if (p) {
            try { p.pause(); } catch (e) {}
            try { p.detachMediaElement(); } catch (e) {}
            try { p.unload(); } catch (e) {}
            try { p.destroy(); } catch (e) {}
            p = null;
        }
        playerMap.delete(containerId);
        frameMap.delete(containerId);
        var cameraId = containerMap.get(containerId);
        if (cameraId) cameraMap.delete(cameraId);
        containerMap.delete(containerId);
    };

    window.handleClose = function(containerId) {
        $('#' + containerId).hide();
        $('#cl_' + containerId).show();
        window.closeVideo(containerId);
    };

    window.handleCloseByHand = function(containerId) {
        window.handleClose(containerId);
    };

    window.handleStatics = function() {
        layer.open({
            type: 2,
            title: '统计配置',
            shade: 0.1,
            area: ['70%', '70%'],
            content: '/stream/formConfig'
        });
    };

    window.handleStaticsTpl = function() {
        var renderStats = function(items) {
            if (!Array.isArray(items) || items.length === 0) {
                $('#statics-items').html('<div>未配置统计算法，请点击统计配置</div>');
                return;
            }
            var html = template('statics-item-tpl', { datas: items });
            $('#statics-items').html(html);
        };

        $.post('/stream/statics/algorithms', {}, function(res) {
            var items = [];
            if (res && res.code === 0 && Array.isArray(res.data)) items = res.data;
            if (items.length > 0) {
                renderStats(items);
                return;
            }

            $.post('/stream/dashboard/summary', {}, function(summaryRes) {
                var pie = [];
                if (summaryRes && summaryRes.code === 0 && summaryRes.data && Array.isArray(summaryRes.data.pie)) {
                    pie = summaryRes.data.pie;
                }
                var fallback = [];
                for (var i = 0; i < pie.length; i++) {
                    fallback.push({
                        id: 'summary_' + i,
                        name: pie[i].name || ('类别' + (i + 1)),
                        staticsFlagVal: pie[i].value || 0
                    });
                }
                renderStats(fallback);
            }).fail(function() {
                renderStats([]);
            });
        }).fail(function() {
            renderStats([]);
        });
    };

    window.handleImgLoad = function(obj, alarmBoxId) {
        var box = $('#' + alarmBoxId);
        if (!box.length) return;
        box.find('.alarm-mask').remove();

        var sWidth = $(obj).width();
        var nWidth = obj.naturalWidth;
        if (!nWidth) return;

        var ratio = (sWidth / nWidth).toFixed(2);
        var arr = parseArray($(obj).attr('params'));
        for (var i = 0; i < arr.length; i++) {
            var type = arr[i].type;
            var confidence = arr[i].confidence;
            var p = arr[i].position;
            if (!p || p.length < 4) continue;
            var startX = p[0];
            var startY = p[1];
            var width = p[2] - p[0];
            var height = p[3] - p[1];
            box.append('<div class="alarm-mask" style="left:' + (startX * ratio) + 'px;top:' + (startY * ratio) + 'px;width:' + (width * ratio) + 'px;height:' + (height * ratio) + 'px;">' + type + '/' + confidence + '</div>');
        }
    };

    window.handleAlarmList = function() {
        if (parent && parent.layui && parent.layui.admin) {
            parent.layui.admin.addTab('alarm_list', '告警列表', '/report/list_card');
        } else {
            location.href = '/report/list_card';
        }
    };

    window.frameDelta = function() {
        for (var player of playerMap.values()) {
            if (player && player.buffered && player.buffered.length > 0) {
                let end = player.buffered.end(0);
                let delta = end - player.currentTime;
                if (delta >= 1) {
                    player.currentTime = end - 0.3;
                }
            }
        }
    };

    window.handleCameraActives = function() {
        $.post('/camera/actives', {}, function(res) {
            if (res.code === 0) {
                var actives = res.data || [];
                var size = Math.min(cols, actives.length);
                var wrappers = [];
                $('#video-list > div').each(function() {
                    wrappers.push($(this).attr('id').replace('wrapper_', ''));
                });
                for (var i = 0; i < size; i++) {
                    window.selectRtsp(wrappers[i], actives[i].id);
                }
            }
        });
    };

    window.handleVideoPlay = function() {
        var itr = cameraMap.keys();
        var cameraIds = [];
        for (var i = 0; i < cameraMap.size; i++) {
            cameraIds.push(itr.next().value);
        }
        $.post('/camera/refreshVideoPlay', { cameraIds: cameraIds.join(',') }, function() {});
    };

    function initCharts() {
        if (typeof echarts === 'undefined') return;
        if (!trendChart) trendChart = echarts.init(document.getElementById('chart-trend'));
        if (!pieChart) pieChart = echarts.init(document.getElementById('chart-pie'));
        if (!rankingChart) rankingChart = echarts.init(document.getElementById('chart-ranking'));
    }

    function renderTrend(data) {
        if (!trendChart) return;
        trendChart.setOption({
            grid: { left: 38, right: 20, top: 20, bottom: 28 },
            xAxis: { type: 'category', boundaryGap: false, data: data.labels || [], axisLabel: { color: '#b9cff1' }, axisLine: { lineStyle: { color: '#6287bd' } } },
            yAxis: { type: 'value', axisLabel: { color: '#b9cff1' }, splitLine: { lineStyle: { color: 'rgba(120,161,219,.2)' } } },
            tooltip: { trigger: 'axis' },
            series: [{ type: 'line', smooth: true, symbol: 'circle', symbolSize: 7, data: data.values || [], lineStyle: { width: 3, color: '#2ad2ff' }, itemStyle: { color: '#68f2ff' },
                areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(45,194,255,.45)' }, { offset: 1, color: 'rgba(45,194,255,.03)' }]) } }]
        });
    }

    function renderPie(data) {
        if (!pieChart) return;
        pieChart.setOption({
            tooltip: { trigger: 'item' },
            legend: { bottom: 0, textStyle: { color: '#b9cff1' } },
            series: [{ type: 'pie', radius: ['35%', '68%'], center: ['50%', '44%'], itemStyle: { borderColor: '#081935', borderWidth: 2 }, label: { color: '#deecff' }, data: data || [] }]
        });
    }

    function renderRanking(data) {
        if (!rankingChart) return;
        rankingChart.setOption({
            grid: { left: 44, right: 16, top: 16, bottom: 28 },
            xAxis: { type: 'value', axisLabel: { color: '#b9cff1' }, splitLine: { lineStyle: { color: 'rgba(120,161,219,.2)' } } },
            yAxis: { type: 'category', data: data.labels || [], axisLabel: { color: '#cfe0ff' }, axisLine: { lineStyle: { color: '#6287bd' } } },
            tooltip: { trigger: 'axis' },
            series: [{ type: 'bar', barWidth: 14, data: data.values || [], itemStyle: { borderRadius: [0, 8, 8, 0], color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [{ offset: 0, color: '#17d6ff' }, { offset: 1, color: '#3b74ff' }]) } }]
        });
    }

    function renderTelemetry(status, err) {
        var pill = $('#telemetry-pill');
        var txt = 'telemetry: ' + (status || 'ok');
        if (err) txt += ' (' + err + ')';
        pill.text(txt);
        if (status === 'degraded') pill.addClass('warn');
        else pill.removeClass('warn');
    }

    window.refreshDashboard = function() {
        $.post('/stream/dashboard/summary', {}, function(res) {
            if (res.code !== 0 || !res.data) return;
            var data = res.data;
            var ov = data.overview || {};
            var alerts = ov.todayAlerts || 0;
            $('#alarm-counter').text(alerts);
            $('#kpi_today').text(alerts);
            $('#kpi_online').text(ov.onlineCameras || 0);
            $('#kpi_total').text(ov.totalCameras || 0);
            $('#kpi_algo').text(ov.algorithmCount || 0);
            $('#kpi_model').text(ov.modelCount || 0);

            renderTrend(data.trend || {});
            renderPie(data.pie || []);
            renderRanking(data.ranking || {});
            renderTelemetry(data.telemetry_status || 'ok', data.telemetry_error || '');
        });
    };

    $(window).on('resize', function() {
        updateGridGeometry();
        if (trendChart) trendChart.resize();
        if (pieChart) pieChart.resize();
        if (rankingChart) rankingChart.resize();
    });

    $(document).ready(function() {
        window.show(cols);
        window.handleStaticsTpl();
        window.initSocket();
        tickClock();
        setInterval(tickClock, 1000);

        initCharts();
        window.refreshDashboard();

        setTimeout(function() { window.handleCameraActives(); }, 1200);
        setInterval(function() { window.refreshDashboard(); }, 30000);
        setInterval(function() { window.frameDelta(); }, 2000);
        setInterval(function() { window.handleVideoPlay(); }, 10000);
    });
});
</script>
</html>





