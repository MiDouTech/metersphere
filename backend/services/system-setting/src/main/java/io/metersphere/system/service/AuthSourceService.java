package io.metersphere.system.service;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.BeanUtils;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.AuthSource;
import io.metersphere.system.domain.AuthSourceExample;
import io.metersphere.system.dto.AuthSourceDTO;
import io.metersphere.system.dto.request.UpdateAuthStatusRequest;
import io.metersphere.system.dto.sdk.BasePageRequest;
import io.metersphere.system.mapper.AuthSourceMapper;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.naming.Context;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 社区版认证源管理（替代企业版 xpack 缺失接口）
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class AuthSourceService {

    @Resource
    private AuthSourceMapper authSourceMapper;

    public List<AuthSourceDTO> list(BasePageRequest request) {
        AuthSourceExample example = new AuthSourceExample();
        example.setOrderByClause("create_time desc");
        if (StringUtils.isNotBlank(request.getKeyword())) {
            example.createCriteria().andNameLike("%" + request.getKeyword() + "%");
        }
        return authSourceMapper.selectByExampleWithBLOBs(example).stream().map(this::toDTO).collect(Collectors.toList());
    }

    public AuthSourceDTO get(String id) {
        AuthSource source = authSourceMapper.selectByPrimaryKey(id);
        if (source == null) {
            throw new MSException("认证源不存在");
        }
        return toDTO(source);
    }

    public AuthSourceDTO getByType(String type) {
        AuthSourceExample example = new AuthSourceExample();
        example.createCriteria().andTypeEqualTo(type);
        List<AuthSource> list = authSourceMapper.selectByExampleWithBLOBs(example);
        if (list.isEmpty()) {
            return null;
        }
        return toDTO(list.getFirst());
    }

    public void add(AuthSourceDTO request) {
        AuthSource record = fromDTO(request);
        record.setId(IDGenerator.nextStr());
        long now = System.currentTimeMillis();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        if (record.getEnable() == null) {
            record.setEnable(false);
        }
        authSourceMapper.insert(record);
    }

    public void update(AuthSourceDTO request) {
        AuthSource exist = authSourceMapper.selectByPrimaryKey(request.getId());
        if (exist == null) {
            throw new MSException("认证源不存在");
        }
        AuthSource record = fromDTO(request);
        record.setUpdateTime(System.currentTimeMillis());
        record.setCreateTime(exist.getCreateTime());
        authSourceMapper.updateByPrimaryKeyWithBLOBs(record);
    }

    public void updateStatus(UpdateAuthStatusRequest request) {
        AuthSource exist = authSourceMapper.selectByPrimaryKey(request.getId());
        if (exist == null) {
            throw new MSException("认证源不存在");
        }
        AuthSource update = new AuthSource();
        update.setId(request.getId());
        update.setEnable(BooleanUtils.isTrue(request.getEnable()));
        update.setUpdateTime(System.currentTimeMillis());
        authSourceMapper.updateByPrimaryKeySelective(update);
    }

    public void delete(String id) {
        authSourceMapper.deleteByPrimaryKey(id);
    }

    public List<String> listEnabledAuthTypes() {
        AuthSourceExample example = new AuthSourceExample();
        example.createCriteria().andEnableEqualTo(true);
        List<String> types = authSourceMapper.selectByExample(example).stream()
                .map(AuthSource::getType)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        if (!types.contains("LOCAL")) {
            types.addFirst("LOCAL");
        }
        return types;
    }

    public List<AuthSourceDTO> listEnabled() {
        AuthSourceExample example = new AuthSourceExample();
        example.createCriteria().andEnableEqualTo(true);
        example.setOrderByClause("create_time desc");
        return authSourceMapper.selectByExampleWithBLOBs(example).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public void testLdapConnect(Map<String, String> config) {
        String url = config.get("ldapUrl");
        String dn = config.get("ldapDn");
        String password = config.get("ldapPassword");
        if (StringUtils.isAnyBlank(url, dn)) {
            throw new MSException("LDAP Url / DN 不能为空");
        }
        bindLdap(url, dn, password);
    }

    public void testLdapLogin(Map<String, String> config) {
        String url = config.get("ldapUrl");
        String userFilter = config.get("ldapUserFilter");
        String username = config.get("username");
        String password = config.get("password");
        if (StringUtils.isAnyBlank(url, username, password)) {
            throw new MSException("LDAP 登录测试参数不完整");
        }
        String userDn = StringUtils.isNotBlank(userFilter)
                ? userFilter.replace("{0}", username).replace("{uid}", username)
                : username;
        bindLdap(url, userDn, password);
    }

    /**
     * 使用已启用的 LDAP 认证源校验账号，供 /ldap/login 调用
     */
    public void authenticateLdapLogin(String username, String password) {
        AuthSourceDTO ldap = getByType("LDAP");
        if (ldap == null || !BooleanUtils.isTrue(ldap.getEnable())) {
            throw new MSException("LDAP 认证源未配置或未启用");
        }
        if (StringUtils.isAnyBlank(username, password)) {
            throw new MSException("LDAP 用户名或密码不能为空");
        }
        Map<String, Object> config = JSON.parseMap(ldap.getConfiguration());
        String url = valueAsString(config.get("ldapUrl"));
        String userFilter = valueAsString(config.get("ldapUserFilter"));
        String userOu = valueAsString(config.get("ldapUserOu"));
        if (StringUtils.isBlank(url)) {
            throw new MSException("LDAP Url 不能为空");
        }
        String userDn;
        if (StringUtils.isNotBlank(userFilter)) {
            userDn = userFilter.replace("{0}", username).replace("{uid}", username);
            if (StringUtils.isNotBlank(userOu) && !StringUtils.containsIgnoreCase(userDn, userOu)) {
                userDn = userDn + "," + userOu;
            }
        } else if (StringUtils.isNotBlank(userOu)) {
            userDn = "uid=" + username + "," + userOu;
        } else {
            userDn = username;
        }
        bindLdap(url, userDn, password);
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void bindLdap(String url, String dn, String password) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, url);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, dn);
        env.put(Context.SECURITY_CREDENTIALS, password == null ? "" : password);
        try {
            DirContext ctx = new InitialDirContext(env);
            ctx.close();
        } catch (Exception e) {
            throw new MSException("LDAP 连接失败: " + e.getMessage());
        }
    }

    private AuthSourceDTO toDTO(AuthSource source) {
        AuthSourceDTO dto = new AuthSourceDTO();
        BeanUtils.copyBean(dto, source, "configuration");
        if (source.getConfiguration() != null) {
            dto.setConfiguration(new String(source.getConfiguration(), StandardCharsets.UTF_8));
        }
        return dto;
    }

    private AuthSource fromDTO(AuthSourceDTO request) {
        AuthSource record = new AuthSource();
        BeanUtils.copyBean(record, request, "configuration");
        if (request.getConfiguration() != null) {
            record.setConfiguration(request.getConfiguration().getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }
}
