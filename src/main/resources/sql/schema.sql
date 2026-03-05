/*
Navicat MySQL Data Transfer

Source Server         : localhost_3306
Source Server Version : 50626
Source Host           : localhost:3306
Source Database       : ai_data

Target Server Type    : MYSQL
Target Server Version : 50626
File Encoding         : 65001

Date: 2022-10-31 11:19:43
*/

-- ----------------------------
-- Table structure for `tbl_biz_account`
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tbl_biz_account` (
                                   `id` bigint(20) NOT NULL DEFAULT '0',
                                   `account` varchar(50) DEFAULT NULL COMMENT 'Login account',
                                   `password` varchar(128) DEFAULT NULL COMMENT 'Login password',
                                   `name` varchar(50) DEFAULT NULL COMMENT 'Real name',
                                   `state` smallint(1) DEFAULT NULL COMMENT 'State 0-ok 1-disabled',
                                   `created_at` datetime DEFAULT NULL COMMENT 'Created Date',
                                   `updated_at` datetime DEFAULT NULL COMMENT 'Update Date',
                                   PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for `tbl_biz_algorithm`
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tbl_biz_algorithm` (
                                     `id` bigint(20) NOT NULL DEFAULT '0',
                                     `name` varchar(50) DEFAULT NULL COMMENT 'Algorithm name',
                                     `frequency` int(5) DEFAULT NULL COMMENT 'Draw frame frequency （discard）',
                                     `interval_time` int(5) DEFAULT NULL COMMENT 'Alarm interval （discard）',
                                     `params` text COMMENT 'Prohibited area （discard）',
                                     `created_at` datetime DEFAULT NULL COMMENT 'Create Date',
                                     `updated_at` datetime DEFAULT NULL COMMENT 'Update Date',
                                     `file_name` varchar(500) DEFAULT NULL COMMENT '（discard）',
                                     `file_width` int(5) DEFAULT NULL COMMENT '（discard）',
                                     `file_height` int(5) DEFAULT NULL COMMENT '（discard）',
                                     `canvas_width` int(5) DEFAULT NULL COMMENT '（discard）',
                                     `canvas_height` int(5) DEFAULT NULL COMMENT '（discard）',
                                     `scale_ratio` float(12,2) DEFAULT NULL COMMENT '（discard）',
                                     `statics_flag` smallint(1) DEFAULT '0' COMMENT '关联视频流统计 0-不关联 1-关联',
                                     `name_en` varchar (50) DEFAULT '' COMMENT '算法英文',
                                     `model_path` varchar (200) DEFAULT '' COMMENT '模型路径',

  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for `tbl_biz_camera`
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tbl_biz_camera` (
                                  `id` bigint(20) NOT NULL DEFAULT '0',
                                  `name` varchar(50) DEFAULT NULL COMMENT 'Camera name',
                                  `rtsp_url` text COMMENT 'camera rtsp stream url',
                                  `action` smallint(1) DEFAULT NULL COMMENT 'Action 0-no action 1-add action  2-delete action 3-update action',
                                  `created_at` date DEFAULT NULL COMMENT 'Create date',
                                  `updated_at` date DEFAULT NULL COMMENT 'Update date',
                                  `state` smallint(1) DEFAULT NULL COMMENT 'Camera state 0-Normal 1-Closed',
                                  `running` smallint(1) DEFAULT NULL COMMENT 'Running state 0-Closed 1-Running',
                                  `interval_time` float(5,2) DEFAULT NULL COMMENT 'Alarm interval  （Unit: second）',
  `frequency` int(5) DEFAULT NULL COMMENT 'Sampling frequency （Unit: ms）',
  `file_name` varchar(500) DEFAULT NULL COMMENT 'Background image file name of forbidden area',
  `file_width` int(5) DEFAULT NULL COMMENT 'File width',
  `file_height` int(5) DEFAULT NULL COMMENT 'File height',
  `canvas_width` int(5) DEFAULT NULL COMMENT 'Canvas width',
  `canvas_height` int(5) DEFAULT NULL COMMENT 'Canvas height',
  `scale_ratio` float(12,4) DEFAULT NULL COMMENT 'Scale',
  `params` text COMMENT 'Canvas coordinate point',
  `api_params` text COMMENT 'API coordinate point',
  `warehouse_id` BIGINT(20) DEFAULT NULL,
  `rtsp_type` smallint(1) DEFAULT '0' COMMENT '视频流类型 0-实时 1-备份',
  `location_id` BIGINT(20) DEFAULT '0' COMMENT '区域id',
  `location_ids` text COMMENT '区域ids',
  `video_play` smallint(1) DEFAULT '0' COMMENT '摄像头播放状态 0-不推流 1-推流',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for `tbl_biz_camera_algorithm`
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tbl_biz_camera_algorithm` (
                                            `id` bigint(20) NOT NULL DEFAULT '0',
                                            `camera_id` varchar(50) DEFAULT NULL COMMENT 'camera id',
                                            `algorithm_id` text COMMENT 'Algorithm id',
                                            `confidence` FLOAT(12,2) DEFAULT '0.5' COMMENT '置信度',
                                            `mark_points` text COMMENT '区域标记',
                                            `image_points` text COMMENT '真实图片区域标记, mark_points转换成真实坐标',
                                            PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for `tbl_biz_config`
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tbl_biz_config` (
                                  `id` bigint(20) NOT NULL DEFAULT '0',
                                  `name` varchar(100) DEFAULT NULL COMMENT 'Configuration Name',
                                  `tag` varchar(100) DEFAULT NULL COMMENT 'Unique identification',
                                  `val` varchar(200) DEFAULT NULL COMMENT 'Configuration value',
                                  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for `tbl_biz_report`
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tbl_biz_report` (
                                  `id` bigint(20) NOT NULL DEFAULT '0',
                                  `camera_id` bigint(20) DEFAULT NULL COMMENT 'Camera id',
                                  `algorithm_id` bigint(20) DEFAULT NULL COMMENT 'Algorithm id',
                                  `file_name` varchar(128) DEFAULT NULL COMMENT 'Detect picture file name',
                                  `params` text COMMENT 'Detection coordinate point',
                                  `type` smallint(1) DEFAULT NULL COMMENT 'Alarm Type  1-algorithm 2-stream',
                                  `display` smallint(1) DEFAULT NULL COMMENT 'Whether to display 0-show 1-hide',
                                  `created_at` datetime DEFAULT NULL COMMENT 'Create date',
                                  `created_mills` bigint(20) DEFAULT NULL COMMENT 'Create Millisecond time',
                                  `audit_state` smallint(1) DEFAULT '0' COMMENT 'Create Millisecond time',
                                  `audit_result` smallint(1) DEFAULT '0' COMMENT 'Create Millisecond time',
                                  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ALTER TABLE `tbl_biz_report` ADD INDEX idx_created_mills (`created_mills`);

-- ALTER TABLE `tbl_biz_camera_algorithm` ADD COLUMN confidence FLOAT(12,2) DEFAULT '0.5';
--

-- ----------------------------
-- Table structure for `tbl_biz_report_period`
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tbl_biz_report_period` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `algorithm_id` bigint(20) DEFAULT NULL COMMENT '算法id',
    `camera_id` bigint(20) DEFAULT NULL COMMENT 'camera id',
    `start_time` int(5) DEFAULT NULL,
    `end_time` int(5) DEFAULT NULL,
    `start_text` varchar(20) DEFAULT NULL,
    `end_text` varchar(20) DEFAULT NULL,
    PRIMARY KEY (`id`)
    ) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4;


-- ----------------------------
-- Table structure for `tbl_biz_warehouse`
-- ----------------------------
-- DROP TABLE IF EXISTS `tbl_biz_warehouse`;
CREATE TABLE IF NOT EXISTS `tbl_biz_warehouse` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `index_code` varchar(50) DEFAULT NULL COMMENT '节点id',
    `name` varchar(50) DEFAULT NULL COMMENT '名称',
    `parent_index_code` varchar(50) DEFAULT NULL COMMENT '上一节点id',
    `tree_code` varchar(10) DEFAULT NULL COMMENT '没用',
    `tree_level` smallint(1) DEFAULT NULL COMMENT '节点层次 1,2,3,4, 5-5为摄像头，不做树形展示',
    `status` smallint(1) DEFAULT NULL COMMENT '状态 0-正常 1-异常，重新同步时，将状态全部设为异常，更新时修改为正常，然后删除掉所有异常数据',
    `rtsp_url` varchar(200) DEFAULT NULL COMMENT '视频地址',
    `pull_status` smallint(1) DEFAULT NULL COMMENT '拉取视频地址状态 0-正常 1-异常',
    `pull_time` datetime DEFAULT NULL COMMENT '最后拉取时间',
    `tree_type` SMALLINT(1) DEFAULT NULL COMMENT '树节点类型 0-目录 1-摄像头',
    PRIMARY KEY (`id`)
    ) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4;


CREATE TABLE IF NOT EXISTS `tbl_biz_camera_log` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `index_code` varchar(50) DEFAULT NULL COMMENT '节点id',
    `camera_id` bigint(20) DEFAULT NULL COMMENT '摄像头id',
    `type` smallint(1) DEFAULT NULL COMMENT '0-实时流 1-备份流',
    `params` text DEFAULT NULL COMMENT '请求参数',
    `result` text DEFAULT NULL COMMENT '结果参数',
    `code` varchar(10) DEFAULT NULL COMMENT '结果状态',
    `url` varchar(200) DEFAULT NULL COMMENT '结果URL',
    `created_at` datetime DEFAULT NULL COMMENT '创建时间',
    `camera_name` varchar(50) DEFAULT NULL COMMENT '摄像头名称',
    PRIMARY KEY (`id`)
    ) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4;


CREATE TABLE IF NOT EXISTS `tbl_biz_location` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `name` varchar(50) DEFAULT NULL COMMENT '位置名称',
  `sort` int(11) DEFAULT NULL COMMENT '排序值',
  `parent_id` bigint(20) DEFAULT NULL COMMENT '上级区域',
  `parent_names` text COMMENT '上级位置名称',
  `parent_ids` text COMMENT '上级区域ids',
  `latitude` float(12,4) DEFAULT NULL COMMENT '纬度',
  `longitude` float(12,4) DEFAULT NULL COMMENT '经度',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='摄像头区域节点<树形结构>表';


CREATE TABLE IF NOT EXISTS `tbl_biz_sms_phone` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号码',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信推送的手机号码';

CREATE TABLE IF NOT EXISTS `tbl_biz_video_play` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `camera_id` bigint(20) DEFAULT NULL COMMENT '摄像头id',
  `video_port` int(5) DEFAULT NULL COMMENT '视频端口',
  `last_time` bigint(20) DEFAULT NULL COMMENT '最后播放时间(毫秒)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1651819912689147906 DEFAULT CHARSET=utf8mb4 COMMENT='视频播放控制';
-- ----------------------------
-- Table structure for `tbl_biz_model`
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tbl_biz_model` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) DEFAULT NULL COMMENT 'Model name',
  `type` smallint(1) DEFAULT NULL COMMENT 'Model type',
  `description` varchar(500) DEFAULT NULL COMMENT 'Model description',
  `class_biz` varchar(200) DEFAULT NULL COMMENT 'Business labels',
  `class_all` varchar(500) DEFAULT NULL COMMENT 'All labels',
  `input_param` text COMMENT 'Input params',
  `output_param` text COMMENT 'Output params',
  `img_width` int(11) DEFAULT NULL COMMENT 'Input image width',
  `img_height` int(11) DEFAULT NULL COMMENT 'Input image height',
  `onnx_name` varchar(255) DEFAULT NULL COMMENT 'ONNX file name',
  `onnx_tag` varchar(255) DEFAULT NULL COMMENT 'ONNX file tag',
  `onnx_md5` varchar(128) DEFAULT NULL COMMENT 'ONNX MD5',
  `call_url` varchar(500) DEFAULT NULL COMMENT 'Model call URL',
  `state` smallint(1) DEFAULT '0' COMMENT 'State 0-enable 1-disable',
  `created_at` datetime DEFAULT NULL COMMENT 'Created time',
  `onnx_size` bigint(20) DEFAULT '0' COMMENT 'ONNX file size',
  `version_count` int(11) DEFAULT '0' COMMENT 'Version count',
  PRIMARY KEY (`id`),
  KEY `idx_model_name` (`name`),
  KEY `idx_model_state` (`state`),
  KEY `idx_model_onnx_md5` (`onnx_md5`),
  KEY `idx_model_onnx_name` (`onnx_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for `tbl_biz_model_depend`
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tbl_biz_model_depend` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `model_id` bigint(20) DEFAULT NULL COMMENT 'Model id',
  `depend_model_id` bigint(20) DEFAULT NULL COMMENT 'Dependency model id',
  PRIMARY KEY (`id`),
  KEY `idx_model_depend_model` (`model_id`),
  KEY `idx_model_depend_depend` (`depend_model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
