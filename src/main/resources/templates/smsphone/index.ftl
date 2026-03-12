<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>短信手机号码管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
</head>
<body class="pear-container">
<div class="layui-row layui-col-space10">
    <div class="layui-col-md12">
        <div class="layui-card">
            <div class="layui-card-body">
                <table id="table" lay-filter="table"></table>
            </div>
        </div>
    </div>
</div>
</body>
<script type="text/html" id="table-toolbar">
    <button class="pear-btn pear-btn-primary pear-btn-md" lay-event="add">
        <i class="layui-icon layui-icon-add-1"></i>
        新增号码
    </button>
</script>
<script type="text/html" id="table-actions">
    <a href="#" style="color: #DD4A68;" lay-event="remove">删除</a>
</script>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['table', 'form', 'jquery', 'util', 'popup'], function() {
        let table = layui.table;
        let $ = layui.jquery;
        let popup = layui.popup;
        let permissions = {can_write_system: false};

        //
        let cols = [{
            title: '手机号码',
            field: 'phone'
        }, {
            title: '操作',
            toolbar: '#table-actions',
            align: 'left',
            width: 120,
            fixed: 'right'
        }];

        //
        table.render({
            elem: '#table',
            url: '/smsphone/listData',
            method: 'post',
            page: false,
            cols: [cols],
            skin: 'line',
            height: 'full-148',
            toolbar: '#table-toolbar',
            defaultToolbar: [{
                title: '刷新',
                layEvent: 'refresh',
                icon: 'layui-icon-refresh'
            }]
        });

        //
        table.on('tool(table)', function(obj) {
            if(obj.event == 'remove') {
                if(!guardWriteAction()) {
                    return;
                }
                window.removeData(obj);
            } else if(obj.event == 'edit') {
                //window.editForm(obj);
            }
        });

        //
        table.on('toolbar(table)', function(obj) {
            if(obj.event == 'add') {
                if(!guardWriteAction()) {
                    return;
                }
                window.addForm();
            } else if(obj.event == 'refresh') {
                window.refreshTable();
            }
        });

        //
        window.addForm = function() {
            layer.open({
                type: 2,
                title: '新增手机号码',
                shade: 0.1,
                area: ['60%', '60%'],
                content: '/smsphone/form'
            });
        }

        //
        window.refreshTable = function() {
            table.reload('table');
        }

        //
        window.removeData = function (obj) {
            if(!guardWriteAction()) {
                return;
            }
            layer.confirm('确定删除该手机号码吗?', {
                icon: 3,
                title: '提示'
            }, function (index) {
                layer.close(index);
                var loading = layer.load(2);
                $.post('/smsphone/delete', {'id': obj.data['id']}, function(res) {
                    layer.close(loading);
                    if(res.code == 0) {
                        layer.msg('操作成功', {icon:1, time:1000}, function() {
                            window.refreshTable();
                        });
                    } else {
                        popup.failure(res.msg);
                    }
                });
            });
        }

        function guardWriteAction() {
            if(permissions.can_write_system) {
                return true;
            }
            popup.failure('permission denied');
            return false;
        }

        function applyPermissionUi() {
            if(permissions.can_write_system) {
                return;
            }
            $('button[lay-event=add]').prop('disabled', true).addClass('layui-btn-disabled');
        }

        $.post('/account/permissions', {}, function(res) {
            if(res && res.code == 0 && res.data) {
                permissions = res.data;
            }
            applyPermissionUi();
        });
    })
</script>
</html>
