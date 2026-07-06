package io.metersphere.system.service.wecom;

import io.metersphere.system.dto.wecom.WecomDepartmentDTO;
import io.metersphere.system.dto.wecom.WecomUserDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.verify.VerificationTimes;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class WecomContactClientTest {

    private static final String CORP_ID = "corp-test";
    private static final String CONTACT_SECRET = "secret-test";

    private static ClientAndServer mockServer;
    private WecomContactClient wecomContactClient;

    @BeforeAll
    static void startMockServer() {
        mockServer = ClientAndServer.startClientAndServer(0);
    }

    @AfterAll
    static void stopMockServer() {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        mockServer.reset();
        wecomContactClient = new WecomContactClient();
        ReflectionTestUtils.setField(wecomContactClient, "apiBaseUrl", "http://localhost:" + mockServer.getPort());
        ReflectionTestUtils.setField(wecomContactClient, "retryTimes", 3);
        ReflectionTestUtils.setField(wecomContactClient, "connectTimeoutMs", 5000);
        ReflectionTestUtils.setField(wecomContactClient, "readTimeoutMs", 10000);
        wecomContactClient.initRestTemplate();
        wecomContactClient.invalidateAccessToken(CORP_ID, CONTACT_SECRET);
    }

    @Test
    void getAccessToken_successAndCache() {
        mockGetToken("token-1", 7200);

        String first = wecomContactClient.getAccessToken(CORP_ID, CONTACT_SECRET);
        String second = wecomContactClient.getAccessToken(CORP_ID, CONTACT_SECRET);

        Assertions.assertEquals("token-1", first);
        Assertions.assertEquals("token-1", second);
        mockServer.verify(
                request().withMethod("GET").withPath("/cgi-bin/gettoken"),
                VerificationTimes.exactly(1)
        );
    }

    @Test
    void getAccessToken_refreshWhenExpiringSoon() {
        mockGetTokenOnce("token-short", 200);
        mockGetTokenOnce("token-new", 7200);

        String first = wecomContactClient.getAccessToken(CORP_ID, CONTACT_SECRET);
        String second = wecomContactClient.getAccessToken(CORP_ID, CONTACT_SECRET);

        Assertions.assertEquals("token-short", first);
        Assertions.assertEquals("token-new", second);
        mockServer.verify(
                request().withMethod("GET").withPath("/cgi-bin/gettoken"),
                VerificationTimes.exactly(2)
        );
    }

    @Test
    void executeWithToken_tokenExpiredThenRetry() {
        mockGetTokenOnce("token-1", 7200);
        mockServer.when(
                request().withMethod("GET").withPath("/cgi-bin/department/list")
                        .withQueryStringParameter("access_token", "token-1")
        ).respond(jsonResponse(
                "{\"errcode\":42001,\"errmsg\":\"access_token expired\"}"
        ));

        mockGetTokenOnce("token-2", 7200);
        mockServer.when(
                request().withMethod("GET").withPath("/cgi-bin/department/list")
                        .withQueryStringParameter("access_token", "token-2")
        ).respond(jsonResponse(
                "{\"errcode\":0,\"errmsg\":\"ok\",\"department\":[{\"id\":1,\"name\":\"Root\",\"parentid\":0,\"order\":1}]}"
        ));

        List<WecomDepartmentDTO> departments = wecomContactClient.executeWithToken(
                CORP_ID, CONTACT_SECRET, wecomContactClient::listDepartments
        );

        Assertions.assertEquals(1, departments.size());
        Assertions.assertEquals("Root", departments.getFirst().getName());
    }

    @Test
    void listDepartments_parseCorrectly() {
        mockGetToken("token-1", 7200);
        mockServer.when(
                request().withMethod("GET").withPath("/cgi-bin/department/list")
        ).respond(jsonResponse(
                "{\"errcode\":0,\"errmsg\":\"ok\",\"department\":[{\"id\":2,\"name\":\"研发部\",\"parentid\":1,\"order\":10,\"department_leader\":[\"leader-1\"]}]}"
        ));

        List<WecomDepartmentDTO> departments = wecomContactClient.listDepartments("token-1");

        Assertions.assertEquals(1, departments.size());
        WecomDepartmentDTO department = departments.getFirst();
        Assertions.assertEquals(2L, department.getId());
        Assertions.assertEquals("研发部", department.getName());
        Assertions.assertEquals(1L, department.getParentid());
        Assertions.assertEquals(10L, department.getOrder());
        Assertions.assertEquals(List.of("leader-1"), department.getDepartmentLeader());
    }

    @Test
    void listDepartmentUsers_fetchChildParseCorrectly() {
        mockGetToken("token-1", 7200);
        mockServer.when(
                request().withMethod("GET").withPath("/cgi-bin/user/list")
                        .withQueryStringParameter("department_id", "2")
                        .withQueryStringParameter("fetch_child", "1")
        ).respond(jsonResponse(
                "{\"errcode\":0,\"errmsg\":\"ok\",\"userlist\":[{\"userid\":\"zhangsan\",\"name\":\"张三\",\"mobile\":\"13800000000\",\"email\":\"zhangsan@example.com\",\"position\":\"工程师\",\"department\":[2,3],\"status\":1}]}"
        ));

        List<WecomUserDTO> users = wecomContactClient.listDepartmentUsers("token-1", 2L, true);

        Assertions.assertEquals(1, users.size());
        WecomUserDTO user = users.getFirst();
        Assertions.assertEquals("zhangsan", user.getUserid());
        Assertions.assertEquals("张三", user.getName());
        Assertions.assertEquals("13800000000", user.getMobile());
        Assertions.assertEquals(List.of(2L, 3L), user.getDepartment());
        Assertions.assertEquals(1, user.getStatus());
    }

    @Test
    void listDepartments_nonZeroErrcodeThrows() {
        mockGetToken("token-1", 7200);
        mockServer.when(
                request().withMethod("GET").withPath("/cgi-bin/department/list")
        ).respond(jsonResponse(
                "{\"errcode\":60003,\"errmsg\":\"invalid department\"}"
        ));

        WecomApiException exception = Assertions.assertThrows(
                WecomApiException.class,
                () -> wecomContactClient.listDepartments("token-1")
        );
        Assertions.assertEquals(60003, exception.getErrcode());
        Assertions.assertTrue(exception.getMessage().contains("invalid department"));
    }

    private void mockGetToken(String token, int expiresIn) {
        mockServer.when(
                request().withMethod("GET").withPath("/cgi-bin/gettoken")
                        .withQueryStringParameter("corpid", CORP_ID)
                        .withQueryStringParameter("corpsecret", CONTACT_SECRET)
        ).respond(jsonResponse(
                "{\"errcode\":0,\"errmsg\":\"ok\",\"access_token\":\"" + token + "\",\"expires_in\":" + expiresIn + "}"
        ));
    }

    private void mockGetTokenOnce(String token, int expiresIn) {
        mockServer.when(
                request().withMethod("GET").withPath("/cgi-bin/gettoken")
                        .withQueryStringParameter("corpid", CORP_ID)
                        .withQueryStringParameter("corpsecret", CONTACT_SECRET),
                Times.once()
        ).respond(jsonResponse(
                "{\"errcode\":0,\"errmsg\":\"ok\",\"access_token\":\"" + token + "\",\"expires_in\":" + expiresIn + "}"
        ));
    }

    private org.mockserver.model.HttpResponse jsonResponse(String body) {
        return response()
                .withStatusCode(200)
                .withHeader("Content-Type", "application/json; charset=utf-8")
                .withBody(body);
    }
}
