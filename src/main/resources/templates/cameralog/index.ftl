<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>调用日志管理</title>
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
    <div>RTSP调用日志</div>
</script>
<script type="text/html" id="table-actions">
    <a href="#" style="color: #409EFF;" lay-event="edit">编辑</a>
</script>
<script type="text/html" id="typeTpl">
    {{#  if(d.type=='0'){ }}
    <span>实时流</span>
    {{#  } else { }}
    <span>备份流</span>
    {{# } }}
</script>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['table', 'form', 'jquery', 'util', 'popup'], function() {
        let table = layui.table;
        let $ = layui.jquery;
        let popup = layui.popup;

        //
        let cols = [{
            title: '摄像头名称',
            field: 'name'
        }, {
            title: '流类型',
            field: 'type',
            templet: '#typeTpl'
        }, {
            title: '状态码',
            field: 'code'
        }, {
            title: '请求参数',
            field: 'params'
        }, {
            title: '结果参数',
            field: 'result'
        }, {
            title: 'RTSP',
            field: 'url'
        }, {
            title: '时间',
            field: 'createdAt',
            templet: '<span>{{ layui.util.toDateString(d.createdAt, "yyyy-MM-dd HH:mm:ss") }}</span>'
        }];

        //
        table.render({
            elem: '#table',
            url: '/cameralog/listPage?indexCode=${(indexCode)!''}',
            method: 'post',
            page: true,
            cols: [cols],
            skin: 'line',
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
                window.removeData(obj);
            } else if(obj.event == 'edit') {
                window.editForm(obj);
            }
        });

        //
        table.on('toolbar(table)', function(obj) {
            if(obj.event == 'add') {
                window.addForm();
            } else if(obj.event == 'refresh') {
                window.refreshTable();
            }
        });

        //
        window.addForm = function() {
            layer.open({
                type: 2,
                title: '新增算法',
                shade: 0.1,
                area: ['60%', '80%'],
                content: '/algorithm/form'
            });
        }

        //
        window.editForm = function(obj) {
            layer.open({
                type: 2,
                title: '编辑算法',
                shade: 0.1,
                area: ['60%', '80%'],
                content: '/algorithm/form?id=' + obj.data.id
            });
        }

        //
        window.refreshTable = function() {
            table.reload('table');
        }

        //
        window.removeData = function (obj) {
            layer.confirm('确定删除该算法吗?', {
                icon: 3,
                title: '提示'
            }, function (index) {
                layer.close(index);
                var loading = layer.load(2);
                $.post('/algorithm/delete', {'id': obj.data['id']}, function(res) {
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
    })
</script>
</html>
