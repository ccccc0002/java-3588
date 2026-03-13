<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>统计查询</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>
        .camera-counter { display: flex; flex-direction: column; justify-content: center; align-items: center; height: 100%; }
        .camera-counter h4 { font-size: 18px; }
        .camera-counter h1 { margin-top: 20px; font-weight: bold; }
    </style>
</head>
<body class="pear-container">
<div class="layui-row layui-col-space10">
    <div class="layui-card" id="contentQuery">
        <div class="layui-card-body">
            <form class="layui-form" action="">
                <div class="layui-form-item">
                    <div class="layui-form-item layui-inline">
                        <label class="layui-form-label">快捷时间</label>
                        <div class="layui-input-inline">
                            <select name="quickRange" lay-filter="quickRange">
                                <option value="">自定义</option>
                                <option value="today">今日</option>
                                <option value="24h">最近24小时</option>
                                <option value="7d">最近7天</option>
                                <option value="30d">最近30天</option>
                            </select>
                        </div>
                    </div>
                    <div class="layui-form-item layui-inline">
                        <label class="layui-form-label">开始时间</label>
                        <div class="layui-input-inline">
                            <input type="text" name="startDate" id="startDate" value="${startDate!''}" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item layui-inline">
                        <label class="layui-form-label">结束时间</label>
                        <div class="layui-input-inline">
                            <input type="text" name="endDate" id="endDate" value="${endDate!''}" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item layui-inline">
                        <label class="layui-form-label">摄像头</label>
                        <div class="layui-input-inline">
                            <select name="cameraId">
                                <option value="">全部</option>
                                <#list cameraList as item>
                                    <option value="${(item.id)!''}">${(item.name)!''}</option>
                                </#list>
                            </select>
                        </div>
                    </div>
                    <div class="layui-form-item layui-inline">
                        <label class="layui-form-label">算法</label>
                        <div class="layui-input-inline">
                            <select name="algorithmId">
                                <option value="">全部</option>
                                <#list algorithmList as item>
                                    <option value="${(item.id)!''}">${(item.name)!''}</option>
                                </#list>
                            </select>
                        </div>
                    </div>
                    <div class="layui-form-item layui-inline">
                        <label class="layui-form-label">告警类型</label>
                        <div class="layui-input-inline">
                            <select name="type">
                                <option value="">全部</option>
                                <#list typeList as item>
                                    <option value="${(item.id)!''}">${(item.name)!''}</option>
                                </#list>
                            </select>
                        </div>
                    </div>
                    <div class="layui-form-item layui-inline">
                        <button class="pear-btn pear-btn-md pear-btn-primary" lay-submit lay-filter="query">
                            <i class="layui-icon layui-icon-search"></i>查询
                        </button>
                        <button type="reset" class="pear-btn pear-btn-md" id="resetBtn">
                            <i class="layui-icon layui-icon-refresh"></i>重置
                        </button>
                    </div>
                </div>
            </form>
        </div>
    </div>

    <div class="layui-card">
        <div class="layui-row layui-col-space10" style="margin-top: 15px;" id="contentMain">
            <div class="layui-col-xs12 layui-col-sm12 layui-col-md6">
                <div class="camera-counter">
                    <h4>摄像头总数</h4>
                    <h1>${(cameraCount)!'0'} CHANNELS</h1>
                </div>
            </div>

            <div class="layui-col-xs12 layui-col-sm12 layui-col-md6">
                <div id="algorithmRatio"></div>
            </div>

            <div class="layui-col-xs12 layui-col-sm12 layui-col-md6">
                <div id="cameraColumnChart"></div>
            </div>

            <div class="layui-col-xs12 layui-col-sm12 layui-col-md6">
                <div id="alarmTrendChart"></div>
            </div>
        </div>
    </div>
