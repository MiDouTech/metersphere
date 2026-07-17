package io.metersphere.system.service;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.PlatformSource;
import io.metersphere.system.domain.PlatformSourceExample;
import io.metersphere.system.dto.DingTalkInfoDTO;
import io.metersphere.system.dto.LarkInfoDTO;
import io.metersphere.system.dto.PlatformSourceDTO;
import io.metersphere.system.dto.WeComInfoDTO;
import io.metersphere.system.dto.request.EnableEditorRequest;
import io.metersphere.system.dto.sdk.OptionDTO;
import io.metersphere.system.mapper.PlatformSourceMapper;
import io.metersphere.system.service.wecom.WecomContactClient;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 社区版扫码登录平台配置（替代企业版 xpack 中缺失的 PlatformSource 接口）
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class PlatformSourceService {

    public static final String WE_COM = "WE_COM";
    public static final String DING_TALK = "DING_TALK";
    public static final String LARK = "LARK";
    public static final String LARK_SUITE = "LARK_SUITE";

    private static final String[] PLATFORMS = {WE_COM, DING_TALK, LARK, LARK_SUITE};

    @Resource
    private PlatformSourceMapper platformSourceMapper;
    @Resource
    private SystemParameterService systemParameterService;
    @Resource
    private WecomContactClient wecomContactClient;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<PlatformSourceDTO> listPlatformInfo() {
        Map<String, PlatformSource> map = loadAllAsMap();
        List<PlatformSourceDTO> result = new ArrayList<>();
        for (String platform : PLATFORMS) {
            PlatformSourceDTO dto = new PlatformSourceDTO();
            dto.setPlatform(platform);
            PlatformSource source = map.get(platform);
            if (source == null) {
                dto.setEnable(false);
                dto.setValid(false);
                dto.setHasConfig(false);
            } else {
                dto.setEnable(BooleanUtils.isTrue(source.getEnable()));
                dto.setValid(BooleanUtils.isTrue(source.getValid()));
                dto.setHasConfig(source.getConfig() != null && source.getConfig().length > 0);
            }
            result.add(dto);
        }
        return result;
    }

    public List<OptionDTO> listEnabledPlatformOptions() {
        List<OptionDTO> options = new ArrayList<>();
        for (PlatformSourceDTO item : listPlatformInfo()) {
            if (BooleanUtils.isTrue(item.getEnable()) && BooleanUtils.isTrue(item.getValid()) && BooleanUtils.isTrue(item.getHasConfig())) {
                options.add(new OptionDTO(item.getPlatform(), item.getPlatform()));
            }
        }
        return options;
    }

    public WeComInfoDTO getWeComDetail() {
        return getDetail(WE_COM, WeComInfoDTO.class, new WeComInfoDTO());
    }

    public DingTalkInfoDTO getDingTalkDetail() {
        return getDetail(DING_TALK, DingTalkInfoDTO.class, new DingTalkInfoDTO());
    }

    public LarkInfoDTO getLarkDetail() {
        return getDetail(LARK, LarkInfoDTO.class, new LarkInfoDTO());
    }

    public LarkInfoDTO getLarkSuiteDetail() {
        return getDetail(LARK_SUITE, LarkInfoDTO.class, new LarkInfoDTO());
    }

    public void saveWeCom(WeComInfoDTO request) {
        request.setCallBack(resolveCallBack(WE_COM, request.getCallBack()));
        save(WE_COM, request, request.getEnable(), request.getValid());
    }

    public void saveDingTalk(DingTalkInfoDTO request) {
        request.setCallBack(resolveCallBack(DING_TALK, request.getCallBack()));
        save(DING_TALK, request, request.getEnable(), request.getValid());
    }

    public void saveLark(LarkInfoDTO request) {
        request.setCallBack(resolveCallBack(LARK, request.getCallBack()));
        save(LARK, request, request.getEnable(), request.getValid());
    }

    public void saveLarkSuite(LarkInfoDTO request) {
        request.setCallBack(resolveCallBack(LARK_SUITE, request.getCallBack()));
        save(LARK_SUITE, request, request.getEnable(), request.getValid());
    }

    public void enable(String platform, EnableEditorRequest request) {
        PlatformSource source = required(platform);
        source.setEnable(BooleanUtils.isTrue(request.getEnable()));
        platformSourceMapper.updateByPrimaryKeySelective(source);
    }

    public void invalidate(String platform) {
        PlatformSource source = platformSourceMapper.selectByPrimaryKey(platform);
        if (source == null) {
            return;
        }
        source.setValid(false);
        source.setEnable(false);
        platformSourceMapper.updateByPrimaryKeySelective(source);
    }

    public void validateWeCom(WeComInfoDTO request) {
        WeComInfoDTO config = mergeDetail(request, getWeComDetail());
        if (StringUtils.isAnyBlank(config.getCorpId(), config.getAppSecret())) {
            throw new MSException("企业微信 CorpId / AppSecret 不能为空");
        }
        wecomContactClient.getAccessToken(config.getCorpId(), config.getAppSecret());
        config.setValid(true);
        saveWeCom(config);
    }

    public void validateDingTalk(DingTalkInfoDTO request) {
        DingTalkInfoDTO config = mergeDetail(request, getDingTalkDetail());
        if (StringUtils.isAnyBlank(config.getAppKey(), config.getAppSecret())) {
            throw new MSException("钉钉 AppKey / AppSecret 不能为空");
        }
        String url = "https://oapi.dingtalk.com/gettoken?appkey=" + config.getAppKey()
                + "&appsecret=" + config.getAppSecret();
        Map<?, ?> body = restTemplate.getForObject(url, Map.class);
        if (body == null || asInt(body.get("errcode")) != 0) {
            throw new MSException("钉钉连接校验失败: " + (body == null ? "empty response" : body.get("errmsg")));
        }
        config.setValid(true);
        saveDingTalk(config);
    }

    public void validateLark(LarkInfoDTO request, String platform) {
        LarkInfoDTO saved = LARK_SUITE.equals(platform) ? getLarkSuiteDetail() : getLarkDetail();
        LarkInfoDTO config = mergeDetail(request, saved);
        if (StringUtils.isAnyBlank(config.getAgentId(), config.getAppSecret())) {
            throw new MSException("飞书 AppId / AppSecret 不能为空");
        }
        String host = LARK_SUITE.equals(platform) ? "https://open.larksuite.com" : "https://open.feishu.cn";
        Map<String, String> payload = new HashMap<>();
        payload.put("app_id", config.getAgentId());
        payload.put("app_secret", config.getAppSecret());
        ResponseEntity<Map> response = restTemplate.postForEntity(host + "/open-apis/auth/v3/tenant_access_token/internal",
                payload, Map.class);
        Map body = response.getBody();
        if (body == null || asInt(body.get("code")) != 0) {
            throw new MSException("飞书连接校验失败: " + (body == null ? "empty response" : body.get("msg")));
        }
        config.setValid(true);
        if (LARK_SUITE.equals(platform)) {
            saveLarkSuite(config);
        } else {
            saveLark(config);
        }
    }

    public WeComInfoDTO getWeComLoginInfo() {
        WeComInfoDTO detail = getWeComDetail();
        if (!isLoginReady(WE_COM)) {
            return new WeComInfoDTO();
        }
        WeComInfoDTO info = new WeComInfoDTO();
        info.setCorpId(detail.getCorpId());
        info.setAgentId(detail.getAgentId());
        info.setCallBack(detail.getCallBack());
        info.setState("fit2cloud-wecom-qr");
        return info;
    }

    public DingTalkInfoDTO getDingTalkLoginInfo() {
        DingTalkInfoDTO detail = getDingTalkDetail();
        if (!isLoginReady(DING_TALK)) {
            return new DingTalkInfoDTO();
        }
        DingTalkInfoDTO info = new DingTalkInfoDTO();
        info.setAppKey(detail.getAppKey());
        info.setCallBack(detail.getCallBack());
        info.setState("fit2cloud-dingtalk-qr");
        return info;
    }

    public LarkInfoDTO getLarkLoginInfo(String platform) {
        LarkInfoDTO detail = LARK_SUITE.equals(platform) ? getLarkSuiteDetail() : getLarkDetail();
        if (!isLoginReady(platform)) {
            return new LarkInfoDTO();
        }
        LarkInfoDTO info = new LarkInfoDTO();
        info.setAgentId(detail.getAgentId());
        info.setCallBack(detail.getCallBack());
        info.setState(LARK_SUITE.equals(platform) ? "fit2cloud-larksuite-qr" : "fit2cloud-lark-qr");
        return info;
    }

    public void rejectSsoCallback(String platform) {
        throw new MSException("扫码登录回调暂未启用完整 SSO 换票，请先完成平台配置或使用账号密码登录。platform=" + platform);
    }

    private boolean isLoginReady(String platform) {
        PlatformSource source = platformSourceMapper.selectByPrimaryKey(platform);
        return source != null && BooleanUtils.isTrue(source.getEnable()) && BooleanUtils.isTrue(source.getValid());
    }

    private <T> T getDetail(String platform, Class<T> clazz, T empty) {
        PlatformSource source = platformSourceMapper.selectByPrimaryKey(platform);
        if (source == null || source.getConfig() == null || source.getConfig().length == 0) {
            return empty;
        }
        T detail = JSON.parseObject(new String(source.getConfig(), StandardCharsets.UTF_8), clazz);
        if (detail instanceof WeComInfoDTO dto) {
            dto.setEnable(source.getEnable());
            dto.setValid(source.getValid());
            if (StringUtils.isBlank(dto.getCallBack())) {
                dto.setCallBack(resolveCallBack(platform, null));
            }
        } else if (detail instanceof DingTalkInfoDTO dto) {
            dto.setEnable(source.getEnable());
            dto.setValid(source.getValid());
            if (StringUtils.isBlank(dto.getCallBack())) {
                dto.setCallBack(resolveCallBack(platform, null));
            }
        } else if (detail instanceof LarkInfoDTO dto) {
            dto.setEnable(source.getEnable());
            dto.setValid(source.getValid());
            if (StringUtils.isBlank(dto.getCallBack())) {
                dto.setCallBack(resolveCallBack(platform, null));
            }
        }
        return detail;
    }

    private void save(String platform, Object config, Boolean enable, Boolean valid) {
        PlatformSource record = new PlatformSource();
        record.setPlatform(platform);
        record.setEnable(BooleanUtils.isTrue(enable));
        record.setValid(BooleanUtils.isTrue(valid));
        record.setConfig(JSON.toJSONString(config).getBytes(StandardCharsets.UTF_8));
        if (platformSourceMapper.selectByPrimaryKey(platform) == null) {
            platformSourceMapper.insert(record);
        } else {
            platformSourceMapper.updateByPrimaryKeyWithBLOBs(record);
        }
    }

    private PlatformSource required(String platform) {
        PlatformSource source = platformSourceMapper.selectByPrimaryKey(platform);
        if (source == null) {
            throw new MSException("平台尚未配置: " + platform);
        }
        return source;
    }

    private Map<String, PlatformSource> loadAllAsMap() {
        List<PlatformSource> list = platformSourceMapper.selectByExampleWithBLOBs(new PlatformSourceExample());
        Map<String, PlatformSource> map = new HashMap<>();
        for (PlatformSource item : list) {
            map.put(item.getPlatform(), item);
        }
        return map;
    }

    private String resolveCallBack(String platform, String callBack) {
        if (StringUtils.isNotBlank(callBack)) {
            return callBack;
        }
        String baseUrl = systemParameterService.getBaseInfo().getUrl();
        if (StringUtils.isBlank(baseUrl)) {
            return "/sso/callback/" + platform.toLowerCase();
        }
        return StringUtils.removeEnd(baseUrl, "/") + "/sso/callback/" + platform.toLowerCase();
    }

    @SuppressWarnings("unchecked")
    private <T> T mergeDetail(T request, T saved) {
        if (request == null) {
            return saved;
        }
        Map<String, Object> merged = JSON.parseMap(JSON.toJSONString(saved));
        Map<String, Object> reqMap = JSON.parseMap(JSON.toJSONString(request));
        for (Map.Entry<String, Object> entry : reqMap.entrySet()) {
            if (entry.getValue() != null && !(entry.getValue() instanceof String s && StringUtils.isBlank(s))) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        return (T) JSON.parseObject(JSON.toJSONString(merged), request.getClass());
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return -1;
    }
}
