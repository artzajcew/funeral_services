package ru.memory.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.memory.backend.entity.AuditEntry;
import ru.memory.backend.entity.User;
import ru.memory.backend.repository.AuditRepository;
import ru.memory.backend.repository.UserRepository;

/**
 * Запись действий в журнал аудита.
 *
 * log()   — пишет ПОСЛЕ коммита текущей транзакции (afterCommit) в
 *           отдельной транзакции: запись появляется только если
 *           основная операция зафиксирована, и ссылается на уже
 *           существующего пользователя (FK);
 * logNow() — пишет немедленно в отдельной транзакции — для событий,
 *           связанных с откатом (неудачный вход).
 *
 * Сбой журнала не ломает основной сценарий: ошибки глушатся.
 */
@Service
public class AuditService {

    private final AuditRepository auditRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    public AuditService(AuditRepository auditRepository,
                        UserRepository userRepository,
                        org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
                Propagation.REQUIRES_NEW.value());
    }

    /** Запись после успешного коммита текущей транзакции. */
    public void log(Integer userId, String action, String entityType,
                    Integer entityId, String details) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            write(userId, action, entityType, entityId, details);
                        }
                    });
        } else {
            write(userId, action, entityType, entityId, details);
        }
    }

    /** Немедленная запись (для неудачных входов и отказов в доступе). */
    public void logNow(Integer userId, String action, String entityType,
                       Integer entityId, String details) {
        write(userId, action, entityType, entityId, details);
    }

    private void write(Integer userId, String action, String entityType,
                       Integer entityId, String details) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                User user = userId == null ? null : userRepository.getReferenceById(userId);
                auditRepository.save(new AuditEntry(user, action, entityType, entityId, details));
            });
        } catch (RuntimeException e) {
            System.err.println("[audit] Не удалось записать действие " + action
                    + " пользователя " + userId + ": " + e.getMessage());
        }
    }
}
