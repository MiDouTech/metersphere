package io.metersphere.system.domain;

import java.util.ArrayList;
import java.util.List;

public class OrgSyncLogExample {
    protected String orderByClause;

    protected boolean distinct;

    protected List<Criteria> oredCriteria;

    public OrgSyncLogExample() {
        oredCriteria = new ArrayList<Criteria>();
    }

    public void setOrderByClause(String orderByClause) {
        this.orderByClause = orderByClause;
    }

    public String getOrderByClause() {
        return orderByClause;
    }

    public void setDistinct(boolean distinct) {
        this.distinct = distinct;
    }

    public boolean isDistinct() {
        return distinct;
    }

    public List<Criteria> getOredCriteria() {
        return oredCriteria;
    }

    public void or(Criteria criteria) {
        oredCriteria.add(criteria);
    }

    public Criteria or() {
        Criteria criteria = createCriteriaInternal();
        oredCriteria.add(criteria);
        return criteria;
    }

    public Criteria createCriteria() {
        Criteria criteria = createCriteriaInternal();
        if (oredCriteria.size() == 0) {
            oredCriteria.add(criteria);
        }
        return criteria;
    }

    protected Criteria createCriteriaInternal() {
        Criteria criteria = new Criteria();
        return criteria;
    }

    public void clear() {
        oredCriteria.clear();
        orderByClause = null;
        distinct = false;
    }

    protected abstract static class GeneratedCriteria {
        protected List<Criterion> criteria;

        protected GeneratedCriteria() {
            super();
            criteria = new ArrayList<Criterion>();
        }

        public boolean isValid() {
            return criteria.size() > 0;
        }

        public List<Criterion> getAllCriteria() {
            return criteria;
        }

        public List<Criterion> getCriteria() {
            return criteria;
        }

        protected void addCriterion(String condition) {
            if (condition == null) {
                throw new RuntimeException("Value for condition cannot be null");
            }
            criteria.add(new Criterion(condition));
        }

        protected void addCriterion(String condition, Object value, String property) {
            if (value == null) {
                throw new RuntimeException("Value for " + property + " cannot be null");
            }
            criteria.add(new Criterion(condition, value));
        }

        protected void addCriterion(String condition, Object value1, Object value2, String property) {
            if (value1 == null || value2 == null) {
                throw new RuntimeException("Between values for " + property + " cannot be null");
            }
            criteria.add(new Criterion(condition, value1, value2));
        }

        public Criteria andIdIsNull() {
            addCriterion("id is null");
            return (Criteria) this;
        }

        public Criteria andIdIsNotNull() {
            addCriterion("id is not null");
            return (Criteria) this;
        }

