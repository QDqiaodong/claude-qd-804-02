package com.substation.ops.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.substation.ops.domain.StepFailNotification;

public interface StepFailNotificationRepository extends JpaRepository<StepFailNotification, Long> {

    /** 某接收人的通知：未解除在前，再按发生时间倒序（同一失败的调度员/监护人各一行） */
    @Query("select n from StepFailNotification n where n.recipientName = :recipientName "
            + "order by n.resolved asc, n.failTime desc, n.id desc")
    List<StepFailNotification> findRecipientNotifications(@Param("recipientName") String recipientName);

    /** 未读角标：未解除且该接收人尚未确认（解除后的未读不再打扰） */
    @Query("select count(n) from StepFailNotification n "
            + "where n.recipientName = :recipientName and n.readTime is null and n.resolved = false")
    long countUnread(@Param("recipientName") String recipientName);

    /** 票面失败提示用：该票全部未解除通知，按步骤号、角色排序 */
    List<StepFailNotification> findByTicketIdAndResolvedFalseOrderByStepNoAscRecipientRoleAsc(Long ticketId);

    /** 票面留痕用：该票全部通知（含已解除），按发生时间倒序 */
    List<StepFailNotification> findByTicketIdOrderByFailTimeDescIdDesc(Long ticketId);

    Optional<StepFailNotification> findFirstByTicketIdAndStepNoAndRecipientNameAndResolvedFalse(
            Long ticketId, Integer stepNo, String recipientName);

    /** 原步骤重试成功：该步未解除通知批量置为已解除（双方各自的 read_time 不参与更新，原样保留） */
    @Modifying
    @Query("update StepFailNotification n set n.resolved = true, n.resolveTime = :resolveTime "
            + "where n.ticketId = :ticketId and n.stepNo = :stepNo and n.resolved = false")
    int resolveStep(@Param("ticketId") Long ticketId,
                    @Param("stepNo") Integer stepNo,
                    @Param("resolveTime") java.time.LocalDateTime resolveTime);
}
