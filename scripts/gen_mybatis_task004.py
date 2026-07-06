#!/usr/bin/env python3
"""Generate MyBatis Example, Mapper interface and XML for task004 tables."""

import os
from pathlib import Path

BASE = Path(__file__).resolve().parents[1]
DOMAIN = BASE / "backend/framework/domain/src/main/java/io/metersphere/system"
MAPPER_DIR = DOMAIN / "mapper"

# (column, java_property, java_type, jdbc_type, delimited)
TABLES = {
    "Department": {
        "table": "department",
        "pk_type": "String",
        "fields": [
            ("id", "id", "String", "VARCHAR", False),
            ("organization_id", "organizationId", "String", "VARCHAR", False),
            ("name", "name", "String", "VARCHAR", True),
            ("parent_id", "parentId", "String", "VARCHAR", False),
            ("wecom_dept_id", "wecomDeptId", "Long", "BIGINT", False),
            ("sort_order", "sortOrder", "Integer", "INTEGER", False),
            ("dept_status", "deptStatus", "Integer", "TINYINT", False),
            ("sync_status", "syncStatus", "Integer", "TINYINT", False),
            ("sync_time", "syncTime", "Long", "BIGINT", False),
            ("leader_wecom_userid", "leaderWecomUserid", "String", "VARCHAR", False),
            ("create_time", "createTime", "Long", "BIGINT", False),
            ("update_time", "updateTime", "Long", "BIGINT", False),
            ("create_user", "createUser", "String", "VARCHAR", False),
            ("update_user", "updateUser", "String", "VARCHAR", False),
        ],
    },
    "OrgWecomSyncConfig": {
        "table": "org_wecom_sync_config",
        "pk_type": "String",
        "fields": [
            ("id", "id", "String", "VARCHAR", False),
            ("organization_id", "organizationId", "String", "VARCHAR", False),
            ("corp_id", "corpId", "String", "VARCHAR", False),
            ("contact_secret", "contactSecret", "String", "VARCHAR", False),
            ("agent_id", "agentId", "String", "VARCHAR", False),
            ("schedule_enabled", "scheduleEnabled", "Integer", "TINYINT", False),
            ("schedule_cron", "scheduleCron", "String", "VARCHAR", False),
            ("retry_times", "retryTimes", "Integer", "INTEGER", False),
            ("last_sync_time", "lastSyncTime", "Long", "BIGINT", False),
            ("create_time", "createTime", "Long", "BIGINT", False),
            ("update_time", "updateTime", "Long", "BIGINT", False),
            ("create_user", "createUser", "String", "VARCHAR", False),
            ("update_user", "updateUser", "String", "VARCHAR", False),
        ],
    },
    "OrgSyncLog": {
        "table": "org_sync_log",
        "pk_type": "String",
        "fields": [
            ("id", "id", "String", "VARCHAR", False),
            ("organization_id", "organizationId", "String", "VARCHAR", False),
            ("sync_mode", "syncMode", "String", "VARCHAR", False),
            ("sync_status", "syncStatus", "String", "VARCHAR", False),
            ("dept_total", "deptTotal", "Integer", "INTEGER", False),
            ("dept_success", "deptSuccess", "Integer", "INTEGER", False),
            ("dept_failed", "deptFailed", "Integer", "INTEGER", False),
            ("user_total", "userTotal", "Integer", "INTEGER", False),
            ("user_success", "userSuccess", "Integer", "INTEGER", False),
            ("user_failed", "userFailed", "Integer", "INTEGER", False),
            ("duration_ms", "durationMs", "Long", "BIGINT", False),
            ("error_message", "errorMessage", "String", "LONGVARCHAR", False),
            ("create_time", "createTime", "Long", "BIGINT", False),
            ("create_user", "createUser", "String", "VARCHAR", False),
        ],
    },
}


def camel_to_pascal(s):
    return s[0].upper() + s[1:] if s else s


