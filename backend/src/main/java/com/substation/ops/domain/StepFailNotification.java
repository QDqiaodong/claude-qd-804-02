package com.substation.ops.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 倒闸操作失败站内通知：失败记录落库时，向本票调度员、监护人各生成一行。
 *
 * <p>两名接收人各自确认已读（{@code read_time} 相互独立，不互相覆盖）；
 * 通知未读不影响原步骤重试；原步骤重试成功后 {@code resolved} 置为已解除，
 * 双方已读时间仍保留。该行只增不物理删除（解除行同样留痕，不能当普通通知删掉）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "step_fail_notification")
public class StepFailNotification {

    /** 接收角色：调度员（发令人） */
    public static final String ROLE_DISPATCHER = "DISPATCHER";
    /** 接收角色：监护人 */
    public static final String ROLE_GUARDIAN = "GUARDIAN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** 票号冗余：通知列表无需回查操作票即可展示 */
    @Column(name = "ticket_no", length = 64)
    private String ticketNo;

    @Column(name = "step_no", nullable = false)
    private Integer stepNo;

    /** 步骤内容冗余：通知直接展示「第 N 步 · 拉开 101 断路器」 */
    @Column(name = "action_desc", length = 255)
    private String actionDesc;

    @Column(name = "fail_person", length = 32)
    private String failPerson;

    @Column(name = "fail_time", nullable = false)
    private LocalDateTime failTime;

    @Column(name = "fail_reason", length = 255, nullable = false)
    private String failReason;

    @Column(name = "recipient_name", length = 32, nullable = false)
    private String recipientName;

    @Column(name = "recipient_role", length = 16, nullable = false)
    private String recipientRole;

    /** 该接收人的确认已读时间；两个接收人各写各的行，彼此不覆盖 */
    @Column(name = "read_time")
    private LocalDateTime readTime;

    /** 原步骤重试成功后转为已解除（true）；解除不抹掉任何已读时间 */
    @Column(name = "resolved", nullable = false)
    private Boolean resolved = Boolean.FALSE;

    @Column(name = "resolve_time")
    private LocalDateTime resolveTime;
}
