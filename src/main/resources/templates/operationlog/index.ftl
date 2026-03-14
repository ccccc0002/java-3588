<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>操作日志</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
</head>
<body class="pear-container">
<div class="layui-card">
    <div class="layui-card-body">
        <form class="layui-form" style="margin-bottom: 10px;">
            <div class="layui-row layui-col-space8">
                <div class="layui-col-md2">
                    <input type="text" name="operatorName" placeholder="操作人" class="layui-input">
                </div>
                <div class="layui-col-md2">
                    <select name="role">
                        <option value="">全部角色</option>
                        <option value="super_admin">super_admin</option>
                        <option value="ops">ops</option>
                        <option value="read_only">read_only</option>
                    </select>
                </div>
                <div class="layui-col-md2">
                    <input type="text" name="action" placeholder="操作动作" class="layui-input">
                </div>
                <div class="layui-col-md2">
                    <select name="success">
                        <option value="">全部状态</option>
                        <option value="1">成功</option>
                        <option value="0">失败</option>
                    </select>
                </div>
                <div class="layui-col-md2">
                    <input type="text" name="startText" placeholder="开始时间 yyyy-MM-dd HH:mm:ss" class="layui-input">
                </div>
                <div class="layui-col-md2">
                    <input type="text" name="endText" placeholder="结束时间 yyyy-MM-dd HH:mm:ss" class="layui-input">
                </div>
            </div>
            <div style="margin-top: 8px;">
                <button class="pear-btn pear-btn-primary pear-btn-sm" lay-submit lay-filter="query">查询</button>
            </div>
        </form>
        <table id="table" lay-filter="table"></table>
    </div>
</div>

<script type="text/html" id="successTpl">
    {{# if(d.success == 1){ }}
    <span style="color:#16b777;">成功</span>
    {{# } else { }}
    <span style="color:#ff5722;">失败</span>
    {{# } }}
</script>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['table', 'form'], function() {
        let table = layui.table;
        let form = layui.form;

        table.render({
            elem: '#table',
            id: 'table',
            url: '/operationlog/listPage',
            method: 'post',
            page: true,
            limit: 10,
            cols: [[
                {field: 'time_text', title: '时间', width: 170},
                {field: 'operator_name', title: '操作人', width: 130},
                {field: 'account', title: '账号', width: 130},
                {field: 'role', title: '角色', width: 110},
                {field: 'action', title: '动作', width: 160},
                {field: 'target', title: '目标', width: 180},
                {field: 'success', title: '状态', width: 90, templet: '#successTpl'},
                {field: 'message', title: '结果信息'},
                {field: 'detail', title: '详情'}
            ]]
        });

        form.on('submit(query)', function(data) {
            table.reload('table', {
                page: { curr: 1 },
                where: data.field
            });
            return false;
        });
    });
</script>
</body>
</html>