def field_criteria(col, prop, java_type):
    lines = []
    pascal = camel_to_pascal(prop)
    if java_type == "String":
        ops = [
            ("IsNull", f'addCriterion("{col} is null");'),
            ("IsNotNull", f'addCriterion("{col} is not null");'),
            ("EqualTo", f'addCriterion("{col} =", value, "{prop}");'),
            ("NotEqualTo", f'addCriterion("{col} <>", value, "{prop}");'),
            ("GreaterThan", f'addCriterion("{col} >", value, "{prop}");'),
            ("GreaterThanOrEqualTo", f'addCriterion("{col} >=", value, "{prop}");'),
            ("LessThan", f'addCriterion("{col} <", value, "{prop}");'),
            ("LessThanOrEqualTo", f'addCriterion("{col} <=", value, "{prop}");'),
            ("Like", f'addCriterion("{col} like", value, "{prop}");'),
            ("NotLike", f'addCriterion("{col} not like", value, "{prop}");'),
            ("In", f'addCriterion("{col} in", values, "{prop}");'),
            ("NotIn", f'addCriterion("{col} not in", values, "{prop}");'),
            ("Between", f'addCriterion("{col} between", value1, value2, "{prop}");'),
            ("NotBetween", f'addCriterion("{col} not between", value1, value2, "{prop}");'),
        ]
        for op, body in ops:
            if op in ("In", "NotIn"):
                sig = f"public Criteria and{pascal}{op}(List<{java_type}> values)"
            elif op in ("Between", "NotBetween"):
                sig = f"public Criteria and{pascal}{op}({java_type} value1, {java_type} value2)"
            else:
                sig = f"public Criteria and{pascal}{op}({java_type} value)" if "Null" not in op else f"public Criteria and{pascal}{op}()"
            lines.append(f"        {sig} {{")
            lines.append(f"            {body}")
            lines.append("            return (Criteria) this;")
            lines.append("        }")
            lines.append("")
    else:
        ops = [
            ("IsNull", f'addCriterion("{col} is null");'),
            ("IsNotNull", f'addCriterion("{col} is not null");'),
            ("EqualTo", f'addCriterion("{col} =", value, "{prop}");'),
            ("NotEqualTo", f'addCriterion("{col} <>", value, "{prop}");'),
            ("GreaterThan", f'addCriterion("{col} >", value, "{prop}");'),
            ("GreaterThanOrEqualTo", f'addCriterion("{col} >=", value, "{prop}");'),
            ("LessThan", f'addCriterion("{col} <", value, "{prop}");'),
            ("LessThanOrEqualTo", f'addCriterion("{col} <=", value, "{prop}");'),
            ("In", f'addCriterion("{col} in", values, "{prop}");'),
            ("NotIn", f'addCriterion("{col} not in", values, "{prop}");'),
            ("Between", f'addCriterion("{col} between", value1, value2, "{prop}");'),
            ("NotBetween", f'addCriterion("{col} not between", value1, value2, "{prop}");'),
        ]
        for op, body in ops:
            if op in ("In", "NotIn"):
                sig = f"public Criteria and{pascal}{op}(List<{java_type}> values)"
            elif op in ("Between", "NotBetween"):
                sig = f"public Criteria and{pascal}{op}({java_type} value1, {java_type} value2)"
            else:
                sig = f"public Criteria and{pascal}{op}({java_type} value)" if "Null" not in op else f"public Criteria and{pascal}{op}()"
            lines.append(f"        {sig} {{")
            lines.append(f"            {body}")
            lines.append("            return (Criteria) this;")
            lines.append("        }")
            lines.append("")
    return "\n".join(lines)


def sql_col(col, delimited):
    return f"`{col}`" if delimited else col


