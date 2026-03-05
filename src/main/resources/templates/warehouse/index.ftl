<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>基地管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <link href="/static/js/jstree/themes/default/style.css" rel="stylesheet" />
    <style>
        .organizationTree {
            width: 100% !important;
            height: -webkit-calc(100vh - 130px);
            height: -moz-calc(100vh - 130px);
            height: calc(100vh - 130px);
        }
        .layui-table-tool {padding-top: 0;}
        /*定义滚动条高宽及背景 高宽分别对应横竖滚动条的尺寸*/
        ::-webkit-scrollbar {
            width: 7px;
            height: 7px;
            background-color: #f5f5f5;
        }
        /*定义滚动条轨道 内阴影+圆角*/
        ::-webkit-scrollbar-track {
            box-shadow: inset 0 0 6px rgba(0, 0, 0, 0.3);
            -webkit-box-shadow: inset 0 0 6px rgba(0, 0, 0, 0.3);
            border-radius: 10px;
            background-color: #f5f5f5;
        }
        /*定义滑块 内阴影+圆角*/
        ::-webkit-scrollbar-thumb {
            border-radius: 10px;
            box-shadow: inset 0 0 6px rgba(0, 0, 0, 0.1);
            -webkit-box-shadow: inset 0 0 6px rgba(0, 0, 0, 0.1);
            background-color: #c8c8c8;
        }
    </style>
    <script type="text/html" id="pullStatusTpl">
        {{#  if(d.pullStatus=='0'){ }}
        <span style="color: #67c23a">拉取正常</span>
        {{#  } else { }}
        <span style="color: #f56c6c">拉取异常</span>
        {{# } }}
    </script>
    <script type="text/html" id="treeTypeTpl">
        {{#  if(d.treeType=='0'){ }}
        <span>区域</span>
        {{#  } else { }}
        <span>监控点</span>
        {{# } }}
    </script>
    <script type="text/html" id="organization-toolbar">
        <button class="pear-btn pear-btn-primary pear-btn-md" lay-event="sync2node">
            <i class="layui-icon layui-icon-add-1"></i>
            同步当前子节点
        </button>

        <button class="pear-btn pear-btn-warming pear-btn-md" lay-event="sync2all">
            <i class="layui-icon layui-icon-add-1"></i>
            同步所有节点
        </button>

        <button class="pear-btn pear-btn-success pear-btn-md" lay-event="select2export">
            <i class="layui-icon layui-icon-add-1"></i>
            导入到摄像头管理
        </button>

        <button class="pear-btn pear-btn-default pear-btn-md" lay-event="pull">
            <i class="layui-icon layui-icon-add-1"></i>
            拉取视频地址
        </button>
    </script>

    <script type="text/html" id="organization-bar">
        <a href="#" style="color: #409EFF;" lay-event="edit">编辑</a>
        <a href="#" style="color: #DD4A68; margin-left: 15px;" lay-event="remove">删除</a>
    </script>
</head>
<body class="pear-container">
<div class="layui-row layui-col-space15">
    <div class="layui-col-md4">
        <div class="layui-card">
            <div class="layui-card-body">
                <#if count?? && count == 0 >
                    <div id="empty" style="margin: 50px auto; text-align: center; color: #999999; display: flex; flex-direction: column; align-items: center; justify-content: center;">
                        <i class="pear-icon pear-icon-prompt" style="font-size: 20px;"></i>
                        <span style="margin-top: 5px;">还没有基地节点</span>
                    </div>
                </#if>
                <div class="treeDiv" style="overflow: auto; height: calc(100vh - 40px);">
                    <div id="treeData" class="demo"></div>
                </div>
            </div>
        </div>
    </div>
    <div class="layui-col-md8">
        <div class="layui-card">
            <div class="layui-card-body">
                <div id="current-node-text" style="padding: 0px 15px;">当前选中节点： </div>
                <table id="organization-table" lay-filter="organization-table"></table>
            </div>
        </div>
    </div>
</div>
</body>
<script type="text/html" id="table-actions">
    <a href="#" style="color: #409EFF;" lay-event="logs">日志</a>
</script>
<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script src="/static/js/jquery.3.6.1.min.js"></script>
<script src="/static/js/jstree/jstree.min.js"></script>
<script>
    layui.use(['table', 'form', 'jquery', 'util', 'popup'], function() {
        let table = layui.table;
        let $ = layui.jquery;
        let popup = layui.popup;
        let form = layui.form;

        //
        var currentIndexCode = '-1';

        //
        let cols = [
            [
                {
                    type: 'checkbox'
                },
                {
                    title: '节点名称',
                    field: 'name',
                },
                {
                    title: '节点类型',
                    field: 'treeType',
                    templet: '#treeTypeTpl'
                },
                {
                    title: '视频地址',
                    field: 'rtspUrl',
                },
                {
                    title: '拉取状态',
                    field: 'pullStatus',
                    templet: '#pullStatusTpl'
                },
                {
                    title: '最后拉取时间',
                    field: 'pullTime',
                    templet: "<span>{{layui.util.toDateString(d.pullTime, 'yyyy-MM-dd HH:mm:ss')}}</span>"
                },
                {
                    title: '操作',
                    toolbar: '#table-actions',
                    align: 'left',
                    width: 80,
                    fixed: 'right'
                }
            ]
        ]

        //
        table.render({
            elem: '#organization-table',
            url: '/warehouse/listPage',
            method: 'post',
            where: {
                'parentIndexCode': currentIndexCode
            },
            height: 'full-150',
            page: true,
            cols: cols,
            skin: 'line',
            toolbar: '#organization-toolbar',
            defaultToolbar: [{
                title: '刷新',
                layEvent: 'refresh',
                icon: 'layui-icon-refresh',
            }]
        });

        //
        table.on('tool(organization-table)', function(obj) {
            if(obj.event == 'logs') {
                parent.layui.admin.addTab(obj.data.id, obj.data.name + ' 调用日志', '/cameralog?indexCode=' + obj.data.indexCode);
            }
        });

        //
        table.on('toolbar(organization-table)', function(obj) {
            if (obj.event === 'sync2node') {
                window.sync2node(obj);
            } else if (obj.event === 'refresh') {
                window.refresh();
            } else if (obj.event === 'sync2all') {
                window.sync2all(obj);
            } else if (obj.event === 'pull') {
                window.pullRtsp(obj);
            } else if (obj.event == 'select2export') {
                window.select2export(obj);
            }
        });

        //
        window.sync2all = function() {
            layer.confirm('同步所有节点时间比较长，确认执行吗?', {
                icon: 3,
                title: '提示'
            }, function(index) {
                layer.close(index);
                let loading = layer.load();
                $.post('/warehouse/sync2all', {}, function(res) {
                    layer.close(loading);
                    if(res.code == 0) {
                        popup.success('数据正在处理');
                    } else {
                        popup.failure(res.msg);
                    }
                });
            });
        }

        //
        window.sync2node = function(obj) {
            layer.confirm('确认同步当前节点数据吗?', {
                icon: 3,
                title: '提示'
            }, function(index) {
                layer.close(index);
                let loading = layer.load();
                $.post('/warehouse/sync2node', {'indexCode': currentIndexCode}, function(res) {
                    layer.close(loading);
                    if(res.code == 0) {
                        popup.success('数据正在处理');
                    } else {
                        popup.failure(res.msg);
                    }
                });
            });
        }

        //
        window.pullRtsp = function() {
            let loading = layer.load();
            $.post('/warehouse/pullRtsp', {'indexCode': currentIndexCode}, function(res) {
                layer.close(loading);
               if(res.code == 0) {
                   table.reload('organization-table', {
                       where: {
                           'parentIndexCode': currentIndexCode
                       }
                   });
                   popup.success('操作成功，请稍后刷新');
               } else {
                   popup.failure(res.msg);
               }
            });
        }

        //
        window.select2export = function(obj) {
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
            layer.confirm('确定要导入到摄像头管理吗?', {
                icon: 3,
                title: '提示'
            }, function (index) {
                layer.close(index);
                let loading = layer.load();
                $.post('/warehouse/select2export', {'ids': ids}, function(res) {
                    layer.close(loading);
                    if(res.code == 0) {
                        popup.success('导入完成');
                    } else {
                        popup.failure(res.msg);
                    }
                });
            });
        }

        //
        window.refresh = function(param) {
            table.reload('organization-table');
        }

        //
        window.refreshData = function() {
            table.reload('organization-table', {
                where: {
                    'parentId': currentIndexCode
                }
            });
            DTree.reload();
        }

        //
        $(document).ready(function() {
            $('#treeData').on("changed.jstree", function (e, data) {
                    if(data.selected.length) {
                        var jsonData = data.instance.get_node(data.selected[0]).original;
                        if(currentIndexCode != jsonData['meId']) {
                            currentIndexCode = jsonData['meId'];
                            $('#current-node-text').text('当前选中节点: ' + jsonData['text']);
                            table.reload('organization-table', {
                                where: {
                                    'parentIndexCode': currentIndexCode
                                }
                            });
                        }
                    }
                })
                .jstree({
                    'core' : {
                        'data' : {
                            "url" : "/warehouse/listTree",
                            "type": "post",
                            "dataType" : "json"
                        },
                        'themes': {
                            'dots': false
                        }
                    }
                });
        });
    })
</script>
</html>
