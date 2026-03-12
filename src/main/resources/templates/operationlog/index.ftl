<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>Operation Log</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
</head>
<body class="pear-container">
<div class="layui-card">
    <div class="layui-card-body">
        <form class="layui-form" style="margin-bottom: 10px;">
            <div class="layui-row layui-col-space8">
                <div class="layui-col-md2">
                    <input type="text" name="operatorName" placeholder="Operator" class="layui-input">
                </div>
                <div class="layui-col-md2">
                    <select name="role">
                        <option value="">All Roles</option>
                        <option value="super_admin">super_admin</option>
                        <option value="ops">ops</option>
                        <option value="read_only">read_only</option>
                    </select>
                </div>
                <div class="layui-col-md2">
                    <input type="text" name="action" placeholder="Action" class="layui-input">
                </div>
                <div class="layui-col-md2">
                    <select name="success">
                        <option value="">All Status</option>
                        <option value="1">Success</option>
                        <option value="0">Failed</option>
                    </select>
                </div>
                <div class="layui-col-md2">
                    <input type="text" name="startText" placeholder="Start yyyy-MM-dd HH:mm:ss" class="layui-input">
                </div>
                <div class="layui-col-md2">
                    <input type="text" name="endText" placeholder="End yyyy-MM-dd HH:mm:ss" class="layui-input">
                </div>
            </div>
            <div style="margin-top: 8px;">
                <button class="pear-btn pear-btn-primary pear-btn-sm" lay-submit lay-filter="query">Query</button>
            </div>
        </form>
        <table id="table" lay-filter="table"></table>
    </div>
</div>

<script type="text/html" id="successTpl">
    {{# if(d.success == 1){ }}
    <span style="color:#16b777;">Success</span>
    {{# } else { }}
    <span style="color:#ff5722;">Failed</span>
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
                {field: 'time_text', title: 'Time', width: 170},
                {field: 'operator_name', title: 'Operator', width: 130},
                {field: 'account', title: 'Account', width: 130},
                {field: 'role', title: 'Role', width: 110},
                {field: 'action', title: 'Action', width: 160},
                {field: 'target', title: 'Target', width: 180},
                {field: 'success', title: 'Status', width: 90, templet: '#successTpl'},
                {field: 'message', title: 'Message'},
                {field: 'detail', title: 'Detail'}
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