        public Criteria andIdEqualTo(String value) {
            addCriterion("id =", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotEqualTo(String value) {
            addCriterion("id <>", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdGreaterThan(String value) {
            addCriterion("id >", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdGreaterThanOrEqualTo(String value) {
            addCriterion("id >=", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdLessThan(String value) {
            addCriterion("id <", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdLessThanOrEqualTo(String value) {
            addCriterion("id <=", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdLike(String value) {
            addCriterion("id like", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotLike(String value) {
            addCriterion("id not like", value, "id");
            return (Criteria) this;
        }

        public Criteria andIdIn(List<String> values) {
            addCriterion("id in", values, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotIn(List<String> values) {
            addCriterion("id not in", values, "id");
            return (Criteria) this;
        }

        public Criteria andIdBetween(String value1, String value2) {
            addCriterion("id between", value1, value2, "id");
            return (Criteria) this;
        }

        public Criteria andIdNotBetween(String value1, String value2) {
            addCriterion("id not between", value1, value2, "id");
            return (Criteria) this;
        }
        public Criteria andOrganizationIdIsNull() {
            addCriterion("organization_id is null");
            return (Criteria) this;
        }

        public Criteria andOrganizationIdIsNotNull() {
            addCriterion("organization_id is not null");
            return (Criteria) this;
        }

        public Criteria andOrganizationIdEqualTo(String value) {
            addCriterion("organization_id =", value, "organizationId");
            return (Criteria) this;
        }

        public Criteria andOrganizationIdNotEqualTo(String value) {
            addCriterion("organization_id <>", value, "organizationId");
            return (Criteria) this;
        }

        public Criteria andOrganizationIdGreaterThan(String value) {
            addCriterion("organization_id >", value, "organizationId");
            return (Criteria) this;
        }

        public Criteria andOrganizationIdGreaterThanOrEqualTo(String value) {
            addCriterion("organization_id >=", value, "organizationId");
            return (Criteria) this;
        }

        public Criteria andOrganizationIdLessThan(String value) {
            addCriterion("organization_id <", value, "organizationId");
            return (Criteria) this;
        }

        public Criteria andOrganizationIdLessThanOrEqualTo(String value) {
            addCriterion("organization_id <=", value, "organizationId");
            return (Criteria) this;
        }

        public Criteria andOrganizationIdLike(String value) {
            addCriterion("organization_id like", value, "organizationId");
            return (Criteria) this;
        }

        public Criteria andOrganizationIdNotLike(String value) {
            addCriterion("organization_id not like", value, "organizationId");
            return (Criteria) this;
        }

        public Criteria andOrganizationIdIn(List<String> values) {
            addCriterion("organization_id in", values, "organizationId");
            return (Criteria) this;
        }

        public Criteria andOrganizationIdNotIn(List<String> values) {
            addCriterion("organization_id not in", values, "organizationId");
            return (Criteria) this;
        }

        public Criteria andOrganizationIdBetween(String value1, String value2) {
            addCriterion("organization_id between", value1, value2, "organizationId");
            return (Criteria) this;
        }

        public Criteria andOrganizationIdNotBetween(String value1, String value2) {
            addCriterion("organization_id not between", value1, value2, "organizationId");
            return (Criteria) this;
        }
        public Criteria andSyncModeIsNull() {
            addCriterion("sync_mode is null");
            return (Criteria) this;
        }

        public Criteria andSyncModeIsNotNull() {
            addCriterion("sync_mode is not null");
            return (Criteria) this;
        }

        public Criteria andSyncModeEqualTo(String value) {
            addCriterion("sync_mode =", value, "syncMode");
            return (Criteria) this;
        }

        public Criteria andSyncModeNotEqualTo(String value) {
            addCriterion("sync_mode <>", value, "syncMode");
            return (Criteria) this;
        }

        public Criteria andSyncModeGreaterThan(String value) {
            addCriterion("sync_mode >", value, "syncMode");
            return (Criteria) this;
        }

        public Criteria andSyncModeGreaterThanOrEqualTo(String value) {
            addCriterion("sync_mode >=", value, "syncMode");
            return (Criteria) this;
        }

        public Criteria andSyncModeLessThan(String value) {
            addCriterion("sync_mode <", value, "syncMode");
            return (Criteria) this;
        }

        public Criteria andSyncModeLessThanOrEqualTo(String value) {
            addCriterion("sync_mode <=", value, "syncMode");
            return (Criteria) this;
        }

        public Criteria andSyncModeLike(String value) {
            addCriterion("sync_mode like", value, "syncMode");
            return (Criteria) this;
        }

        public Criteria andSyncModeNotLike(String value) {
            addCriterion("sync_mode not like", value, "syncMode");
            return (Criteria) this;
        }

        public Criteria andSyncModeIn(List<String> values) {
            addCriterion("sync_mode in", values, "syncMode");
            return (Criteria) this;
        }

        public Criteria andSyncModeNotIn(List<String> values) {
            addCriterion("sync_mode not in", values, "syncMode");
            return (Criteria) this;
        }

        public Criteria andSyncModeBetween(String value1, String value2) {
            addCriterion("sync_mode between", value1, value2, "syncMode");
            return (Criteria) this;
        }

        public Criteria andSyncModeNotBetween(String value1, String value2) {
            addCriterion("sync_mode not between", value1, value2, "syncMode");
            return (Criteria) this;
        }
        public Criteria andSyncStatusIsNull() {
            addCriterion("sync_status is null");
            return (Criteria) this;
        }

        public Criteria andSyncStatusIsNotNull() {
            addCriterion("sync_status is not null");
            return (Criteria) this;
        }

        public Criteria andSyncStatusEqualTo(String value) {
            addCriterion("sync_status =", value, "syncStatus");
            return (Criteria) this;
        }

        public Criteria andSyncStatusNotEqualTo(String value) {
            addCriterion("sync_status <>", value, "syncStatus");
            return (Criteria) this;
        }

        public Criteria andSyncStatusGreaterThan(String value) {
            addCriterion("sync_status >", value, "syncStatus");
            return (Criteria) this;
        }

        public Criteria andSyncStatusGreaterThanOrEqualTo(String value) {
            addCriterion("sync_status >=", value, "syncStatus");
            return (Criteria) this;
        }

        public Criteria andSyncStatusLessThan(String value) {
            addCriterion("sync_status <", value, "syncStatus");
            return (Criteria) this;
        }

        public Criteria andSyncStatusLessThanOrEqualTo(String value) {
            addCriterion("sync_status <=", value, "syncStatus");
            return (Criteria) this;
        }

        public Criteria andSyncStatusLike(String value) {
            addCriterion("sync_status like", value, "syncStatus");
            return (Criteria) this;
        }

        public Criteria andSyncStatusNotLike(String value) {
            addCriterion("sync_status not like", value, "syncStatus");
            return (Criteria) this;
        }

        public Criteria andSyncStatusIn(List<String> values) {
            addCriterion("sync_status in", values, "syncStatus");
            return (Criteria) this;
        }

        public Criteria andSyncStatusNotIn(List<String> values) {
            addCriterion("sync_status not in", values, "syncStatus");
            return (Criteria) this;
        }

        public Criteria andSyncStatusBetween(String value1, String value2) {
            addCriterion("sync_status between", value1, value2, "syncStatus");
            return (Criteria) this;
        }

        public Criteria andSyncStatusNotBetween(String value1, String value2) {
            addCriterion("sync_status not between", value1, value2, "syncStatus");
            return (Criteria) this;
        }
        public Criteria andDeptTotalIsNull() {
            addCriterion("dept_total is null");
            return (Criteria) this;
        }

        public Criteria andDeptTotalIsNotNull() {
            addCriterion("dept_total is not null");
            return (Criteria) this;
        }

        public Criteria andDeptTotalEqualTo(Integer value) {
            addCriterion("dept_total =", value, "deptTotal");
            return (Criteria) this;
        }

        public Criteria andDeptTotalNotEqualTo(Integer value) {
            addCriterion("dept_total <>", value, "deptTotal");
            return (Criteria) this;
        }

        public Criteria andDeptTotalGreaterThan(Integer value) {
            addCriterion("dept_total >", value, "deptTotal");
            return (Criteria) this;
        }

        public Criteria andDeptTotalGreaterThanOrEqualTo(Integer value) {
            addCriterion("dept_total >=", value, "deptTotal");
            return (Criteria) this;
        }

        public Criteria andDeptTotalLessThan(Integer value) {
            addCriterion("dept_total <", value, "deptTotal");
            return (Criteria) this;
        }

        public Criteria andDeptTotalLessThanOrEqualTo(Integer value) {
            addCriterion("dept_total <=", value, "deptTotal");
            return (Criteria) this;
        }

        public Criteria andDeptTotalIn(List<Integer> values) {
            addCriterion("dept_total in", values, "deptTotal");
            return (Criteria) this;
        }

        public Criteria andDeptTotalNotIn(List<Integer> values) {
            addCriterion("dept_total not in", values, "deptTotal");
            return (Criteria) this;
        }

        public Criteria andDeptTotalBetween(Integer value1, Integer value2) {
            addCriterion("dept_total between", value1, value2, "deptTotal");
            return (Criteria) this;
        }

        public Criteria andDeptTotalNotBetween(Integer value1, Integer value2) {
            addCriterion("dept_total not between", value1, value2, "deptTotal");
            return (Criteria) this;
        }
        public Criteria andDeptSuccessIsNull() {
            addCriterion("dept_success is null");
            return (Criteria) this;
        }

        public Criteria andDeptSuccessIsNotNull() {
            addCriterion("dept_success is not null");
            return (Criteria) this;
        }

        public Criteria andDeptSuccessEqualTo(Integer value) {
            addCriterion("dept_success =", value, "deptSuccess");
            return (Criteria) this;
        }

        public Criteria andDeptSuccessNotEqualTo(Integer value) {
            addCriterion("dept_success <>", value, "deptSuccess");
            return (Criteria) this;
        }

        public Criteria andDeptSuccessGreaterThan(Integer value) {
            addCriterion("dept_success >", value, "deptSuccess");
            return (Criteria) this;
        }

        public Criteria andDeptSuccessGreaterThanOrEqualTo(Integer value) {
            addCriterion("dept_success >=", value, "deptSuccess");
            return (Criteria) this;
        }

        public Criteria andDeptSuccessLessThan(Integer value) {
            addCriterion("dept_success <", value, "deptSuccess");
            return (Criteria) this;
        }

        public Criteria andDeptSuccessLessThanOrEqualTo(Integer value) {
            addCriterion("dept_success <=", value, "deptSuccess");
            return (Criteria) this;
        }

        public Criteria andDeptSuccessIn(List<Integer> values) {
            addCriterion("dept_success in", values, "deptSuccess");
            return (Criteria) this;
        }

        public Criteria andDeptSuccessNotIn(List<Integer> values) {
            addCriterion("dept_success not in", values, "deptSuccess");
            return (Criteria) this;
        }

        public Criteria andDeptSuccessBetween(Integer value1, Integer value2) {
            addCriterion("dept_success between", value1, value2, "deptSuccess");
            return (Criteria) this;
        }

        public Criteria andDeptSuccessNotBetween(Integer value1, Integer value2) {
            addCriterion("dept_success not between", value1, value2, "deptSuccess");
            return (Criteria) this;
        }
        public Criteria andDeptFailedIsNull() {
            addCriterion("dept_failed is null");
            return (Criteria) this;
        }

        public Criteria andDeptFailedIsNotNull() {
            addCriterion("dept_failed is not null");
            return (Criteria) this;
        }

        public Criteria andDeptFailedEqualTo(Integer value) {
            addCriterion("dept_failed =", value, "deptFailed");
            return (Criteria) this;
        }

        public Criteria andDeptFailedNotEqualTo(Integer value) {
            addCriterion("dept_failed <>", value, "deptFailed");
            return (Criteria) this;
        }

        public Criteria andDeptFailedGreaterThan(Integer value) {
            addCriterion("dept_failed >", value, "deptFailed");
            return (Criteria) this;
        }

        public Criteria andDeptFailedGreaterThanOrEqualTo(Integer value) {
            addCriterion("dept_failed >=", value, "deptFailed");
            return (Criteria) this;
        }

        public Criteria andDeptFailedLessThan(Integer value) {
            addCriterion("dept_failed <", value, "deptFailed");
            return (Criteria) this;
        }

        public Criteria andDeptFailedLessThanOrEqualTo(Integer value) {
            addCriterion("dept_failed <=", value, "deptFailed");
            return (Criteria) this;
        }

        public Criteria andDeptFailedIn(List<Integer> values) {
            addCriterion("dept_failed in", values, "deptFailed");
            return (Criteria) this;
        }

        public Criteria andDeptFailedNotIn(List<Integer> values) {
            addCriterion("dept_failed not in", values, "deptFailed");
            return (Criteria) this;
        }

        public Criteria andDeptFailedBetween(Integer value1, Integer value2) {
            addCriterion("dept_failed between", value1, value2, "deptFailed");
            return (Criteria) this;
        }

        public Criteria andDeptFailedNotBetween(Integer value1, Integer value2) {
            addCriterion("dept_failed not between", value1, value2, "deptFailed");
            return (Criteria) this;
        }
        public Criteria andUserTotalIsNull() {
            addCriterion("user_total is null");
            return (Criteria) this;
        }

        public Criteria andUserTotalIsNotNull() {
            addCriterion("user_total is not null");
            return (Criteria) this;
        }

        public Criteria andUserTotalEqualTo(Integer value) {
            addCriterion("user_total =", value, "userTotal");
            return (Criteria) this;
        }

        public Criteria andUserTotalNotEqualTo(Integer value) {
            addCriterion("user_total <>", value, "userTotal");
            return (Criteria) this;
        }

        public Criteria andUserTotalGreaterThan(Integer value) {
            addCriterion("user_total >", value, "userTotal");
            return (Criteria) this;
        }

        public Criteria andUserTotalGreaterThanOrEqualTo(Integer value) {
            addCriterion("user_total >=", value, "userTotal");
            return (Criteria) this;
        }

        public Criteria andUserTotalLessThan(Integer value) {
            addCriterion("user_total <", value, "userTotal");
            return (Criteria) this;
        }

        public Criteria andUserTotalLessThanOrEqualTo(Integer value) {
            addCriterion("user_total <=", value, "userTotal");
            return (Criteria) this;
        }

        public Criteria andUserTotalIn(List<Integer> values) {
            addCriterion("user_total in", values, "userTotal");
            return (Criteria) this;
        }

        public Criteria andUserTotalNotIn(List<Integer> values) {
            addCriterion("user_total not in", values, "userTotal");
            return (Criteria) this;
        }

        public Criteria andUserTotalBetween(Integer value1, Integer value2) {
            addCriterion("user_total between", value1, value2, "userTotal");
            return (Criteria) this;
        }

        public Criteria andUserTotalNotBetween(Integer value1, Integer value2) {
            addCriterion("user_total not between", value1, value2, "userTotal");
            return (Criteria) this;
        }
        public Criteria andUserSuccessIsNull() {
            addCriterion("user_success is null");
            return (Criteria) this;
        }

        public Criteria andUserSuccessIsNotNull() {
            addCriterion("user_success is not null");
            return (Criteria) this;
        }

        public Criteria andUserSuccessEqualTo(Integer value) {
            addCriterion("user_success =", value, "userSuccess");
            return (Criteria) this;
        }

        public Criteria andUserSuccessNotEqualTo(Integer value) {
            addCriterion("user_success <>", value, "userSuccess");
            return (Criteria) this;
        }

        public Criteria andUserSuccessGreaterThan(Integer value) {
            addCriterion("user_success >", value, "userSuccess");
            return (Criteria) this;
        }

        public Criteria andUserSuccessGreaterThanOrEqualTo(Integer value) {
            addCriterion("user_success >=", value, "userSuccess");
            return (Criteria) this;
        }

        public Criteria andUserSuccessLessThan(Integer value) {
            addCriterion("user_success <", value, "userSuccess");
            return (Criteria) this;
        }

        public Criteria andUserSuccessLessThanOrEqualTo(Integer value) {
            addCriterion("user_success <=", value, "userSuccess");
            return (Criteria) this;
        }

        public Criteria andUserSuccessIn(List<Integer> values) {
            addCriterion("user_success in", values, "userSuccess");
            return (Criteria) this;
        }

        public Criteria andUserSuccessNotIn(List<Integer> values) {
            addCriterion("user_success not in", values, "userSuccess");
            return (Criteria) this;
        }

        public Criteria andUserSuccessBetween(Integer value1, Integer value2) {
            addCriterion("user_success between", value1, value2, "userSuccess");
            return (Criteria) this;
        }

        public Criteria andUserSuccessNotBetween(Integer value1, Integer value2) {
            addCriterion("user_success not between", value1, value2, "userSuccess");
            return (Criteria) this;
        }
        public Criteria andUserFailedIsNull() {
            addCriterion("user_failed is null");
            return (Criteria) this;
        }

        public Criteria andUserFailedIsNotNull() {
            addCriterion("user_failed is not null");
            return (Criteria) this;
        }

        public Criteria andUserFailedEqualTo(Integer value) {
            addCriterion("user_failed =", value, "userFailed");
            return (Criteria) this;
        }

        public Criteria andUserFailedNotEqualTo(Integer value) {
            addCriterion("user_failed <>", value, "userFailed");
            return (Criteria) this;
        }

        public Criteria andUserFailedGreaterThan(Integer value) {
            addCriterion("user_failed >", value, "userFailed");
            return (Criteria) this;
        }

        public Criteria andUserFailedGreaterThanOrEqualTo(Integer value) {
            addCriterion("user_failed >=", value, "userFailed");
            return (Criteria) this;
        }

        public Criteria andUserFailedLessThan(Integer value) {
            addCriterion("user_failed <", value, "userFailed");
            return (Criteria) this;
        }

        public Criteria andUserFailedLessThanOrEqualTo(Integer value) {
            addCriterion("user_failed <=", value, "userFailed");
            return (Criteria) this;
        }

        public Criteria andUserFailedIn(List<Integer> values) {
            addCriterion("user_failed in", values, "userFailed");
            return (Criteria) this;
        }

        public Criteria andUserFailedNotIn(List<Integer> values) {
            addCriterion("user_failed not in", values, "userFailed");
            return (Criteria) this;
        }

        public Criteria andUserFailedBetween(Integer value1, Integer value2) {
            addCriterion("user_failed between", value1, value2, "userFailed");
            return (Criteria) this;
        }

        public Criteria andUserFailedNotBetween(Integer value1, Integer value2) {
            addCriterion("user_failed not between", value1, value2, "userFailed");
            return (Criteria) this;
        }
        public Criteria andDurationMsIsNull() {
            addCriterion("duration_ms is null");
            return (Criteria) this;
        }

        public Criteria andDurationMsIsNotNull() {
            addCriterion("duration_ms is not null");
            return (Criteria) this;
        }

        public Criteria andDurationMsEqualTo(Long value) {
            addCriterion("duration_ms =", value, "durationMs");
            return (Criteria) this;
        }

        public Criteria andDurationMsNotEqualTo(Long value) {
            addCriterion("duration_ms <>", value, "durationMs");
            return (Criteria) this;
        }

        public Criteria andDurationMsGreaterThan(Long value) {
            addCriterion("duration_ms >", value, "durationMs");
            return (Criteria) this;
        }

        public Criteria andDurationMsGreaterThanOrEqualTo(Long value) {
            addCriterion("duration_ms >=", value, "durationMs");
            return (Criteria) this;
        }

        public Criteria andDurationMsLessThan(Long value) {
            addCriterion("duration_ms <", value, "durationMs");
            return (Criteria) this;
        }

        public Criteria andDurationMsLessThanOrEqualTo(Long value) {
            addCriterion("duration_ms <=", value, "durationMs");
            return (Criteria) this;
        }

        public Criteria andDurationMsIn(List<Long> values) {
            addCriterion("duration_ms in", values, "durationMs");
            return (Criteria) this;
        }

        public Criteria andDurationMsNotIn(List<Long> values) {
            addCriterion("duration_ms not in", values, "durationMs");
            return (Criteria) this;
        }

        public Criteria andDurationMsBetween(Long value1, Long value2) {
            addCriterion("duration_ms between", value1, value2, "durationMs");
            return (Criteria) this;
        }

        public Criteria andDurationMsNotBetween(Long value1, Long value2) {
            addCriterion("duration_ms not between", value1, value2, "durationMs");
            return (Criteria) this;
        }
        public Criteria andErrorMessageIsNull() {
            addCriterion("error_message is null");
            return (Criteria) this;
        }

        public Criteria andErrorMessageIsNotNull() {
            addCriterion("error_message is not null");
            return (Criteria) this;
        }

        public Criteria andErrorMessageEqualTo(String value) {
            addCriterion("error_message =", value, "errorMessage");
            return (Criteria) this;
        }

        public Criteria andErrorMessageNotEqualTo(String value) {
            addCriterion("error_message <>", value, "errorMessage");
            return (Criteria) this;
        }

        public Criteria andErrorMessageGreaterThan(String value) {
            addCriterion("error_message >", value, "errorMessage");
            return (Criteria) this;
        }

        public Criteria andErrorMessageGreaterThanOrEqualTo(String value) {
            addCriterion("error_message >=", value, "errorMessage");
            return (Criteria) this;
        }

        public Criteria andErrorMessageLessThan(String value) {
            addCriterion("error_message <", value, "errorMessage");
            return (Criteria) this;
        }

        public Criteria andErrorMessageLessThanOrEqualTo(String value) {
            addCriterion("error_message <=", value, "errorMessage");
            return (Criteria) this;
        }

        public Criteria andErrorMessageLike(String value) {
            addCriterion("error_message like", value, "errorMessage");
            return (Criteria) this;
        }

        public Criteria andErrorMessageNotLike(String value) {
            addCriterion("error_message not like", value, "errorMessage");
            return (Criteria) this;
        }

        public Criteria andErrorMessageIn(List<String> values) {
            addCriterion("error_message in", values, "errorMessage");
            return (Criteria) this;
        }

        public Criteria andErrorMessageNotIn(List<String> values) {
            addCriterion("error_message not in", values, "errorMessage");
            return (Criteria) this;
        }

        public Criteria andErrorMessageBetween(String value1, String value2) {
            addCriterion("error_message between", value1, value2, "errorMessage");
            return (Criteria) this;
        }

        public Criteria andErrorMessageNotBetween(String value1, String value2) {
            addCriterion("error_message not between", value1, value2, "errorMessage");
            return (Criteria) this;
        }
        public Criteria andCreateTimeIsNull() {
            addCriterion("create_time is null");
            return (Criteria) this;
        }

        public Criteria andCreateTimeIsNotNull() {
            addCriterion("create_time is not null");
            return (Criteria) this;
        }

        public Criteria andCreateTimeEqualTo(Long value) {
            addCriterion("create_time =", value, "createTime");
            return (Criteria) this;
        }

        public Criteria andCreateTimeNotEqualTo(Long value) {
            addCriterion("create_time <>", value, "createTime");
            return (Criteria) this;
        }

        public Criteria andCreateTimeGreaterThan(Long value) {
            addCriterion("create_time >", value, "createTime");
            return (Criteria) this;
        }

        public Criteria andCreateTimeGreaterThanOrEqualTo(Long value) {
            addCriterion("create_time >=", value, "createTime");
            return (Criteria) this;
        }

        public Criteria andCreateTimeLessThan(Long value) {
            addCriterion("create_time <", value, "createTime");
            return (Criteria) this;
        }

        public Criteria andCreateTimeLessThanOrEqualTo(Long value) {
            addCriterion("create_time <=", value, "createTime");
            return (Criteria) this;
        }

        public Criteria andCreateTimeIn(List<Long> values) {
            addCriterion("create_time in", values, "createTime");
            return (Criteria) this;
        }

        public Criteria andCreateTimeNotIn(List<Long> values) {
            addCriterion("create_time not in", values, "createTime");
            return (Criteria) this;
        }

        public Criteria andCreateTimeBetween(Long value1, Long value2) {
            addCriterion("create_time between", value1, value2, "createTime");
            return (Criteria) this;
        }

        public Criteria andCreateTimeNotBetween(Long value1, Long value2) {
            addCriterion("create_time not between", value1, value2, "createTime");
            return (Criteria) this;
        }
        public Criteria andCreateUserIsNull() {
            addCriterion("create_user is null");
            return (Criteria) this;
        }

        public Criteria andCreateUserIsNotNull() {
            addCriterion("create_user is not null");
            return (Criteria) this;
        }

        public Criteria andCreateUserEqualTo(String value) {
            addCriterion("create_user =", value, "createUser");
            return (Criteria) this;
        }

        public Criteria andCreateUserNotEqualTo(String value) {
            addCriterion("create_user <>", value, "createUser");
            return (Criteria) this;
        }

        public Criteria andCreateUserGreaterThan(String value) {
            addCriterion("create_user >", value, "createUser");
            return (Criteria) this;
        }

        public Criteria andCreateUserGreaterThanOrEqualTo(String value) {
            addCriterion("create_user >=", value, "createUser");
            return (Criteria) this;
        }

        public Criteria andCreateUserLessThan(String value) {
            addCriterion("create_user <", value, "createUser");
            return (Criteria) this;
        }

        public Criteria andCreateUserLessThanOrEqualTo(String value) {
            addCriterion("create_user <=", value, "createUser");
            return (Criteria) this;
        }

        public Criteria andCreateUserLike(String value) {
            addCriterion("create_user like", value, "createUser");
            return (Criteria) this;
        }

        public Criteria andCreateUserNotLike(String value) {
            addCriterion("create_user not like", value, "createUser");
            return (Criteria) this;
        }

        public Criteria andCreateUserIn(List<String> values) {
            addCriterion("create_user in", values, "createUser");
            return (Criteria) this;
        }

        public Criteria andCreateUserNotIn(List<String> values) {
            addCriterion("create_user not in", values, "createUser");
            return (Criteria) this;
        }

        public Criteria andCreateUserBetween(String value1, String value2) {
            addCriterion("create_user between", value1, value2, "createUser");
            return (Criteria) this;
        }

        public Criteria andCreateUserNotBetween(String value1, String value2) {
            addCriterion("create_user not between", value1, value2, "createUser");
            return (Criteria) this;
        }

    }

    public static class Criteria extends GeneratedCriteria {

        protected Criteria() {
            super();
        }
    }

    public static class Criterion {
        private String condition;

        private Object value;

        private Object secondValue;

        private boolean noValue;

        private boolean singleValue;

        private boolean betweenValue;

        private boolean listValue;

        private String typeHandler;

        public String getCondition() {
            return condition;
        }

        public Object getValue() {
            return value;
        }

        public Object getSecondValue() {
            return secondValue;
        }

        public boolean isNoValue() {
            return noValue;
        }

        public boolean isSingleValue() {
            return singleValue;
        }

        public boolean isBetweenValue() {
            return betweenValue;
        }

        public boolean isListValue() {
            return listValue;
        }

        public String getTypeHandler() {
            return typeHandler;
        }

        protected Criterion(String condition) {
            super();
            this.condition = condition;
            this.typeHandler = null;
            this.noValue = true;
        }

        protected Criterion(String condition, Object value, String typeHandler) {
            super();
            this.condition = condition;
            this.value = value;
            this.typeHandler = typeHandler;
            if (value instanceof List<?>) {
                this.listValue = true;
            } else {
                this.singleValue = true;
            }
        }

        protected Criterion(String condition, Object value) {
            this(condition, value, null);
        }

        protected Criterion(String condition, Object value, Object secondValue, String typeHandler) {
            super();
            this.condition = condition;
            this.value = value;
            this.secondValue = secondValue;
            this.typeHandler = typeHandler;
            this.betweenValue = true;
        }

        protected Criterion(String condition, Object value, Object secondValue) {
            this(condition, value, secondValue, null);
        }
    }
}