</div>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['laydate', 'form', 'jquery', 'echarts'], function() {
        let laydate = layui.laydate;
        let $ = layui.jquery;
        let form = layui.form;
        let echarts = layui.echarts;

        laydate.render({ elem: '#startDate' });
        laydate.render({ elem: '#endDate' });

        function formatDateInput(date) {
            let y = date.getFullYear();
            let m = String(date.getMonth() + 1).padStart(2, '0');
            let d = String(date.getDate()).padStart(2, '0');
            return y + '-' + m + '-' + d;
        }

        function applyQuickRange(rangeValue) {
            let now = new Date();
            let start = null;
            let end = now;
            if (rangeValue === 'today') {
                start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0);
            } else if (rangeValue === '24h') {
                start = new Date(now.getTime() - 24 * 60 * 60 * 1000);
            } else if (rangeValue === '7d') {
                start = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
            } else if (rangeValue === '30d') {
                start = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
            }
            if (start) {
                $('#startDate').val(formatDateInput(start));
                $('#endDate').val(formatDateInput(end));
            }
        }

        let dW = 0;
        let dH = 0;
        let qH = 0;
        let qW = 0;
        let algorithmRatioChart = null;
        let cameraColumnChart = null;
        let alarmTrendChart = null;

        function setHeight() {
            if (dW < 768) {
                $('#contentMain > div').each(function() {
                    $(this).css('height', dW + 'px');
                });
            } else {
                qW = parseInt((dW - 50) / 2);
                qH = parseInt((dH - qH - 60) / 2);
                $('#contentMain > div').each(function() {
                    $(this).css('height', qH + 'px');
                });
            }
        }

        function buildAlgorithmRatioChart() {
            algorithmRatioChart = echarts.init(document.getElementById('algorithmRatio'), null, { width: qW, height: qH });
            algorithmRatioChart.setOption({
                title: { text: '算法告警占比', left: 'center' },
                tooltip: { trigger: 'item' },
                legend: { orient: 'vertical', left: 'left' },
                series: [{
                    name: '算法告警占比',
                    type: 'pie',
                    radius: '50%',
                    data: [],
                    emphasis: {
                        itemStyle: {
                            shadowBlur: 10,
                            shadowOffsetX: 0,
                            shadowColor: 'rgba(0, 0, 0, 0.5)'
                        }
                    }
                }]
            });
        }

        function updateAlgorithmRatioChart(data) {
            $.post('/statistic/algorithm/ratio', data, function(res) {
                algorithmRatioChart.setOption({
                    series: [{ data: (res && res.data) ? res.data : [] }]
                });
            });
        }

        function buildCameraColumnChart() {
            cameraColumnChart = echarts.init(document.getElementById('cameraColumnChart'), null, { width: qW, height: qH });
            cameraColumnChart.setOption({
                title: { text: '摄像头告警统计' },
                xAxis: { type: 'category', data: [] },
                yAxis: { type: 'value' },
                series: [{
                    data: [],
                    type: 'bar',
                    showBackground: true,
                    backgroundStyle: { color: 'rgba(180, 180, 180, 0.2)' }
                }]
            });
        }

        function updateCameraColumnChart(data) {
            $.post('/statistic/camera', data, function(res) {
                let payload = (res && res.data) ? res.data : { xAxiss: [], values: [] };
                cameraColumnChart.setOption({
                    xAxis: { data: payload.xAxiss || [] },
                    series: [{ data: payload.values || [] }]
                });
            });
        }

        function buildAlarmTrendChart() {
            alarmTrendChart = echarts.init(document.getElementById('alarmTrendChart'), null, { width: qW, height: qH });
            alarmTrendChart.setOption({
                title: { text: '告警趋势' },
                tooltip: { trigger: 'axis' },
                xAxis: { type: 'category', data: [] },
                yAxis: { type: 'value' },
                series: [{
                    data: [],
                    type: 'line',
                    smooth: true,
                    areaStyle: {}
                }]
            });
        }

        function updateAlarmTrendChart(data) {
            $.post('/statistic/alarm/trend', data, function(res) {
                let payload = (res && res.data) ? res.data : { xAxiss: [], values: [] };
                alarmTrendChart.setOption({
                    xAxis: { data: payload.xAxiss || [] },
                    series: [{ data: payload.values || [] }]
                });
            });
        }
        function collectQueryData() {
            return {
                startDate: $('#startDate').val(),
                endDate: $('#endDate').val(),
                cameraId: $('select[name="cameraId"]').val(),
                algorithmId: $('select[name="algorithmId"]').val(),
                type: $('select[name="type"]').val()
            };
        }

        function refreshCharts(data) {
            updateAlgorithmRatioChart(data);
            updateCameraColumnChart(data);
            updateAlarmTrendChart(data);
        }

        form.on('select(quickRange)', function(data) {
            applyQuickRange(data.value || '');
            refreshCharts(collectQueryData());
        });

        form.on('submit(query)', function(data) {
            refreshCharts(data.field);
            return false;
        });

        $('#resetBtn').on('click', function() {
            setTimeout(function() {
                form.render('select');
                refreshCharts(collectQueryData());
            }, 0);
        });

        $(window).on('resize', function() {
            dW = $(document).width();
            dH = $(document).height();
            qH = $('#contentQuery').height();
            setHeight();
            if (algorithmRatioChart) { algorithmRatioChart.resize(); }
            if (cameraColumnChart) { cameraColumnChart.resize(); }
            if (alarmTrendChart) { alarmTrendChart.resize(); }
        });

        $(document).ready(function() {
            dW = $(document).width();
            dH = $(document).height();
            qH = $('#contentQuery').height();
            qW = dW;
            setHeight();

            buildAlgorithmRatioChart();
            buildCameraColumnChart();
            buildAlarmTrendChart();

            refreshCharts({
                startDate: '${startDate!''}',
                endDate: '${endDate!''}',
                cameraId: '',
                algorithmId: '',
                type: ''
            });
        });
    });
</script>
</body>
</html>
