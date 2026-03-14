<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>网络管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>
        .pane-title { font-weight: 600; margin-bottom: 10px; }
    </style>
</head>
<body class="pear-container">
<div class="layui-row layui-col-space12">
    <div class="layui-col-md5">
        <div class="layui-card">
            <div class="layui-card-body">
                <div class="pane-title">检测到的网卡</div>
                <table id="ifTable" lay-filter="ifTable"></table>
            </div>
        </div>
    </div>
    <div class="layui-col-md7">
        <div class="layui-card">
            <div class="layui-card-body">
                <div class="pane-title">已保存配置</div>
                <form class="layui-form" id="networkForm">
                    <div class="layui-form-item">
                        <label class="layui-form-label">网卡名</label>
                        <div class="layui-input-block">
                            <input type="text" name="interface_name" id="interface_name" required lay-verify="required" class="layui-input" placeholder="eth0">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">IPv4地址</label>
                        <div class="layui-input-block">
                            <input type="text" name="ip" id="ip" class="layui-input" placeholder="192.168.1.10">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">网关</label>
                        <div class="layui-input-block">
                            <input type="text" name="gateway" id="gateway" class="layui-input" placeholder="192.168.1.1">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">DNS</label>
                        <div class="layui-input-block">
                            <input type="text" name="dns" id="dns" class="layui-input" placeholder="8.8.8.8,1.1.1.1">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <div class="layui-input-block">
                            <button type="submit" class="pear-btn pear-btn-primary" lay-submit lay-filter="saveNetwork">保存</button>
                            <button type="button" class="pear-btn" id="btnReload">刷新</button>
                        </div>
                    </div>
                </form>
                <table id="savedTable" lay-filter="savedTable"></table>
            </div>
        </div>
    </div>
</div>

<script type="text/html" id="savedActions">
    <a href="#" style="color: #409EFF;" lay-event="edit">编辑</a>
    <a href="#" style="color: #DD4A68; margin-left: 10px;" lay-event="delete">删除</a>
</script>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['table', 'form', 'jquery', 'popup'], function() {
        let table = layui.table;
        let form = layui.form;
        let $ = layui.jquery;
        let popup = layui.popup;

        function loadInterfaces() {
            $.post('/config/network/interfaces', function(res) {
                if (res.code !== 0) {
                    popup.failure(res.msg || '加载网卡信息失败');
                    return;
                }
                table.reload('ifTable', {data: res.data || []});
            });
        }

        function loadSaved() {
            $.post('/config/network/saved', function(res) {
                if (res.code !== 0) {
                    popup.failure(res.msg || '加载已保存配置失败');
                    return;
                }
                table.reload('savedTable', {data: res.data || []});
            });
        }

        table.render({
            elem: '#ifTable',
            id: 'ifTable',
            data: [],
            page: false,
            cols: [[
                {title: '网卡名', field: 'name', width: 120},
                {title: 'MAC', field: 'mac'},
                {title: 'IPv4地址', field: 'ipv4', width: 140}
            ]]
        });

        table.render({
            elem: '#savedTable',
            id: 'savedTable',
            data: [],
            page: false,
            cols: [[
                {title: '网卡名', field: 'interface_name', width: 120},
                {title: 'IP地址', field: 'ip', width: 140},
                {title: '网关', field: 'gateway', width: 140},
                {title: 'DNS', field: 'dns'},
                {title: '操作', toolbar: '#savedActions', width: 90}
            ]]
        });

        form.on('submit(saveNetwork)', function(data) {
            let loading = layer.load(2, { shade: [0.15,'#000'] });
            $.post('/config/network/save', data.field, function(res) {
                layer.close(loading);
                if (res.code === 0) {
                    layer.msg('保存成功', {icon: 1, time: 800}, function() {
                        loadSaved();
                    });
                } else {
                    popup.failure(res.msg || '保存失败');
                }
            });
            return false;
        });

        table.on('tool(savedTable)', function(obj) {
            if (obj.event === 'edit') {
                $('#interface_name').val(obj.data.interface_name || '');
                $('#ip').val(obj.data.ip || '');
                $('#gateway').val(obj.data.gateway || '');
                $('#dns').val(obj.data.dns || '');
            } else if (obj.event === 'delete') {
                layer.confirm('确定删除该网络配置吗？', {icon: 3, title: '提示'}, function(index) {
                    layer.close(index);
                    $.post('/config/network/delete', {interface_name: obj.data.interface_name}, function(res) {
                        if (res.code === 0) {
                            layer.msg('删除成功', {icon: 1, time: 700}, function() {
                                loadSaved();
                            });
                        } else {
                            popup.failure(res.msg || '删除失败');
                        }
                    });
                });
            }
        });

        table.on('row(ifTable)', function(obj) {
            let row = obj.data || {};
            $('#interface_name').val(row.name || '');
            $('#ip').val(row.ipv4 || '');
            form.render();
        });

        $('#btnReload').on('click', function() {
            loadInterfaces();
            loadSaved();
        });

        loadInterfaces();
        loadSaved();
    });
</script>
</body>
</html>
