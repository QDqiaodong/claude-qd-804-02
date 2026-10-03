package com.substation.ops.config;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 老库（镜像卷已存在、schema.sql 不会重跑）启动时补建「操作失败通知」表。
 * CREATE TABLE IF NOT EXISTS 非破坏式：新库由 mysql/schema.sql 建过则空操作，
 * 老库就地补齐，不影响既有数据。
 */
@Slf4j
@Component
@Order(2)
public class StepFailNotificationTableInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public StepFailNotificationTableInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `step_fail_notification` (
                    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
                    `ticket_id`      BIGINT       NOT NULL COMMENT '所属操作票',
                    `ticket_no`      VARCHAR(64)  DEFAULT NULL COMMENT '票号（冗余）',
                    `step_no`        INT          NOT NULL COMMENT '失败步骤序号',
                    `action_desc`    VARCHAR(255) DEFAULT NULL COMMENT '步骤内容（冗余）',
                    `fail_person`    VARCHAR(32)  DEFAULT NULL COMMENT '失败登记人',
                    `fail_time`      DATETIME     NOT NULL COMMENT '失败发生时间',
                    `fail_reason`    VARCHAR(255) NOT NULL COMMENT '失败原因',
                    `recipient_name` VARCHAR(32)  NOT NULL COMMENT '接收人（调度员或监护人）',
                    `recipient_role` VARCHAR(16)  NOT NULL COMMENT 'DISPATCHER/GUARDIAN',
                    `read_time`      DATETIME     DEFAULT NULL COMMENT '该接收人确认已读时间',
                    `resolved`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '原步重试成功后已解除',
                    `resolve_time`   DATETIME     DEFAULT NULL COMMENT '解除时间',
                    PRIMARY KEY (`id`),
                    KEY `idx_sfn_recipient` (`recipient_name`, `resolved`),
                    KEY `idx_sfn_ticket_step` (`ticket_id`, `step_no`, `resolved`),
                    KEY `idx_sfn_ticket` (`ticket_id`)
                ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
                  COMMENT ='倒闸操作失败站内通知'
                """);
        log.info("step_fail_notification 表已就绪");
    }
}
