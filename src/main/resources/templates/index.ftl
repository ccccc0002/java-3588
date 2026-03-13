<!DOCTYPE html>
<html>
	<head>
		<meta charset="utf-8">
		<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
		<title>AI 视频监控管理</title>
		<link rel="stylesheet" href="/static/component/pear/css/pear.css" />
		<link rel="stylesheet" href="/static/admin/css/loader.css" />
		<link rel="stylesheet" href="/static/admin/css/admin.css" />
		<style>
			.recorder { padding: 0; position: absolute; right: 20px; bottom: 20px; width: 50px; height: 50px; font-size: 40px; color: #36b368; border: 2px solid #36b368; z-index: 900; border-radius: 4px; display: flex; flex-direction: row; justify-content: center; align-items: center; }
			.recorder i { color: #36b368; margin: 0; padding: 0; font-size: 22px; }
			.recorder a i {  }
		</style>
	</head>
	<body class="layui-layout-body pear-admin">
		<div class="layui-layout layui-layout-admin">
			<div class="layui-header">
				<div class="layui-logo">
					<img class="logo">
					<span class="title" style="color: #ffffff !important; "></span>
				</div>
				<ul class="layui-nav layui-layout-left">
					<li class="collapse layui-nav-item"><a href="#" class="layui-icon layui-icon-shrink-right"></a></li>
					<li class="refresh layui-nav-item"><a href="#" class="layui-icon layui-icon-refresh-1" loading = 600></a></li>
				</ul>
				<div id="control" class="layui-layout-control"></div>
				<ul class="layui-nav layui-layout-right">
					<li class="layui-nav-item layui-hide-xs" style="display: none;"><a href="#" class="fullScreen layui-icon layui-icon-screen-full"></a></li>
					<li class="layui-nav-item layui-hide-xs" style="display: none;"><a href="http://www.pearadmin.com" class="layui-icon layui-icon-website"></a></li>
					<li class="layui-nav-item layui-hide-xs message" style="display: none;"></li>
					<li class="layui-nav-item user">
						<a class="layui-icon layui-icon-username" href="javascript:">${accountName!''} [${accountRole!''}]</a>
						<dl class="layui-nav-child">
							<dd><a href="javascript:void(0);" user-menu-title="profile" onclick="showPass();">更新密码</a></dd>
							<dd><a href="/logout" class="logout">退出系统</a></dd>
						</dl>
					</li>
					<li class="layui-nav-item setting"><a href="#" class="layui-icon layui-icon-more-vertical"></a></li>
				</ul>
			</div>
			<div class="layui-side layui-bg-black">
				<div class="layui-logo">
					<img class="logo">
					<span class="title"></span>
				</div>
				<div class="layui-side-scroll">
					<div id="sideMenu"></div>
				</div>
			</div>
			<div class="layui-body">
				<div id="content"></div>
			</div>
			<div class="layui-footer layui-text">
				<span class="left">
					Released under the MIT license.
				</span>
				<span class="center"></span>
				<span class="right">
					Copyright © 2021-2022 pearadmin.com
				</span>
			</div>
			<div class="pear-cover"></div>
			<div class="loader-main">
				<div class="loader"></div>
			</div>

<#--			<a href="javascript:void(0);" class="recorder" onclick="window.playRecorder();"><i class="layui-icon layui-icon-play" id="recorderIcon"></i></a>-->
		</div>
		<div class="pear-collapsed-pe collapse">
			<a href="#" class="layui-icon layui-icon-shrink-right"></a>
		</div>

		<script src="/static/component/layui/layui.js?v=1.0"></script>
		<script src="/static/component/pear/pear.js"></script>
		<script src="/static/js/sockjs.min.js"></script>
		<script src="/static/js/recorder.mp3.min.js"></script>
		<script src="/static/js/recorder.wav.min.js"></script>
		<script>
			layui.use(['admin','jquery','popup','drawer', 'toast'], function() {
				var $ = layui.jquery;
				var admin = layui.admin;
				var popup = layui.popup;
				var toast = layui.toast;

					admin.setConfigType("yml");
					admin.setConfigPath("/static/config/pear.config.yml?v=1.22");
					
					admin.render();
					var dynamicBrandTitle = '${(brandTitle!"")?js_string}';
					var dynamicBrandLogoUrl = '${(brandLogoUrl!"")?js_string}';
					if (dynamicBrandTitle) {
						document.title = dynamicBrandTitle;
						$('.layui-logo .title').text(dynamicBrandTitle);
					}
					if (dynamicBrandLogoUrl) {
						$('.layui-logo .logo').attr('src', dynamicBrandLogoUrl);
					}
				
				// 登出逻辑 
				// admin.logout(function(){
				// 	popup.success("注销成功",function(){
				// 		location.href = "login.html";
				// 	})
				// 	// 注销逻辑 返回 true / false
				// 	return true;
				// });

				/*
				//
				var recorder = Recorder({type:"wav", sampleRate:16000, bitRate:16});;
				var recorderPlay = false;

				//
				window.playRecorder = function() {
					if(!recorderPlay) {
						recorderPlay = true;
						window.startRecorder();
					} else {
						recorderPlay = false;
						window.stopRecorder();
					}
				}

				//
				window.startRecorder = function() {
					recorder.open(function() {
						recorder.start();
						$('#recorderIcon').removeClass('layui-icon-play').removeClass('layui-icon-upload-circle').addClass('layui-icon-pause');
					}, function(msg, isUserNotAllow) {
						recorderPlay = false;
						if(isUserNotAllow) {
							toast.warning({title: '提示', message: '用户拒绝录音', position: 'topCenter', timeout: 1500});
						} else {
							toast.warning({title: '提示', message: '浏览器不支持录音', position: 'topCenter', timeout: 1500});
						}
					});
				}

				//
				window.stopRecorder = function() {
					recorder.stop(function(blob, duration) {
						$('#recorderIcon').removeClass('layui-icon-play').removeClass('layui-icon-pause').addClass('layui-icon-upload-circle');

						var formData = new FormData();
						formData.append("file", blob, "recorder.wav");

						// var audio=document.createElement("audio");
						// audio.controls=true;
						// document.body.appendChild(audio);
						//
						// //非常简单的就能拿到blob音频url
						// audio.src=URL.createObjectURL(blob);
						// audio.play();

						$.ajax({
							url: '/recorder/upload',
							method: 'post',
							data: formData,
							contentType: false,
							cache: false,
							processData: false,
							success: function(data) {
								console.log(data);
								<#--if (JSON.parse(data).result == 1) {-->
								<#--	$('.prompt').html(`文件${JSON.parse(data).filename}已上传成功`);-->
								<#--}-->
								recorderPlay = false;
								$('#recorderIcon').removeClass('layui-icon-pause').removeClass('layui-icon-upload-circle').addClass('layui-icon-play');
								if(data.code == 0) {
									if(data.data == 24) {
										admin.addTab('24', '基地管理', '/warehouse');
									} else if(data.data == 10) {
										admin.addTab('10', '算法管理', '/algorithm');
									} else if(data.data == 11) {
										admin.addTab('11', '摄像头管理', '/camera');
									}
								} else {
									toast.warning({title: '提示', message: data.msg, position: 'topCenter', timeout: 1500});
								}
							},
							error: function (err) {
								recorderPlay = false;
								$('#recorderIcon').removeClass('layui-icon-pause').removeClass('layui-icon-upload-circle').addClass('layui-icon-play');
								toast.warning({title: '提示', message: '上传录音错误', position: 'topCenter', timeout: 1500});
							}

						});
					}, function(msg) {
						recorderPlay = false;
						toast.warning({title: '提示', message: '录音失败:' + msg, position: 'topCenter', timeout: 1500});
					});
				}
				*/

				window.showPass = function () {
					layer.open({
						type: 2,
						title: '更新密码',
						shade: 0.1,
						area: ['60%', '60%'],
						content: '/account/password'
					});
				}

				// socket
				var ws = null;
				var wsTimer = null;
				window.initSocket = function() {
					ws = new WebSocket('${wsUrl!''}/message/${uid!''}');
					ws.onopen = function () {
						wsTimer = setInterval(function() {
							ws.send({'r': new Date().getTime()});
						}, 10000);
					};

					//
					ws.onmessage = function(event) {
						var res = JSON.parse(event.data);
						if(res.type == 'REPORT') {
							toast.warning({title: '警告', message: res.content + '<a href="javascript:void(0);" onclick="handleReport(\'' + res.data.reportId + '\', \'' + res.data.cameraName + '\');" style="margin-left: 15px; color: #409EFF;">查看详情</a>', position: 'topCenter', timeout: 300});
						}
					};

					//
					ws.onclose = function() {
						ws = null;
						clearInterval(wsTimer);
						wsTimer = null;
						setTimeout(function() {
							window.initSocket();
						}, 5000);
					}
				}

				// report
				window.handleReport = function(reportId, cameraName) {
					admin.addTab(reportId, cameraName + '告警', '/report/detail?id=' + reportId);
				}

				window.initSocket();
			})
		</script>		
	</body>
</html>
