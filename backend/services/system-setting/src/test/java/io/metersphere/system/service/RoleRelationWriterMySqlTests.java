package io.metersphere.system.service;

import io.metersphere.system.base.BaseTest;
import io.metersphere.system.domain.UserRoleRelation;
import io.metersphere.system.domain.UserRoleRelationExample;
import io.metersphere.system.mapper.UserRoleRelationMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real unique-index and transaction verification; this does not substitute for endpoint authorization tests. */
class RoleRelationWriterMySqlTests extends BaseTest {
    @Resource RoleRelationWriter writer;
    @Resource UserRoleRelationMapper mapper;
    @Resource org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void overlappingConcurrentTransactionsCreateOneRelationshipAndRetainIdentity() throws Exception {
        String scope = "ci-" + UUID.randomUUID();
        UserRoleRelation first = relation(scope);
        UserRoleRelation second = relation(scope);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { assertTrue(start.await(10, TimeUnit.SECONDS)); return writer.ensurePresent(first); });
            var b = pool.submit(() -> { assertTrue(start.await(10, TimeUnit.SECONDS)); return writer.ensurePresent(second); });
            start.countDown();
            boolean insertedA = a.get(20, TimeUnit.SECONDS);
            boolean insertedB = b.get(20, TimeUnit.SECONDS);
            assertNotEquals(insertedA, insertedB);
            var existing = mapper.selectByExample(scopeExample(scope));
            assertEquals(1, existing.size());
            assertEquals(insertedA ? first.getId() : second.getId(), existing.getFirst().getId());
            assertFalse(writer.ensurePresent(relation(scope)));
            assertEquals(existing.getFirst().getId(), mapper.selectByExample(scopeExample(scope)).getFirst().getId());
        } finally {
            mapper.deleteByExample(scopeExample(scope));
        }
    }

    @Test
    void unrelatedCollisionRollsBackEarlierRelationshipInSameTransaction() {
        String scope = "ci-" + UUID.randomUUID();
        UserRoleRelation occupied = relation(scope);
        mapper.insert(occupied);
        UserRoleRelation collision = relation(scope);
        collision.setId(occupied.getId());
        collision.setUserId("different-user");
        UserRoleRelation preceding = relation(scope);
        preceding.setUserId("new-user");
        try {
            assertThrows(DuplicateKeyException.class, () -> new TransactionTemplate(transactionManager).execute(status -> {
                writer.ensurePresent(preceding);
                writer.ensurePresent(collision);
                return null;
            }));
            List<UserRoleRelation> rows = mapper.selectByExample(scopeExample(scope));
            assertEquals(1, rows.size());
            assertEquals(occupied.getId(), rows.getFirst().getId());
        } finally {
            mapper.deleteByExample(scopeExample(scope));
        }
    }

    private UserRoleRelation relation(String scope) {
        UserRoleRelation row = new UserRoleRelation();
        row.setId(UUID.randomUUID().toString());
        row.setUserId("ci-relation-user");
        row.setRoleId("ci-relation-role");
        row.setSourceId(scope);
        row.setOrganizationId(scope);
        row.setCreateUser("admin");
        row.setCreateTime(System.currentTimeMillis());
        return row;
    }

    private UserRoleRelationExample scopeExample(String scope) {
        UserRoleRelationExample example = new UserRoleRelationExample();
        example.createCriteria().andSourceIdEqualTo(scope);
        return example;
    }
}
