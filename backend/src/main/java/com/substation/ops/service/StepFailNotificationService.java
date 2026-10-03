package com.substation.ops.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.substation.ops.common.BizException;
import com.substation.ops.domain.OperationStep;
import com.substation.ops.domain.OperationTicket;
import com.substation.ops.domain.StepFailNotification;
import com.substation.ops.repository.StepFailNotificationRepository;

/**
 * 倒闸操作失败通知：
 *
 * <ul>
 *   <li>失败记录落库时（与步骤状态同一事务）向本票调度员、监护人各发一条站内通知，
 *       内容含票号、步骤（序号 + 内容）、失败原因、失败人、发生时间；</li>
 *   <li>两类接收人各自一行、各自确认已读，彼此的已读状态互不覆盖；</li>
 *   <li>未读不阻断原步骤重试：重试与通知完全解耦；</li>
 *   <li>原步重试成功后通知置为「已解除」，双方已读时间原样保留；
 *       通知只增不物理删除（解除行留痕，不能按普通通知删除）；</li>
 *   <li>同一步在重试成功前重复登记失败：刷新该步未解除通知的原因与发生时间并重新置未读。</li>
 * </ul>
 */
@Service
public class StepFailNotificationService {

    private final StepFailNotificationRepository repository;

    public StepFailNotificationService(StepFailNotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * 失败落库时通知本票调度员、监护人。必须在 {@code failStep} 的事务内调用，
     * 保证「失败记录」与「通知生成」同生共死。
     */
    @Transactional
    public void notifyFailure(OperationTicket ticket, OperationStep step, String person, String reason) {
        LocalDateTime now = step.getFailTime() == null ? LocalDateTime.now() : step.getFailTime();
        upsert(ticket, step, person, reason, now, StepFailNotification.ROLE_DISPATCHER, ticket.getDispatcher());
        upsert(ticket, step, person, reason, now, StepFailNotification.ROLE_GUARDIAN, ticket.getGuardian());
    }

    private void upsert(OperationTicket ticket, OperationStep step, String person, String reason,
                        LocalDateTime failTime, String role, String recipient) {
        if (recipient == null || recipient.isBlank()) {
            // 票面未指派该角色时没有接收人，不产生空名通知
            return;
        }
        StepFailNotification n = repository
                .findFirstByTicketIdAndStepNoAndRecipientNameAndResolvedFalse(
                        ticket.getId(), step.getStepNo(), recipient)
                .orElseGet(StepFailNotification::new);
        boolean fresh = n.getId() == null;
        n.setTicketId(ticket.getId());
        n.setTicketNo(ticket.getTicketNo());
        n.setStepNo(step.getStepNo());
        n.setActionDesc(step.getActionDesc());
        n.setFailPerson(person);
        n.setFailTime(failTime);
        n.setFailReason(reason);
        n.setRecipientName(recipient);
        n.setRecipientRole(role);
        // 同一步再次登记失败 = 一次新的失败事件：此前的确认作废，需要双方重新确认
        if (!fresh) {
            n.setReadTime(null);
        }
        n.setResolved(Boolean.FALSE);
        n.setResolveTime(null);
        repository.save(n);
    }

    /**
     * 原步骤重试成功：该步未解除通知全部置为已解除。
     * 不动 read_time —— 双方已读时间必须保留。在 {@code executeStep} 事务内调用。
     */
    @Transactional
    public void resolveStep(Long ticketId, Integer stepNo, LocalDateTime resolveTime) {
        repository.resolveStep(ticketId, stepNo, resolveTime);
    }

    /**
     * 接收人确认已读：只写自己那一行，另一方的已读时间不受影响。
     * 已解除的通知仍可补确认（已读时间照样保留）。
     */
    @Transactional
    public void markRead(Long id, String person) {
        if (person == null || person.isBlank()) {
            throw new BizException("请先选择当前值班员身份后再确认");
        }
        StepFailNotification n = repository.findById(id)
                .orElseThrow(() -> new BizException("通知不存在或已被清理"));
        if (!person.equals(n.getRecipientName())) {
            throw new BizException("该通知只发送给" + roleName(n.getRecipientRole())
                    + "「" + n.getRecipientName() + "」，不能代确认");
        }
        if (n.getReadTime() == null) {
            n.setReadTime(LocalDateTime.now());
            repository.save(n);
        }
    }

    /** 当前值班员的通知列表：未解除在前、发生时间倒序 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listFor(String person) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (person == null || person.isBlank()) {
            return result;
        }
        for (StepFailNotification n : repository.findRecipientNotifications(person)) {
            result.add(toMap(n));
        }
        return result;
    }

    /** 未解除未读数（顶部铃铛角标） */
    @Transactional(readOnly = true)
    public long unreadCount(String person) {
        if (person == null || person.isBlank()) {
            return 0;
        }
        return repository.countUnread(person);
    }

    /**
     * 票面失败提示与留痕：按步骤号聚合该票通知。
     * activeOnly=true 只含未解除（页面持续标出尚未确认的角色）；
     * false 含已解除（重试成功后展示解除时间，双方已读时间保留可查）。
     */
    @Transactional(readOnly = true)
    public Map<Integer, List<Map<String, Object>>> groupByStep(Long ticketId, boolean activeOnly) {
        List<StepFailNotification> all = activeOnly
                ? repository.findByTicketIdAndResolvedFalseOrderByStepNoAscRecipientRoleAsc(ticketId)
                : repository.findByTicketIdOrderByFailTimeDescIdDesc(ticketId);
        Map<Integer, List<Map<String, Object>>> grouped = new HashMap<>();
        for (StepFailNotification n : all) {
            grouped.computeIfAbsent(n.getStepNo(), k -> new ArrayList<>()).add(toMap(n));
        }
        return grouped;
    }

    private Map<String, Object> toMap(StepFailNotification n) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", n.getId());
        m.put("ticketId", n.getTicketId());
        m.put("ticketNo", n.getTicketNo());
        m.put("stepNo", n.getStepNo());
        m.put("actionDesc", n.getActionDesc());
        m.put("failPerson", n.getFailPerson());
        m.put("failTime", n.getFailTime());
        m.put("failReason", n.getFailReason());
        m.put("recipientName", n.getRecipientName());
        m.put("recipientRole", n.getRecipientRole());
        m.put("recipientRoleName", roleName(n.getRecipientRole()));
        m.put("readTime", n.getReadTime());
        m.put("read", n.getReadTime() != null);
        boolean resolved = Boolean.TRUE.equals(n.getResolved());
        m.put("resolved", resolved);
        m.put("resolveTime", n.getResolveTime());
        return m;
    }

    /** 角色中文名：调度员（发令人）/ 监护人 */
    public static String roleName(String role) {
        return StepFailNotification.ROLE_GUARDIAN.equals(role) ? "监护人" : "调度员";
    }
}