def gen_example(name, fields):
    criteria_blocks = []
    for col, prop, java_type, _, _ in fields:
        criteria_blocks.append(field_criteria(col, prop, java_type))

    return f"""package io.metersphere.system.domain;

import java.util.ArrayList;
import java.util.List;

public class {name}Example {{
    protected String orderByClause;

    protected boolean distinct;

    protected List<Criteria> oredCriteria;

    public {name}Example() {{
        oredCriteria = new ArrayList<Criteria>();
    }}

    public void setOrderByClause(String orderByClause) {{
        this.orderByClause = orderByClause;
    }}

    public String getOrderByClause() {{
        return orderByClause;
    }}

    public void setDistinct(boolean distinct) {{
        this.distinct = distinct;
    }}

    public boolean isDistinct() {{
        return distinct;
    }}

    public List<Criteria> getOredCriteria() {{
        return oredCriteria;
    }}

    public void or(Criteria criteria) {{
        oredCriteria.add(criteria);
    }}

    public Criteria or() {{
        Criteria criteria = createCriteriaInternal();
        oredCriteria.add(criteria);
        return criteria;
    }}

    public Criteria createCriteria() {{
        Criteria criteria = createCriteriaInternal();
        if (oredCriteria.size() == 0) {{
            oredCriteria.add(criteria);
        }}
        return criteria;
    }}

    protected Criteria createCriteriaInternal() {{
        Criteria criteria = new Criteria();
        return criteria;
    }}

    public void clear() {{
        oredCriteria.clear();
        orderByClause = null;
        distinct = false;
    }}

    protected abstract static class GeneratedCriteria {{
        protected List<Criterion> criteria;

        protected GeneratedCriteria() {{
            super();
            criteria = new ArrayList<Criterion>();
        }}

        public boolean isValid() {{
            return criteria.size() > 0;
        }}

        public List<Criterion> getAllCriteria() {{
            return criteria;
        }}

        public List<Criterion> getCriteria() {{
            return criteria;
        }}

        protected void addCriterion(String condition) {{
            if (condition == null) {{
                throw new RuntimeException("Value for condition cannot be null");
            }}
            criteria.add(new Criterion(condition));
        }}

        protected void addCriterion(String condition, Object value, String property) {{
            if (value == null) {{
                throw new RuntimeException("Value for " + property + " cannot be null");
            }}
            criteria.add(new Criterion(condition, value));
        }}

        protected void addCriterion(String condition, Object value1, Object value2, String property) {{
            if (value1 == null || value2 == null) {{
                throw new RuntimeException("Between values for " + property + " cannot be null");
            }}
            criteria.add(new Criterion(condition, value1, value2));
        }}

{"".join(criteria_blocks)}
    }}

    public static class Criteria extends GeneratedCriteria {{

        protected Criteria() {{
            super();
        }}
    }}

    public static class Criterion {{
        private String condition;

        private Object value;

        private Object secondValue;

        private boolean noValue;

        private boolean singleValue;

        private boolean betweenValue;

        private boolean listValue;

        private String typeHandler;

        public String getCondition() {{
            return condition;
        }}

        public Object getValue() {{
            return value;
        }}

        public Object getSecondValue() {{
            return secondValue;
        }}

        public boolean isNoValue() {{
            return noValue;
        }}

        public boolean isSingleValue() {{
            return singleValue;
        }}

        public boolean isBetweenValue() {{
            return betweenValue;
        }}

        public boolean isListValue() {{
            return listValue;
        }}

        public String getTypeHandler() {{
            return typeHandler;
        }}

        protected Criterion(String condition) {{
            super();
            this.condition = condition;
            this.typeHandler = null;
            this.noValue = true;
        }}

        protected Criterion(String condition, Object value, String typeHandler) {{
            super();
            this.condition = condition;
            this.value = value;
            this.typeHandler = typeHandler;
            if (value instanceof List<?>) {{
                this.listValue = true;
            }} else {{
                this.singleValue = true;
            }}
        }}

        protected Criterion(String condition, Object value) {{
            this(condition, value, null);
        }}

        protected Criterion(String condition, Object value, Object secondValue, String typeHandler) {{
            super();
            this.condition = condition;
            this.value = value;
            this.secondValue = secondValue;
            this.typeHandler = typeHandler;
            this.betweenValue = true;
        }}

        protected Criterion(String condition, Object value, Object secondValue) {{
            this(condition, value, secondValue, null);
        }}
    }}
}}
"""


def gen_mapper_java(name, pk_type):
    return f"""package io.metersphere.system.mapper;

import io.metersphere.system.domain.{name};
import io.metersphere.system.domain.{name}Example;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface {name}Mapper {{
    long countByExample({name}Example example);

    int deleteByExample({name}Example example);

    int deleteByPrimaryKey({pk_type} id);

    int insert({name} record);

    int insertSelective({name} record);

    List<{name}> selectByExample({name}Example example);

    {name} selectByPrimaryKey({pk_type} id);

    int updateByExampleSelective(@Param("record") {name} record, @Param("example") {name}Example example);

    int updateByExample(@Param("record") {name} record, @Param("example") {name}Example example);

    int updateByPrimaryKeySelective({name} record);

    int updateByPrimaryKey({name} record);

    int batchInsert(@Param("list") List<{name}> list);

    int batchInsertSelective(@Param("list") List<{name}> list, @Param("selective") {name}.Column ... selective);
}}
"""


