<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>摄像头管理</title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <link href="/static/js/jstree/themes/default/style.css" rel="stylesheet" />
    <style>
        .node-empty-box { margin: 50px auto; text-align: center; color: #999999; display: flex; flex-direction: column; align-items: center; justify-content: center; }
        .node-info { margin-bottom: 10px; display: flex; flex-direction: row; justify-content: center; align-items: center; }
        .node-info span { padding: 0px 6px;  }
        .vakata-context, .vakata-context ul { background-color: #fff; border: #ddd; box-shadow: none; border: 1px solid #eee; max-height: 300px; overflow-y: auto; background-color: #fff; border-radius: 2px; box-shadow: 1px 1px 4px rgb(0 0 0 / 8%); box-sizing: border-box; }
        .vakata-context li > a { font-family: Helvetica Neue,Helvetica,PingFang SC,Tahoma,Arial,sans-serif; color: #5f5f5f; }
        .camera-table-tit { font-size: 16px; font-weight: bold; color: #333; }
    </style>
</head>
<body class="pear-container">
<div class="layui-row layui-col-space10">
    <div class="layui-col-md3">
        <div class="layui-card">
            <div class="layui-card-body">
                <div id="tree-empty" style="display: none;">
                    <div class="node-empty-box">
                        <div class="node-info">
                            <i class="pear-icon pear-icon-prompt" style="font-size: 20px;"></i>
                            <span>暂无区域节点</span>
                        </div>
                        <a href="javascript:void(0);" onclick="handleAddLocation('0');" class="pear-btn pear-btn-primary pear-btn-sm" lay-event="add">
                            <i class="layui-icon layui-icon-add-1"></i>
                            新增区域节点
                        </a>
                    </div>
                </div>
                <div class="treeDiv" style="overflow: auto; height: calc(100vh - 20px);">
                    <div id="treeData" class="demo"></div>
                </div>
            </div>
        </div>
    </div>
    <div class="layui-col-md9">
        <div class="layui-card">
            <div class="layui-card-body">
                <form class="layui-form" action="">
                    <input type="hidden" id="locationId" name="locationId" value="">
                    <div class="layui-form-item" style="margin-bottom: 0;">
                        <div class="layui-form-item layui-inline">
                            <label class="layui-form-label">摄像头名称</label>
                            <div class="layui-input-inline">
                                <input type="text" class="layui-input" name="name" id="name"/>
                            </div>
                        </div>

                        <div class="layui-form-item layui-inline">
                            <button class="pear-btn pear-btn-md pear-btn-primary" lay-submit lay-filter="query">
                                <i class="layui-icon layui-icon-search"></i>
                                查询
                            </button>
                            <button type="button" class="pear-btn pear-btn-md" onclick="resetFormData();">
                                <i class="layui-icon layui-icon-refresh"></i>
                                重置
                            </button>
                        </div>
                    </div>
                </form>
                <table id="table" lay-filter="table"></table>
            </div>
        </div>
    </div>
</div>
</body>
<script type="text/html" id="table-toolbar">
<#--    <button class="pear-btn pear-btn-primary pear-btn-md" lay-event="add">-->
<#--        <i class="layui-icon layui-icon-add-1"></i>-->
<#--        新增摄像头-->
<#--    </button>-->
    <span class="camera-table-tit">摄像头列表</span>
</script>
<script type="text/html" id="table-actions">
    <a href="#" style="color: #409EFF;" lay-event="edit">编辑</a>
    <a href="#" style="color: #F56C6C; margin-left: 10px;" lay-event="remove">删除</a>
</script>
<script type="text/html" id="stateTpl">
    {{#  if(d.running=='0'){ }}
    <span style="color: #F56C6C" id="{{ d.id }}" state="{{ d.running }}">停止</span>
    {{#  } else { }}
    <span style="color: #67C23A;" id="{{ d.id }}" state="{{ d.running }}">启动</span>
    {{# } }}
    <a href="javascript:void(0);" style="color: #409EFF; margin-left: 6px;" onclick="window.switchRunning('{{ d.id }}')">切换</a>
</script>
<script type="text/html" id="rtspTypeTpl">
    {{#  if(d.rtspType=='0'){ }}
    <span>实时</span>
    {{# } else if(d.rtspType=='1'){ }}
    <span>备份</span>
    {{#  } else { }}
    <span>图片</span>
    {{# } }}
    <a href="javascript:void(0);" style="color: #409EFF; margin-left: 6px;" onclick1="window.switchRtspType('{{ d.id }}')" id="rtsp_type_{{ d.id }}">切换<i class="layui-icon layui-icon-triangle-d"></i></a>
</script>
<script type="text/html" id="nameTpl">
    <a href="javascript:void(0);" onclick="window.showCate('{{ d.id }}');">{{ d.name }}</a>
</script>
<script type="text/html" id="periodTpl">
    <a href="#" style="color: #409EFF;" lay-event="period">配置</a>
</script>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script src="/static/js/jquery.3.6.1.min.js"></script>
<script src="/static/js/jstree/jstree.min.js"></script>
<script>
    layui.use(['table', 'form', 'jquery', 'util', 'popup', 'layer', 'dropdown'], function() {
        let table = layui.table;
        let $ = layui.jquery;
        let popup = layui.popup;
        let form = layui.form;
        let layer = layui.layer;
        let dropdown = layui.dropdown;
        let canWriteSystem = false;

        function loadPermissionMatrix(done) {
            $.post('/account/permissions', {}, function(res) {
                if (res && res.code === 0 && res.data) {
                    canWriteSystem = !!res.data.can_write_system;
                } else {
                    canWriteSystem = false;
                }
            }).always(function() {
                if (typeof done === 'function') {
                    done();
                }
            });
        }

        function guardWriteAction() {
            if (!canWriteSystem) {
                popup.failure('无权限操作');
                return false;
            }
            return true;
        }

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
            field: 'name',
            templet: '#nameTpl'
        }, {
            title: '流类型',
            field: 'rtspType',
            templet: '#rtspTypeTpl',
            width: 120,
        }, {
            title: '告警间隔(秒)',
            field: 'intervalTime',
            width: 110,
            align: 'center'
        }, {
            title: '关联算法',
            field: 'algorithmNames',
            width: 110,
            align: 'center'
        }, {
            title: '时段配置',
            field: 'period',
            templet: '#periodTpl',
            width: 90,
            align: 'center'
        }, {
            title: '运行状态',
            field: 'running',
            templet: '#stateTpl',
            width: 100
        }, {
            title: '操作',
            toolbar: '#table-actions',
            align: 'center',
            width: 110,
            fixed: 'right'
        }];

        //
        table.render({
            elem: '#table',
            url: '/camera/listPage',
            method: 'post',
            page: true,
            cols: [cols],
            skin: 'line',
            // height: 'full-148',
            toolbar: '#table-toolbar',
            defaultToolbar: [{
                title: 'Refresh',
                layEvent: 'refresh',
                icon: 'layui-icon-refresh'
            }],
            done: function (res, curr, count) {
                for (var i = 0; i < res.data.length; i++) {
                    dropdown.render({
                        elem: '#rtsp_type_' + res.data[i].id,
                        data: [{
                            title: '实时视频地址',
                            id: 0,
                            camera_id: res.data[i].id
                        }, {
                            title: '备份视频地址',
                            id: 1,
                            camera_id: res.data[i].id
                        }, {
                            title: '图片地址',
                            id: 2,
                            camera_id: res.data[i].id
                        }],
                        id: '#rtsp_type1_' + res.data[i].id,
                        click: function (obj) {
                            window.switchRtspType(obj.camera_id, obj.id);
                            // layer.tips('点击了：' + obj.title + '' + obj.camera_id, this.elem, {
                            //     tips: [1, '#5FB878']
                            // })
                        }
                    });
                }
            }
        });

        //
        table.on('tool(table)', function(obj) {
            if(obj.event == 'remove') {
                window.removeData(obj);
            } else if(obj.event == 'edit') {
                window.editForm(obj);
            } else if(obj.event == 'period') {
                window.editPeriod(obj);
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
        window.addForm = function(locationId) {
            if (!guardWriteAction()) {
                return;
            }
            layer.open({
                type: 2,
                title: '新增摄像头',
                shade: 0.1,
                area: ['90%', '90%'],
                content: '/camera/form?locationId=' + locationId
            });
        }

        //
        window.editForm = function(obj) {
            if (!guardWriteAction()) {
                return;
            }
            layer.open({
                type: 2,
                title: '编辑摄像头',
                shade: 0.1,
                area: ['90%', '90%'],
                content: '/camera/form?id=' + obj.data.id
            });
        }

        //
        window.editPeriod = function(obj) {
            if (!guardWriteAction()) {
                return;
            }
            layer.open({
                type: 2,
                title: '时段配置',
                shade: 0.1,
                area: ['80%', '80%'],
                content: '/report/period?id=' + obj.data.id
            });
        }

        //
        window.refreshTable = function() {
            table.reload('table');
        }

        //
        window.removeData = function (obj) {
            if (!guardWriteAction()) {
                return;
            }
            layer.confirm('确定删除该摄像头吗?', {
                icon: 3,
                title: '提示'
            }, function (index) {
                layer.close(index);
                var loading = layer.load(2);
                $.post('/camera/delete', {'id': obj.data['id']}, function(res) {
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

        //
        window.reloadRunning = function() {
            // $.post('/camera/running', {}, function(res) {
            //     if(res.code == 0) {
            //         $.each(res.data, function(idx, row) {
            //             var that = $('#' + row.id);
            //             var state = that.attr('state');
            //             if(state != row.running) {
            //                 that.attr('state', row.running);
            //                 if(row.running == 0) {
            //                     that.text('Closed').css('color', '#F56C6C');
            //                 } else {
            //                     that.text('Running').css('color', '#67C23A');
            //                 }
            //             }
            //         });
            //     }
            // });
        }

        //
        window.switchRunning = function(id) {
            if (!guardWriteAction()) {
                return;
            }
            $.post('/camera/switchRunning', {'id': id}, function(res) {
                if(res.code == 0) {
                    table.reload('table');
                    popup.success('状态已切换');
                } else {
                    popup.failure(res.msg);
                }
            });
        }

        //
        window.switchRtspType = function(id, rtspType) {
            if (!guardWriteAction()) {
                return;
            }
            $.post('/camera/switchRtspType', {'id': id, 'rtspType': rtspType}, function(res) {
                if(res.code == 0) {
                    table.reload('table');
                    popup.success('状态已切换');
                } else {
                    popup.failure(res.msg);
                }
            });
        }

        //
        window.showCate = function(id) {
            $.post('/camera/cate', {'id': id}, function(res) {
                if(res.code == 0) {
                    layer.alert(res.data, {title: '摄像头路径'});
                } else {
                    layer.alert(res.msg, {title: '摄像头路径'});
                }
            });
        }

        //
        window.handleAddLocation = function(parentId) {
            if (!guardWriteAction()) {
                return;
            }
            layer.open({
                type: 2,
                title: '新增区域节点',
                shade: 0.1,
                area: ['60%', '60%'],
                content: '/location/form?parentId=' + parentId
            });
        }

        //
        window.handleEditLocation = function(locationId) {
            if (!guardWriteAction()) {
                return;
            }
            layer.open({
                type: 2,
                title: '编辑区域节点',
                shade: 0.1,
                area: ['60%', '60%'],
                content: '/location/edit?id=' + locationId
            });
        }

        //
        var treeInst = null;
        window.createTree = function() {
            $('#tree-empty').css('display', 'none');
            if(treeInst != null) {
                $('#treeData').jstree().destroy();
                treeInst = null;
            }

            treeInst = $('#treeData').on("changed.jstree", function (e, data) {
                //
                var jsonData = data.instance.get_node(data.selected[0]).original;
                $('#locationId').val(jsonData['meId']);
                $('#name').val('');
                table.reload('table', {
                    where: {
                        locationId: jsonData['meId']
                    }
                });
            }).on('loaded.jstree', function (e, obj) {
                var datas = obj.instance.get_json();
                if(datas.length == 0) {
                    $('#tree-empty').css('display', 'block');
                }
            }).on('ready.jstree', function (e, obj) {
                var datas = obj.instance.get_json();
                if(datas.length > 0) {
                    obj.instance.open_node(datas[0].id);
                }
            }).jstree({
                'core' : {
                    'data' : {
                        "url" : "/location/listTree",
                        "type": "post",
                        "dataType" : "json"
                    },
                    'themes': {
                        'dots': false
                    }
                },
                "contextmenu":{
                    "items":{
                        "create":null,
                        "rename":null,
                        "remove":null,
                        "ccp":null,
                        "sub":{
                            "label":"新增子区域",
                            "action":function(data){
                                var inst = $.jstree.reference(data.reference),
                                    jsonData = inst.get_node(data.reference).original;
                                window.handleAddLocation(jsonData['meId']);
                            }
                        },
                        "del":{
                            "label":"删除该区域",
                            "action":function(data) {
                                if (!guardWriteAction()) {
                                    return;
                                }
                                var inst = $.jstree.reference(data.reference),
                                    jsonData = inst.get_node(data.reference).original;
                                layer.confirm('删除该区域将同时摄像摄像头,确定删除吗?', {
                                    icon: 3,
                                    title: '提示'
                                }, function (index) {
                                    layer.close(index);
                                    var loading = layer.load(2);
                                    $.post('/location/delete', {'id': jsonData['meId']}, function(res) {
                                        layer.close(loading);
                                        if(res.code == 0) {
                                            layer.msg('操作成功', {icon:1, time:1000}, function() {
                                                window.createTree();
                                            });
                                        } else {
                                            popup.failure(res.msg);
                                        }
                                    });
                                });
                            }
                        },
                        "edit":{
                            "label":"编辑该区域",
                            "action":function(data){
                                var inst = $.jstree.reference(data.reference),
                                    jsonData = inst.get_node(data.reference).original;
                                window.handleEditLocation(jsonData['meId']);
                            }
                        },
                        "camera":{
                            "label":"创建摄像头",
                            "action":function(data) {
                                if (!guardWriteAction()) {
                                    return;
                                }
                                var inst = $.jstree.reference(data.reference),
                                    jsonData = inst.get_node(data.reference).original;
                                window.addForm(jsonData['meId']);
                                //parent.layui.admin.addTab('camera_action_page', '新增摄像头', '/camera/newForm?locationId=' + jsonData['meId']);
                            }
                        }
                    }
                },
                'plugins' : [ "contextmenu" ]
            });
        }

        //
        window.refreshTree = function() {
            if(treeInst != null) {
                treeInst.jstree(true).refresh();
            }
        }

        // 重置表单
        window.resetFormData = function() {
            $('#locationId').val('');
            $('#name').val('');
            table.reload('table', {
                where: {}
            });
        }

        //
        $(document).ready(function() {
            loadPermissionMatrix(function() {
                window.createTree();
            });
        });
    })
</script>
</html>
