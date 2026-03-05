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
                        <label class="layui-form-label">开始时间</label>
                        <div class="layui-input-inline">
                            <input type="text" name="startDate" id="startDate" value="${startDate!''}" placeholder="" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item layui-inline">
                        <label class="layui-form-label">结束时间</label>
                        <div class="layui-input-inline">
                            <input type="text" name="endDate" id="endDate" value="${endDate!''}" placeholder="" class="layui-input">
                        </div>
                    </div>
                    <div class="layui-form-item layui-inline">
                        <button class="pear-btn pear-btn-md pear-btn-primary" lay-submit lay-filter="query">
                            <i class="layui-icon layui-icon-search"></i>
                            查询
                        </button>
                        <button type="reset" class="pear-btn pear-btn-md">
                            <i class="layui-icon layui-icon-refresh"></i>
                            重置
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
                    <h4>摄像头</h4>
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
                <div id="cameraAlgorithmChart"></div>
            </div>
        </div>
    </div>
</div>
</body>
<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['laydate', 'form', 'jquery', 'util', 'popup', 'echarts'], function() {
        let laydate = layui.laydate;
        let $ = layui.jquery;
        let form = layui.form;
        let echarts = layui.echarts;

        laydate.render({
            elem: '#startDate'
        });

        laydate.render({
            elem: '#endDate'
        });

        form.on('submit(query)', function(data) {
            window.updateAlgorithmRatioChart(data.field);
            window.updateCameraColumnChart(data.field);
            window.updateCameraAlgorithmChart(data.field);
            return false;
        })

        var dW = 0;
        var dH = 0;
        var qH = 0;
        var qW = 0;
        var algorithmRatioChart = null;
        var cameraColumnChart = null;
        var cameraAlgorithmChart = null;

        $(window).on('resize', function() {
            dW = $(document).width();
            dH = $(document).height();
            qH = $('#contentQuery').height();
            window.setHeight();
        });

        window.setHeight = function() {
            if(dW < 768) {
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

        window.buildAlgorithmRatioChart = function() {
            var option = {
                title: {
                    text: '算法告警占比',
                    left: 'center'
                },
                tooltip: {
                    trigger: 'item'
                },
                legend: {
                    orient: 'vertical',
                    left: 'left'
                },
                series: [
                    {
                        name: '算法告警占比',
                        type: 'pie',
                        radius: '50%',
                        data: [
                            // { value: 1048, name: 'Search Engine' },
                            // { value: 735, name: 'Direct' },
                            // { value: 580, name: 'Email' },
                            // { value: 484, name: 'Union Ads' },
                            // { value: 300, name: 'Video Ads' }
                        ],
                        emphasis: {
                            itemStyle: {
                                shadowBlur: 10,
                                shadowOffsetX: 0,
                                shadowColor: 'rgba(0, 0, 0, 0.5)'
                            }
                        }
                    }
                ]
            };

            algorithmRatioChart = echarts.init(document.getElementById('algorithmRatio'), null, {
                width: qW,
                height: qH
            });
            algorithmRatioChart.setOption(option);
        }

        window.updateAlgorithmRatioChart = function(data) {
            $.post('/statistic/algorithm/ratio', data, function(res) {
                algorithmRatioChart.setOption({
                    series: [
                        {
                            data: res.data
                        }
                    ]
                });
            });
        }

        window.buildCameraColumnChart = function() {
            var option = {
                title: {
                    text: '摄像头告警统计'
                },
                xAxis: {
                    type: 'category',
                    data: []
                },
                yAxis: {
                    type: 'value'
                },
                series: [
                    {
                        data: [],
                        type: 'bar',
                        showBackground: true,
                        backgroundStyle: {
                            color: 'rgba(180, 180, 180, 0.2)'
                        }
                    }
                ]
            };
            cameraColumnChart = echarts.init(document.getElementById('cameraColumnChart'), null, {
                width: qW,
                height: qH
            });
            cameraColumnChart.setOption(option);
        }

        window.updateCameraColumnChart = function(data) {
            $.post('/statistic/camera', data, function(res) {
                cameraColumnChart.setOption({
                    xAxis: {
                        data: res.data.xAxiss,
                    },
                    series: [
                        {
                            data: res.data.values
                        }
                    ]
                });
            });
        }

        window.buildCameraAlgorithmChart = function() {
            var app = {};
            var posList = [
                'left',
                'right',
                'top',
                'bottom',
                'inside',
                'insideTop',
                'insideLeft',
                'insideRight',
                'insideBottom',
                'insideTopLeft',
                'insideTopRight',
                'insideBottomLeft',
                'insideBottomRight'
            ];
            app.configParameters = {
                rotate: {
                    min: -90,
                    max: 90
                },
                align: {
                    options: {
                        left: 'left',
                        center: 'center',
                        right: 'right'
                    }
                },
                verticalAlign: {
                    options: {
                        top: 'top',
                        middle: 'middle',
                        bottom: 'bottom'
                    }
                },
                position: {
                    options: posList.reduce(function (map, pos) {
                        map[pos] = pos;
                        return map;
                    }, {})
                },
                distance: {
                    min: 0,
                    max: 100
                }
            };
            app.config = {
                rotate: 90,
                align: 'left',
                verticalAlign: 'middle',
                position: 'insideBottom',
                distance: 15
            };
            var labelOption = {
                show: true,
                position: app.config.position,
                distance: app.config.distance,
                align: app.config.align,
                verticalAlign: app.config.verticalAlign,
                rotate: app.config.rotate,
                formatter: '{c}  {name|{a}}',
                fontSize: 16,
                rich: {
                    // name: {}
                }
            };
            var option = {
                title: {
                    text: '摄像头算法告警统计'
                },
                tooltip: {
                    trigger: 'axis',
                    axisPointer: {
                        // type: 'shadow'
                    }
                },
                legend: {
                    data: [],
                    align: 'right',
                    right: 20,
                    left: 180
                },
                toolbox: {
                    show: true,
                    orient: 'vertical',
                    left: 'right',
                    top: 'center'
                },
                xAxis: [
                    {
                        type: 'category',
                        axisTick: { show: false },
                        data: []
                    }
                ],
                yAxis: [
                    {
                        type: 'value'
                    }
                ],
                series: []
            };

            cameraAlgorithmChart = echarts.init(document.getElementById('cameraAlgorithmChart'), null, {
                width: qW,
                height: qH
            });
            cameraAlgorithmChart.setOption(option);
        }

        window.updateCameraAlgorithmChart = function(data) {
            $.post('/statistic/camera2algorithm', data, function(res) {
                cameraAlgorithmChart.setOption({
                    legend: {
                        data: res.data.algorithmNames
                    },
                    xAxis: [
                        {
                            data: res.data.cameraNames
                        }
                    ],
                    series: res.data.datas
                });
            });
        }

        $(document).ready(function() {
            dW = $(document).width();
            dH = $(document).height();
            qH = $('#contentQuery').height();
            qW = dW;

            window.setHeight();
            window.buildAlgorithmRatioChart();
            window.buildCameraColumnChart();
            window.buildCameraAlgorithmChart();

            setTimeout(function() {
                window.updateAlgorithmRatioChart({'startDate': '${startDate!''}', 'endDate': '${endDate!''}'});
            }, 300);

            setTimeout(function() {
                window.updateCameraColumnChart({'startDate': '${startDate!''}', 'endDate': '${endDate!''}'});
            }, 500);

            setTimeout(function() {
                window.updateCameraAlgorithmChart({'startDate': '${startDate!''}', 'endDate': '${endDate!''}'});
            }, 700);
        });
    })
</script>
</html>