def gen_mapper_xml(name, table, fields):
    result_map = ['  <resultMap id="BaseResultMap" type="io.metersphere.system.domain.' + name + '">']
    for col, prop, _, jdbc, _ in fields:
        tag = "id" if col == "id" else "result"
        result_map.append(f'    <{tag} column="{col}" jdbcType="{jdbc}" property="{prop}" />')
    result_map.append("  </resultMap>")

    col_list = ", ".join(sql_col(c, d) for c, _, _, _, d in fields)

    insert_cols = ", ".join(sql_col(c, d) for c, _, _, _, d in fields)
    insert_vals = ", ".join(f"#{{{p},jdbcType={j}}}" for _, p, _, j, _ in fields)

    def trim_block(prefix, accessor):
        parts = []
        for col, prop, _, jdbc, delimited in fields:
            if col == "id" and prefix == "updateByPrimaryKeySelective":
                continue
            col_sql = sql_col(col, delimited)
            parts.append(f"      <if test=\"{accessor}{prop} != null\">\n        {col_sql} = #{{{accessor}{prop},jdbcType={jdbc}}},\n      </if>")
        return "\n".join(parts)

    def trim_insert_block():
        cols, vals = [], []
        for col, prop, _, jdbc, delimited in fields:
            cols.append(f"      <if test=\"{prop} != null\">\n        {sql_col(col, delimited)},\n      </if>")
            vals.append(f"      <if test=\"{prop} != null\">\n        #{{{prop},jdbcType={jdbc}}},\n      </if>")
        return "\n".join(cols), "\n".join(vals)

    insert_cols_trim, insert_vals_trim = trim_insert_block()

    update_all = ",\n      ".join(
        f"{sql_col(c, d)} = #{{record.{p},jdbcType={j}}}" for c, p, _, j, d in fields
    )
    update_pk = ",\n      ".join(
        f"{sql_col(c, d)} = #{{{p},jdbcType={j}}}" for c, p, _, j, d in fields if c != "id"
    )

    batch_vals = ",\n        ".join(f"#{{item.{p},jdbcType={j}}}" for _, p, _, j, _ in fields)

    batch_sel = []
    for col, prop, _, jdbc, _ in fields:
        batch_sel.append(f"""        <if test="'{col}'.toString() == column.value">
          #{{item.{prop},jdbcType={jdbc}}}
        </if>""")

    return f"""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="io.metersphere.system.mapper.{name}Mapper">
{chr(10).join(result_map)}
  <sql id="Example_Where_Clause">
    <where>
      <foreach collection="oredCriteria" item="criteria" separator="or">
        <if test="criteria.valid">
          <trim prefix="(" prefixOverrides="and" suffix=")">
            <foreach collection="criteria.criteria" item="criterion">
              <choose>
                <when test="criterion.noValue">
                  and ${{criterion.condition}}
                </when>
                <when test="criterion.singleValue">
                  and ${{criterion.condition}} #{{criterion.value}}
                </when>
                <when test="criterion.betweenValue">
                  and ${{criterion.condition}} #{{criterion.value}} and #{{criterion.secondValue}}
                </when>
                <when test="criterion.listValue">
                  and ${{criterion.condition}}
                  <foreach close=")" collection="criterion.value" item="listItem" open="(" separator=",">
                    #{{listItem}}
                  </foreach>
                </when>
              </choose>
            </foreach>
          </trim>
        </if>
      </foreach>
    </where>
  </sql>
  <sql id="Update_By_Example_Where_Clause">
    <where>
      <foreach collection="example.oredCriteria" item="criteria" separator="or">
        <if test="criteria.valid">
          <trim prefix="(" prefixOverrides="and" suffix=")">
            <foreach collection="criteria.criteria" item="criterion">
              <choose>
                <when test="criterion.noValue">
                  and ${{criterion.condition}}
                </when>
                <when test="criterion.singleValue">
                  and ${{criterion.condition}} #{{criterion.value}}
                </when>
                <when test="criterion.betweenValue">
                  and ${{criterion.condition}} #{{criterion.value}} and #{{criterion.secondValue}}
                </when>
                <when test="criterion.listValue">
                  and ${{criterion.condition}}
                  <foreach close=")" collection="criterion.value" item="listItem" open="(" separator=",">
                    #{{listItem}}
                  </foreach>
                </when>
              </choose>
            </foreach>
          </trim>
        </if>
      </foreach>
    </where>
  </sql>
  <sql id="Base_Column_List">
    {col_list}
  </sql>
  <select id="selectByExample" parameterType="io.metersphere.system.domain.{name}Example" resultMap="BaseResultMap">
    select
    <if test="distinct">
      distinct
    </if>
    <include refid="Base_Column_List" />
    from {table}
    <if test="_parameter != null">
      <include refid="Example_Where_Clause" />
    </if>
    <if test="orderByClause != null">
      order by ${{orderByClause}}
    </if>
  </select>
  <select id="selectByPrimaryKey" parameterType="java.lang.String" resultMap="BaseResultMap">
    select
    <include refid="Base_Column_List" />
    from {table}
    where id = #{{id,jdbcType=VARCHAR}}
  </select>
  <delete id="deleteByPrimaryKey" parameterType="java.lang.String">
    delete from {table}
    where id = #{{id,jdbcType=VARCHAR}}
  </delete>
  <delete id="deleteByExample" parameterType="io.metersphere.system.domain.{name}Example">
    delete from {table}
    <if test="_parameter != null">
      <include refid="Example_Where_Clause" />
    </if>
  </delete>
  <insert id="insert" parameterType="io.metersphere.system.domain.{name}">
    insert into {table} ({insert_cols})
    values ({insert_vals})
  </insert>
  <insert id="insertSelective" parameterType="io.metersphere.system.domain.{name}">
    insert into {table}
    <trim prefix="(" suffix=")" suffixOverrides=",">
{insert_cols_trim}
    </trim>
    <trim prefix="values (" suffix=")" suffixOverrides=",">
{insert_vals_trim}
    </trim>
  </insert>
  <select id="countByExample" parameterType="io.metersphere.system.domain.{name}Example" resultType="java.lang.Long">
    select count(*) from {table}
    <if test="_parameter != null">
      <include refid="Example_Where_Clause" />
    </if>
  </select>
  <update id="updateByExampleSelective" parameterType="map">
    update {table}
    <set>
{trim_block("updateByExampleSelective", "record.")}
    </set>
    <if test="_parameter != null">
      <include refid="Update_By_Example_Where_Clause" />
    </if>
  </update>
  <update id="updateByExample" parameterType="map">
    update {table}
    set {update_all}
    <if test="_parameter != null">
      <include refid="Update_By_Example_Where_Clause" />
    </if>
  </update>
  <update id="updateByPrimaryKeySelective" parameterType="io.metersphere.system.domain.{name}">
    update {table}
    <set>
{trim_block("updateByPrimaryKeySelective", "")}
    </set>
    where id = #{{id,jdbcType=VARCHAR}}
  </update>
  <update id="updateByPrimaryKey" parameterType="io.metersphere.system.domain.{name}">
    update {table}
    set {update_pk}
    where id = #{{id,jdbcType=VARCHAR}}
  </update>
  <insert id="batchInsert" parameterType="map">
    insert into {table}
    ({insert_cols})
    values
    <foreach collection="list" item="item" separator=",">
      ({batch_vals})
    </foreach>
  </insert>
  <insert id="batchInsertSelective" parameterType="map">
    insert into {table} (
    <foreach collection="selective" item="column" separator=",">
      ${{column.escapedColumnName}}
    </foreach>
    )
    values
    <foreach collection="list" item="item" separator=",">
      (
      <foreach collection="selective" item="column" separator=",">
{chr(10).join(batch_sel)}
      </foreach>
      )
    </foreach>
  </insert>
</mapper>
"""


