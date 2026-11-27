package io.metersphere.agent.service;

/** Shared enforced upload/parsed-content limits published to the browser. */
public final class DataAssetLimits {
  public static final int MAX_BYTES = 30 * 1024 * 1024;
  public static final int MAX_CONTENT_BYTES = 63 * 1024 * 1024;
  public static final int MAX_ROWS = 100_000;

  public static final int LEGACY_MAX_VERSIONS = 50;
  public static final long LEGACY_MAX_BYTES = 150L * 1024 * 1024;

  /** Counts logical records without allocating a split array; terminal separators add no record. */
  public static int textRecords(String text) {
    if (text.isEmpty()) return 0;
    int count=0;boolean terminal=false;
    for(int i=0;i<text.length();i++) {
      char c=text.charAt(i);
      boolean separator=c=='\r'||c=='\n'||c=='\u000B'||c=='\f'||c=='\u0085'||c=='\u2028'||c=='\u2029';
      if(separator) {count++;if(c=='\r' && i+1<text.length() && text.charAt(i+1)=='\n')i++;}
      terminal=separator;
    }
    return count+(terminal?0:1);
  }
  public static void checkRecords(Object content, String format) {
    int count="TXT".equals(format) && content instanceof String text ? textRecords(text) : "JSON".equals(format) && content instanceof java.util.List<?> list ? list.size() : 1;
    if(count>MAX_ROWS) throw new io.metersphere.sdk.exception.MSException("数据记录超过100000行限制");
  }

  private DataAssetLimits() {}
}
