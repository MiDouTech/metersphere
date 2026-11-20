package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;

/** Only static, user-safe messages are accepted by this application boundary. */
public class BusinessGovernanceException extends MSException {
  private final String code;
  private String rootType;

  public BusinessGovernanceException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }

  public BusinessGovernanceException(String code, String message, Throwable cause) {
    this(code, message);
    this.rootType = cause.getClass().getSimpleName().replaceAll("[^A-Za-z0-9_$]", "");
  }

  public String rootType() {
    return rootType;
  }
}
