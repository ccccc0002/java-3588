<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>增量训练管理</title>
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
                            <label class="layui-form-label">审核状态</label>
                            <div class="layui-input-inline">
                                <select class="layui-input" name="auditState">
                                    <option value="">全部</option>
                                    <option value="0">待审核</option>
                                    <option value="1">已审核</option>
                                </select>
                            </div>
                        </div>
                        <div class="layui-form-item layui-inline">
                            <label class="layui-form-label">审核结果</label>
                            <div class="layui-input-inline">
                                <select class="layui-input" name="auditResult">
                                    <option value="">全部</option>
                                    <option value="1">正确</option>
                                    <option value="2">错误</option>
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
    <button class="pear-btn pear-btn-md pear-btn-primary" lay-event="export">
        <i class="layui-icon layui-icon-export"></i>
        导出数据
    </button>
</script>
<script type="text/html" id="table-actions">
    <a href="#" style="color: #409EFF;" lay-event="detail">详情</a>
</script>
<script type="text/html" id="stateTpl">
    {{#  if(d.state=='0'){ }}
    <span style="color: #F56C6C">Closed</span>
    {{#  } else { }}
    <span style="color: #67C23A;">Running</span>
    {{# } }}
</script>
<script type="text/html" id="auditStateTpl">
    {{#  if(d.auditState=='0'){ }}
    <span>待审核</span>
    {{# } else if(d.auditState=='1'){ }}
    <span>已审核</span>
    {{#  } else { }}
    <span>错误</span>
    {{# } }}
</script>
<script type="text/html" id="auditResultTpl">
    {{#  if(d.auditResult=='0'){ }}
    <span></span>
    {{# } else if(d.auditResult=='1'){ }}
    <span>正确</span>
    {{#  } else { }}
    <span>错误</span>
    {{# } }}
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
            title: '摄像头名称',
            field: 'cameraName'
        }, {
            title: '算法名称',
            field: 'algorithmName'
        }, {
            title: '置信度',
            field: 'conf'
        }, {
            title: '告警类型',
            field: 'typeName'
        }, {
            title: '创建时间',
            field: 'createdStr'
        }, {
            title: '审核状态',
            field: 'auditState',
            templet: '#auditStateTpl'
        }, {
            title: '审核结果',
            field: 'auditResult',
            templet: '#auditResultTpl'
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
            url: '/report/auditListPage',
            method: 'post',
            page: true,
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
                window.removeData(obj);
            } else if(obj.event == 'edit') {
                window.editForm(obj);
            } else if(obj.event == 'detail') {
                layer.open({
                    type: 2,
                    title: '告警详情',
                    shade: 0.1,
                    area: ['90%', '90%'],
                    content: '/report/auditDetail?id=' + obj.data.id
                });
            }
        });

        //
        table.on('toolbar(table)', function(obj) {
            if(obj.event == 'export') {
                window.exportForm(obj);
            } else if(obj.event == 'refresh') {
                window.refreshTable();
            }
        });

        //
        window.exportForm = function() {
            layer.open({
                type: 2,
                title: '导出数据',
                shade: 0.1,
                area: ['60%', '70%'],
                content: '/report/audit/export'
            });
        }

        //
        window.refreshTable = function() {
            table.reload('table');
        }
    })
</script>
</html>

