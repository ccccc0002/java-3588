
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>告警列表</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
</head>
<body class="pear-container">
<div class="layui-card">
    <div class="layui-card-body">
        <form class="layui-form" action="">
            <div class="layui-form-item">
                <div class="layui-form-item layui-inline">
                    <label class="layui-form-label" style="width: 50px;">摄像头</label>
                    <div class="layui-input-inline" style="width: 180px;">
                        <select class="layui-select" name="cameraId">
                            <option value="">所有</option>
                            <#if cameraList??>
                                <#list cameraList as item>
                                    <option value="${(item.id)}">${(item.name)!''}</option>
                                </#list>
                            </#if>
                        </select>
                    </div>
                </div>
                <div class="layui-form-item layui-inline">
                    <label class="layui-form-label" style="width: 50px;">算法</label>
                    <div class="layui-input-inline" style="width: 180px;">
                        <select class="layui-select" name="algorithmId">
                            <option value="">所有</option>
                            <#if algorithmList??>
                                <#list algorithmList as item>
                                    <option value="${(item.id)}">${(item.name)!''}</option>
                                </#list>
                            </#if>
                        </select>
                    </div>
                </div>
                <div class="layui-form-item layui-inline">
                    <label class="layui-form-label">告警时间</label>
                    <div class="layui-input-inline" style="display: flex; flex-direction: row; align-items: center; width: 205px;">
                        <input type="text" name="startDate" id="startDate" value="${(startDate)!''}" placeholder="" class="layui-input">
                        <span style="padding: 0px 5px;">-</span>
                        <input type="text" name="endDate" id="endDate" value="${(endDate)!''}" placeholder="" class="layui-input">
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
        <div id="currentTableId"></div>
    </div>
</div>
<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['table', 'layer', 'form', 'jquery', 'card_report', 'laydate'], function() {
        let table = layui.table;
        let form = layui.form;
        let $ = layui.jquery;
        let layer = layui.layer;
        let card_report = layui.card_report;
        let laydate = layui.laydate;
        //
        laydate.render({
            elem: '#startDate'
        });
        //
        laydate.render({
            elem: '#endDate'
        });
        //
        card_report.render({
            elem: '#currentTableId',
            url: '/report/list_card_data?startDate1=${startDate!''}&endDate1=${endDate!''}',
            data: [],
            page: true,
            limit: 8,
            linenum: 4,
            clickItem: function(data){
                //console.log(data)
            }
        })

        // 监听搜索操作
        form.on('submit(query)', function(data) {
            card_report.reload('currentTableId', {
                where: data.field
            })
            return false;
        });
    })
</script>
</body>
</html>
