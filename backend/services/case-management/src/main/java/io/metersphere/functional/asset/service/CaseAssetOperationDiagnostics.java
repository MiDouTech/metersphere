package io.metersphere.functional.asset.service;
import java.sql.SQLException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/** Emits only bounded diagnostic fields, never exception text or stack traces. */
public final class CaseAssetOperationDiagnostics {
 private static final Logger LOG=LoggerFactory.getLogger(CaseAssetOperationDiagnostics.class);
 private CaseAssetOperationDiagnostics(){}
 public static String record(String phase,String reference,Throwable error){
  String safePhase=phase!=null&&phase.matches("[A-Z_]{1,40}")?phase:"UNKNOWN";
  String ref=reference!=null&&reference.matches("[A-Za-z0-9_-]{1,100}")?reference:UUID.randomUUID().toString();
  var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
  Throwable root=null;SQLException sql=null;boolean network=false,parse=false,file=false,database=false;
  for(Throwable item=error;item!=null&&seen.add(item);item=item.getCause()){
   root=item;if(item instanceof SQLException value)sql=value;
   database|=item instanceof org.springframework.dao.DataAccessException;
   network|=item instanceof java.net.SocketException||item instanceof java.net.SocketTimeoutException||item instanceof java.net.UnknownHostException||item instanceof java.net.http.HttpTimeoutException;
   parse|=item instanceof org.xml.sax.SAXException||item instanceof javax.xml.stream.XMLStreamException||item instanceof com.alibaba.excel.exception.ExcelAnalysisException||item instanceof com.fasterxml.jackson.core.JsonProcessingException||item instanceof java.text.ParseException||item instanceof java.time.format.DateTimeParseException||item instanceof NumberFormatException;
   file|=item instanceof java.io.IOException;
  }
  String category=sql!=null||database?"DATABASE":network?"NETWORK":parse?"PARSE":file?"FILE_STORAGE":"PROCESSING";
  String type=root==null?"Unknown":root.getClass().getSimpleName();if(!type.matches("[A-Za-z0-9_$]{1,100}"))type="UNKNOWN";
  String state=sql==null?"NONE":sql.getSQLState();if(state==null||!state.matches("[A-Z0-9]{5}"))state="UNKNOWN";
  LOG.error("event=asset_operation_failure phase={} ref={} time={} category={} rootType={} sqlState={} vendorCode={}",safePhase,ref,System.currentTimeMillis(),category,type,state,sql==null?0:sql.getErrorCode());
  return "处理失败，请根据错误编号联系管理员。错误编号："+ref;
 }
}