def gen_user_example_fields():
    fields = [
        ("wecom_userid", "wecomUserid", "String"),
        ("department_id", "departmentId", "String"),
        ("position", "position", "String"),
        ("sync_status", "syncStatus", "Integer"),
        ("sync_time", "syncTime", "Long"),
    ]
    return "\n".join(field_criteria(c, p, t) for c, p, t in fields)


def main():
    for name, cfg in TABLES.items():
        (DOMAIN / "domain" / f"{name}Example.java").write_text(
            gen_example(name, cfg["fields"]), encoding="utf-8"
        )
        (MAPPER_DIR / f"{name}Mapper.java").write_text(
            gen_mapper_java(name, cfg["pk_type"]), encoding="utf-8"
        )
        (MAPPER_DIR / f"{name}Mapper.xml").write_text(
            gen_mapper_xml(name, cfg["table"], cfg["fields"]), encoding="utf-8"
        )
        print(f"Generated {name}")

    # patch UserExample
    user_ex = (DOMAIN / "domain" / "UserExample.java").read_text(encoding="utf-8")
    marker = "        public Criteria andCftTokenNotBetween(String value1, String value2) {"
    idx = user_ex.find(marker)
    if idx == -1:
        raise SystemExit("UserExample marker not found")
    end = user_ex.find("        }", user_ex.find("return (Criteria) this;", idx)) + len("        }")
    insert = "\n" + gen_user_example_fields()
    user_ex = user_ex[:end] + insert + user_ex[end:]
    (DOMAIN / "domain" / "UserExample.java").write_text(user_ex, encoding="utf-8")
    print("Patched UserExample")


if __name__ == "__main__":
    main()
