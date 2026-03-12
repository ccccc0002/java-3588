<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>Network</title>
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
                <div class="pane-title">Detected Interfaces</div>
                <table id="ifTable" lay-filter="ifTable"></table>
            </div>
        </div>
    </div>
    <div class="layui-col-md7">
        <div class="layui-card">
            <div class="layui-card-body">
                <div class="pane-title">Saved Configuration</div>
                <form class="layui-form" id="networkForm">
                    <div class="layui-form-item">
                        <label class="layui-form-label">Interface</label>
                        <div class="layui-input-block">
                            <input type="text" name="interface_name" id="interface_name" required lay-verify="required" class="layui-input" placeholder="eth0">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">IPv4</label>
                        <div class="layui-input-block">
                            <input type="text" name="ip" id="ip" class="layui-input" placeholder="192.168.1.10">
                        </div>
                    </div>
                    <div class="layui-form-item">
                        <label class="layui-form-label">Gateway</label>
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
                            <button type="submit" class="pear-btn pear-btn-primary" lay-submit lay-filter="saveNetwork">Save</button>
                            <button type="button" class="pear-btn" id="btnReload">Reload</button>
                        </div>
                    </div>
                </form>
                <table id="savedTable" lay-filter="savedTable"></table>
            </div>
        </div>
    </div>
</div>

<script type="text/html" id="savedActions">
    <a href="#" style="color: #409EFF;" lay-event="edit">Edit</a>
    <a href="#" style="color: #DD4A68; margin-left: 10px;" lay-event="delete">Delete</a>
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
                    popup.failure(res.msg || 'Load interfaces failed');
                    return;
                }
                table.reload('ifTable', {data: res.data || []});
            });
        }

        function loadSaved() {
            $.post('/config/network/saved', function(res) {
                if (res.code !== 0) {
                    popup.failure(res.msg || 'Load saved config failed');
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
                {title: 'Name', field: 'name', width: 120},
                {title: 'MAC', field: 'mac'},
                {title: 'IPv4', field: 'ipv4', width: 140}
            ]]
        });

        table.render({
            elem: '#savedTable',
            id: 'savedTable',
            data: [],
            page: false,
            cols: [[
                {title: 'Interface', field: 'interface_name', width: 120},
                {title: 'IP', field: 'ip', width: 140},
                {title: 'Gateway', field: 'gateway', width: 140},
                {title: 'DNS', field: 'dns'},
                {title: 'Action', toolbar: '#savedActions', width: 90}
            ]]
        });

        form.on('submit(saveNetwork)', function(data) {
            let loading = layer.load(2, { shade: [0.15,'#000'] });
            $.post('/config/network/save', data.field, function(res) {
                layer.close(loading);
                if (res.code === 0) {
                    layer.msg('Saved', {icon: 1, time: 800}, function() {
                        loadSaved();
                    });
                } else {
                    popup.failure(res.msg || 'Save failed');
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
                layer.confirm('Delete selected network config?', {icon: 3, title: 'Warning'}, function(index) {
                    layer.close(index);
                    $.post('/config/network/delete', {interface_name: obj.data.interface_name}, function(res) {
                        if (res.code === 0) {
                            layer.msg('Deleted', {icon: 1, time: 700}, function() {
                                loadSaved();
                            });
                        } else {
                            popup.failure(res.msg || 'Delete failed');
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
