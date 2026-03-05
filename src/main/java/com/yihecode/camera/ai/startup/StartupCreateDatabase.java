package com.yihecode.camera.ai.startup;

import com.yihecode.camera.ai.service.CameraService;
import com.yihecode.camera.ai.service.ConfigService;
import com.yihecode.camera.ai.service.VideoPlayService;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 系统启动管理
 *
 * @author zhoumingxing
 * @mail 465769438@qq.com
 */
@Configuration
@Slf4j
public class StartupCreateDatabase {

    @Resource
    private HikariDataSource dataSource;

    @Value("${uploadDir}")
    private String uploadDir;

    @Autowired
    private CameraService cameraService;

    @Autowired
    private ConfigService configService;

    @Autowired
    private VideoPlayService videoPlayService;

    /**
     * startup initialization
     * @throws ClassNotFoundException
     * @throws URISyntaxException
     * @throws SQLException
     */
    @PostConstruct
    public void init() throws ClassNotFoundException, URISyntaxException, SQLException {
        // 创建文件上传目录
        File dir = new File(uploadDir);
        if(!dir.exists()) {
            dir.mkdirs();
        }

        // 创建数据库
        String driver = dataSource.getDriverClassName();
        String url = dataSource.getJdbcUrl();
        String username = dataSource.getUsername();
        String password = dataSource.getPassword();

        Class.forName(driver);
        URI uri = new URI(url.replace("jdbc:", ""));
        String host = uri.getHost();
        int port = uri.getPort();
        String path = uri.getPath();

        String connectUrl = "jdbc:mysql://" + host + ":" + port;
        try (Connection connection = DriverManager.getConnection(connectUrl, username, password);
             Statement statement = connection.createStatement()) {
            // create database
            statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + path.replace("/", "") + "` DEFAULT CHARACTER SET = `utf8mb4` COLLATE `utf8mb4_general_ci`;");
        }
    }
}
