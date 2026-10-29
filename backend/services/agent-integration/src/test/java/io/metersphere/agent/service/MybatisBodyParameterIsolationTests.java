package io.metersphere.agent.service;

import io.metersphere.functional.domain.FunctionalCaseBlob;
import io.metersphere.sdk.util.FunctionalCaseBlobCodec;
import io.metersphere.system.config.interceptor.FunctionalCaseInterceptor;
import io.metersphere.system.interceptor.MybatisInterceptor;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MybatisBodyParameterIsolationTests {
    private MybatisInterceptor plugin() {
        var plugin = new MybatisInterceptor();
        plugin.setInterceptorConfigList(new FunctionalCaseInterceptor().functionalCaseCompressConfigs());
        return plugin;
    }

    @Test void repeatedNamedParameterWriteDoesNotReplaceCallerLogicalBytes() throws Throwable {
        byte[] logical = "[{\"description\":\"hello\"}]".getBytes(StandardCharsets.UTF_8);
        var body = new FunctionalCaseBlob();
        body.setId("body"); body.setSteps(logical);
        var parameters = new MapperMethod.ParamMap<Object>();
        parameters.put("record", body); parameters.put("param1", body);
        Executor executor = mock(Executor.class);
        when(executor.update(any(), any())).thenAnswer(call -> {
            var actual = (MapperMethod.ParamMap<?>) call.getArgument(1);
            assertSame(actual.get("record"), actual.get("param1"));
            assertArrayEquals(logical, (byte[]) FunctionalCaseBlobCodec.unpack(
                    ((FunctionalCaseBlob) actual.get("record")).getSteps()));
            return 1;
        });
        var method = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        var plugin = plugin();
        for (int i = 0; i < 2; i++) {
            plugin.intercept(new Invocation(executor, method, new Object[]{mock(MappedStatement.class), parameters}));
            assertSame(body, parameters.get("record"));
            assertArrayEquals(logical, body.getSteps());
        }
        verify(executor, times(2)).update(any(), any());
    }

    @Test void heterogeneousAndNonArrayListsKeepEveryRowAndDecodeBody() throws Throwable {
        byte[] logical = "[]".getBytes(StandardCharsets.UTF_8);
        var packed = new FunctionalCaseBlob();
        packed.setSteps((byte[]) FunctionalCaseBlobCodec.pack(logical));
        for (List<Object> rows : List.<List<Object>>of(new ArrayList<>(List.of("metadata", packed, "tail")),
                new LinkedList<>(List.of("metadata", packed, "tail")))) {
            Executor executor = mock(Executor.class);
            when(executor.query(any(), any(), any(), any())).thenReturn(rows);
            var method = Executor.class.getMethod("query", MappedStatement.class, Object.class,
                    RowBounds.class, ResultHandler.class);
            var result = (List<?>) plugin().intercept(new Invocation(executor, method,
                    new Object[]{mock(MappedStatement.class), null, RowBounds.DEFAULT, null}));
            assertEquals(3, result.size());
            assertEquals("metadata", result.get(0));
            assertArrayEquals(logical, ((FunctionalCaseBlob) result.get(1)).getSteps());
            assertEquals("tail", result.get(2));
            assertSame(packed, rows.get(1));
        }
    }
}
