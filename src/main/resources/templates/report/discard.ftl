<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>告警管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
</head>
<body class="pear-container">
<div class="layui-row layui-col-space10">
    <div class="layui-col-md12">
        <div class="layui-card">
            <div class="layui-card-body">
                <form class="layui-form" action="">
                    <div class="layui-form-item">
                        <div class="layui-form-item layui-inline">
                            <label class="layui-form-label">摄像头</label>
                            <div class="layui-input-inline">
                                <select class="layui-input" name="cameraId">
                                    <option value="">-All-</option>
                                    <#list cameraList as item>
                                        <option value="${(item.id)!''}">${(item.name!'')}</option>
                                    </#list>
                                </select>
                            </div>
                        </div>
                        <div class="layui-form-item layui-inline">
                            <label class="layui-form-label">算法</label>
                            <div class="layui-input-inline">
                                <select class="layui-input" name="algorithmId">
                                    <option value="">-All-</option>
                                    <#list algorithmList as item>
                                        <option value="${(item.id)!''}">${(item.name!'')}</option>
                                    </#list>
                                </select>
                            </div>
                        </div>
                        <div class="layui-form-item layui-inline">
                            <label class="layui-form-label">告警类型</label>
                            <div class="layui-input-inline">
                                <select class="layui-input" name="type">
                                    <option value="">-All-</option>
                                    <#list typeList as item>
                                        <option value="${(item.id)!''}">${(item.name!'')}</option>
                                    </#list>
                                </select>
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
            <div class="layui-card-body">
                <table id="table" lay-filter="table"></table>
            </div>
        </div>
    </div>
</div>
</body>
<script type="text/html" id="table-toolbar">
    <button class="pear-btn pear-btn-danger pear-btn-md" lay-event="batchRemove">
        <i class="layui-icon layui-icon-delete"></i>
        删除
    </button>
</script>
<script type="text/html" id="table-actions">
    <a href="/report/detail?id={{ d.id }}" target="_blank" style="color: #409EFF;" lay-event="detail">详情</a>
</script>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script>
    layui.use(['table', 'form', 'jquery', 'util', 'popup'], function() {
        let table = layui.table;
        let $ = layui.jquery;
        let popup = layui.popup;
        let form = layui.form;

        //
        form.on('submit(query)', function(data) {
            table.reload('table', {
                where: data.field
            })
            return false;
        });

        //
        let cols = [{
            type: 'checkbox'
        },{
            title: '摄像头名称',
            field: 'cameraName'
        }, {
            title: '算法名称',
            field: 'algorithmName'
        }, {
            title: '告警类型',
            field: 'typeName'
        }, {
            title: '创建时间',
            field: 'createdStr'
        }, {
            title: '操作',
            toolbar: '#table-actions',
            align: 'left',
            width: 180,
            fixed: 'right'
        }];

        //
        table.render({
            elem: '#table',
            url: '/report/listPage',
            method: 'post',
            page: true,
            cols: [cols],
            skin: 'line',
            height: 'full-148',
            toolbar: '#table-toolbar',
            defaultToolbar: [{
                title: 'Refresh',
                layEvent: 'refresh',
                icon: 'layui-icon-refresh'
            }]
        });

        //
        table.on('tool(table)', function(obj) {
            if(obj.event == 'detail') {
                parent.layui.admin.addTab(obj.data.id, obj.data.cameraName + '告警', '/report/detail?id=' + obj.data.id);
            }
        });

        //
        table.on('toolbar(table)', function(obj) {
            console.log(obj)
            if(obj.event == 'add') {
                window.addForm();
            } else if(obj.event == 'refresh') {
                window.refreshTable();
            } else if(obj.event == 'batchRemove') {
                window.batchRemove(obj);
            }
        });

        //
        window.batchRemove = function(obj) {
            let data = table.checkStatus(obj.config.id).data;
            if (data.length === 0) {
                layer.msg("未选中数据", {
                    icon: 3,
                    time: 1000
                });
                return false;
            }
            let ids = "";
            for (let i = 0; i < data.length; i++) {
                ids += data[i].id + ",";
            }
            ids = ids.substr(0, ids.length - 1);
            layer.confirm('确定要删除这些数据', {
                icon: 3,
                title: '提示'
            }, function (index) {
                layer.close(index);
                let loading = layer.load();

                $.post('/report/batchRemove', {'ids': ids}, function(res) {
                    layer.close(loading);
                    if(res.code == 0) {
                        table.reload('table', {
                            where: data.field
                        })
                        popup.success('操作成功');
                    } else {
                        popup.failure(res.msg);
                    }
                });
            });
        }

        //
        window.addForm = function() {

        }

        //
        window.refreshTable = function() {
            table.reload('table');
        }
    })
</script>
</html>
