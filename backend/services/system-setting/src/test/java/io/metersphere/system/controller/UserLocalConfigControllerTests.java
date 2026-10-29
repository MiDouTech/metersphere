package io.metersphere.system.controller;

import io.metersphere.sdk.util.JSON;
import io.metersphere.system.base.BaseTest;
import io.metersphere.system.controller.handler.ResultHolder;
import io.metersphere.system.domain.UserLocalConfig;
import io.metersphere.system.domain.UserLocalConfigExample;
import io.metersphere.system.dto.UserLocalConfigAddRequest;
import io.metersphere.system.dto.UserLocalConfigUpdateRequest;
import io.metersphere.system.log.constants.OperationLogType;
import io.metersphere.system.mapper.UserLocalConfigMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserLocalConfigControllerTests extends BaseTest {

    private final String BASE_URL = "/user/local/config";
    private final String ADD = BASE_URL + "/add";
    private final String UPDATE = BASE_URL + "/update";
    private final String VALIDATE = BASE_URL + "/validate/%s";
    private final String ENABLE = BASE_URL + "/enable/%s";
    private final String DISABLE = BASE_URL + "/disable/%s";
    private final String GET = BASE_URL + "/get";

    @Resource
    private UserLocalConfigMapper userLocalConfigMapper;

    @BeforeEach
    void prepareIndependentConfigs(TestInfo testInfo) throws Exception {
        UserLocalConfigExample example = new UserLocalConfigExample();
        example.createCriteria().andCreateUserEqualTo("admin");
        userLocalConfigMapper.deleteByExample(example);
        if (!testInfo.getTestMethod().orElseThrow().getName().equals("testAdd")) {
            for (String type : List.of("API", "UI")) {
                UserLocalConfigAddRequest request = new UserLocalConfigAddRequest();
                request.setType(type);
                request.setUserUrl("http://127.0.0.1:8080");
                requestPost(ADD, request).andExpect(status().isOk());
            }
        }
    }

    @AfterEach
    void cleanIndependentConfigs() {
        UserLocalConfigExample example = new UserLocalConfigExample();
        example.createCriteria().andCreateUserEqualTo("admin");
        userLocalConfigMapper.deleteByExample(example);
    }

    public static <T> T parseObjectFromMvcResult(MvcResult mvcResult, Class<T> parseClass) {
        try {
            String returnData = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
            ResultHolder resultHolder = JSON.parseObject(returnData, ResultHolder.class);
            //返回请求正常
            Assertions.assertNotNull(resultHolder);
            return JSON.parseObject(JSON.toJSONString(resultHolder.getData()), parseClass);
        } catch (Exception ignore) {
        }
        return null;
    }


    @Test
    @Order(1)
    public void testAdd() throws Exception {
        UserLocalConfigAddRequest request = new UserLocalConfigAddRequest();
        request.setUserUrl("https://www.baidu.com");
        ResultActions resultActions = requestPost(ADD, request);
        UserLocalConfig userLocalConfig = parseObjectFromMvcResult(resultActions.andReturn(), UserLocalConfig.class);
        assert userLocalConfig != null;
        UserLocalConfig config = userLocalConfigMapper.selectByPrimaryKey(userLocalConfig.getId());
        Assertions.assertEquals("https://www.baidu.com", config.getUserUrl());
        //校验日志
        checkLog(userLocalConfig.getId(), OperationLogType.ADD);

        //已经存在
        request.setUserUrl("https://{ip}:{port}}");
        requestPost(ADD, request).andExpect(businessError("当前用户本地配置已存在"));
        request.setType("UI");
        requestPost(ADD, request);
        //url为空
        request.setUserUrl("");
        requestPost(ADD, request).andExpect(status().is4xxClientError());
        //类型为空
        request.setUserUrl("https://www.baidu.com");
        request.setType("");
        requestPost(ADD, request).andExpect(status().is4xxClientError());

    }

    @Test
    @Order(3)
    public void testUpdate() throws Exception {
        UserLocalConfigExample userLocalConfigExample = new UserLocalConfigExample();
        userLocalConfigExample.createCriteria().andCreateUserEqualTo("admin").andTypeEqualTo("API");
        UserLocalConfig userLocalConfig = userLocalConfigMapper.selectByExample(userLocalConfigExample).getFirst();
        UserLocalConfigUpdateRequest request = new UserLocalConfigUpdateRequest();
        request.setId(userLocalConfig.getId());
        request.setUserUrl("https://www.zhihu.com");
        requestPost(UPDATE, request);
        userLocalConfig = userLocalConfigMapper.selectByPrimaryKey(userLocalConfig.getId());
        Assertions.assertEquals(request.getUserUrl(), userLocalConfig.getUserUrl());
        //校验日志
        checkLog(userLocalConfig.getId(), OperationLogType.UPDATE);
        //不存在的
        request.setId(UUID.randomUUID().toString());
        requestPost(UPDATE, request).andExpect(businessError(io.metersphere.sdk.util.Translator.get("current_user_local_config_not_exist")));
        //id为空
        request.setId("");
        request.setUserUrl("https://www.baidu.com");
        requestPost(UPDATE, request).andExpect(status().is4xxClientError());
    }

    @Test
    @Order(4)
    public void testEnable() throws Exception {
        UserLocalConfigExample userLocalConfigExample = new UserLocalConfigExample();
        userLocalConfigExample.createCriteria().andCreateUserEqualTo("admin").andTypeEqualTo("API");
        UserLocalConfig userLocalConfig = userLocalConfigMapper.selectByExample(userLocalConfigExample).getFirst();
        requestGet(String.format(ENABLE, userLocalConfig.getId()));
        userLocalConfig = userLocalConfigMapper.selectByPrimaryKey(userLocalConfig.getId());
        Assertions.assertEquals(true, userLocalConfig.getEnable());
        //校验日志
        checkLog(userLocalConfig.getId(), OperationLogType.UPDATE);
        //不存在的
        requestGet(String.format(ENABLE, UUID.randomUUID().toString())).andExpect(businessError(io.metersphere.sdk.util.Translator.get("current_user_local_config_not_exist")));
    }

    @Test
    @Order(5)
    public void testDisable() throws Exception {
        UserLocalConfigExample userLocalConfigExample = new UserLocalConfigExample();
        userLocalConfigExample.createCriteria().andCreateUserEqualTo("admin").andTypeEqualTo("API");
        UserLocalConfig userLocalConfig = userLocalConfigMapper.selectByExample(userLocalConfigExample).getFirst();
        requestGet(String.format(DISABLE, userLocalConfig.getId()));
        userLocalConfig = userLocalConfigMapper.selectByPrimaryKey(userLocalConfig.getId());
        Assertions.assertEquals(false, userLocalConfig.getEnable());
        //校验日志
        checkLog(userLocalConfig.getId(), OperationLogType.UPDATE);
        //不存在的
        requestGet(String.format(DISABLE, UUID.randomUUID().toString())).andExpect(businessError(io.metersphere.sdk.util.Translator.get("current_user_local_config_not_exist")));
    }

    @Test
    @Order(6)
    public void testGet() throws Exception {
        ResultActions resultActions = requestGet(GET).andExpect(status().isOk());
        List<UserLocalConfig> userLocalConfigs = getResultDataArray(resultActions.andReturn(), UserLocalConfig.class);
        assert userLocalConfigs != null;
        Assertions.assertEquals(2, userLocalConfigs.size());
        Assertions.assertEquals(java.util.Set.of("API", "UI"), userLocalConfigs.stream().map(UserLocalConfig::getType).collect(java.util.stream.Collectors.toSet()));
    }

}
