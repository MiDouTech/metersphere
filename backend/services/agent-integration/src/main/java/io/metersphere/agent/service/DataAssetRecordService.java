package io.metersphere.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.sdk.exception.MSException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class DataAssetRecordService {
  private final JdbcTemplate jdbc;
  private final DataAssetService assets;
  private final GlobalAssetAccess access;
  private final ObjectMapper json = DataAssetJson.mapper();
  public DataAssetRecordService(JdbcTemplate jdbc, DataAssetService assets, GlobalAssetAccess access) {
    this.jdbc=jdbc; this.assets=assets; this.access=access;
  }
  private String prepare(String project,String id,String version) {
    var detail=assets.detail(project,id);
    String v=version==null||version.isBlank()?detail.get("currentVersionId").toString():version;
    var rows=jdbc.queryForList("SELECT id FROM data_asset_version WHERE id=? AND asset_id=? FOR UPDATE",v,id);
    if(rows.size()!=1) throw new MSException("数据版本不存在");
    if(jdbc.queryForList("SELECT content_json FROM data_asset_record_projection WHERE version_id=? AND record_index=-1 FOR UPDATE",v).isEmpty()) {
      Object content=assets.content(project,id,v).get("content");
      List<Object> records=project(format(v,detail.get("format").toString()),content);
      if(records.size()>100000)throw new MSException("请选择1至100000条记录");
      List<Object[]> batch=new ArrayList<>();
      Map<String,Integer> occurrences=new HashMap<>();
      for(int i=0;i<records.size();i++) {
        String encoded=encode(records.get(i));
        String hash=hash(encoded);
        String key=hash+":"+occurrences.merge(hash,1,Integer::sum);
        batch.add(new Object[]{v,i,key,encoded});
        if(batch.size()==500) { insertProjection(batch);batch.clear(); }
      }
      insertProjection(batch);
      jdbc.update("INSERT INTO data_asset_record_projection(version_id,record_index,record_key,content_json) VALUES(?,-1,'__ready__',?)",v,Integer.toString(records.size()));
    }
    return v;
  }
  private String format(String version,String fallback) {
    String raw=jdbc.queryForObject("SELECT metadata_json FROM data_asset_version WHERE id=?",String.class,version);
    if(raw==null)return fallback;
    Object metadata=decode(raw);
    return metadata instanceof Map<?,?> map&&map.get("format") instanceof String value?value:fallback;
  }
  private void insertProjection(List<Object[]> batch) {
    if(!batch.isEmpty())jdbc.batchUpdate("INSERT INTO data_asset_record_projection(version_id,record_index,record_key,content_json) VALUES(?,?,?,?)",batch);
  }
  static List<Object> project(String format,Object value) {
    List<Object> result=new ArrayList<>();
    if("TXT".equals(format)) {
      String text=value.toString();
      if(text.isEmpty())return result;
      String[] lines=text.split("\\R",-1);
      int count=lines.length;
      if(count>1&&lines[count-1].isEmpty())count--;
      for(int rowIndex=0;rowIndex<count;rowIndex++) {
        String line=lines[rowIndex];
        List<String> cells=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false,fieldStart=true;
        for(int i=0;i<line.length();i++){char c=line.charAt(i);
          if(c=='"') {
            if(quoted) {if(i+1<line.length()&&line.charAt(i+1)=='"'){cell.append('"');i++;}else quoted=false;}
            else if(fieldStart)quoted=true;
            else cell.append(c);
            fieldStart=false;
          }
          else if((c==','||c=='，')&&!quoted){cells.add(cell.toString());cell.setLength(0);fieldStart=true;}else {cell.append(c);fieldStart=false;}
        }
        if(quoted)throw new MSException("数据内容无效");cells.add(cell.toString());result.add(cells);
      }
    } else if(Set.of("XLS","XLSX").contains(format)) {
      for(Object sheet:(List<?>)((Map<?,?>)value).get("sheets"))result.addAll((List<?>)((Map<?,?>)sheet).get("rows"));
    } else if("CSV".equals(format))result.addAll((List<?>)((Map<?,?>)value).get("rows"));
    else if(value instanceof List<?> list)result.addAll(list);else result.add(value);
    return result;
  }
  private Object decode(String value){try{return json.readValue(value,Object.class);}catch(Exception e){throw new IllegalStateException("Record decoding failed",e);}}
  private String cell(Object value) {
    String text=value==null?"":value instanceof Map<?,?> map&&map.containsKey("value")?Objects.toString(map.get("value"),""):value.toString();
    return text.isEmpty()||text.indexOf(',')>=0||text.indexOf('，')>=0||text.indexOf('\n')>=0||text.indexOf('\r')>=0||text.indexOf('"')>=0 ? "\""+text.replace("\"","\"\"")+"\"":text;
  }
  private Object canonical(Object value) {
    if(value instanceof Map<?,?> map) {Map<String,Object> sorted=new TreeMap<>();map.forEach((k,v)->sorted.put(k.toString(),canonical(v)));return sorted;}
    if(value instanceof List<?> list)return list.stream().map(this::canonical).toList();
    return value;
  }
  private String encode(Object value) { try {return json.writeValueAsString(canonical(value));} catch(Exception e) {throw new IllegalStateException("Record encoding failed",e);} }
  private static String hash(String value) {try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("SHA-256 unavailable");}}
  public Map<String,Object> page(String project,String id,String version,int page,int size) {
    if(page<1||size<1||size>100) throw new MSException("记录分页参数无效");
    String v=prepare(project,id,version);
    var rows=jdbc.queryForList("SELECT p.record_key AS recordKey,p.record_index AS recordIndex,p.content_json AS content,u.used_time AS usedTime FROM data_asset_record_projection p LEFT JOIN data_asset_record_usage u ON u.asset_id=? AND u.record_key=p.record_key WHERE p.version_id=? AND p.record_index>=0 ORDER BY p.record_index LIMIT ? OFFSET ? FOR UPDATE",id,v,size,(long)(page-1)*size);
    for(var row:rows) {try {row.put("content",json.readValue(row.get("content").toString(),Object.class));}catch(Exception e){throw new IllegalStateException("Record decoding failed",e);} row.put("used",row.get("usedTime")!=null);}
    return Map.of("versionId",v,"items",rows,"total",jdbc.queryForObject("SELECT CAST(content_json AS SIGNED) FROM data_asset_record_projection WHERE version_id=? AND record_index=-1 FOR UPDATE",Long.class,v),"page",page,"pageSize",size);
  }
  private List<Map<String,Object>> selected(String v,List<String> keys) {
    if(keys==null||keys.isEmpty()||keys.size()>100000||new HashSet<>(keys).size()!=keys.size()||keys.stream().anyMatch(key->key==null||key.length()>80||!key.matches("[0-9a-f]{64}:[1-9][0-9]{0,5}"))) throw new MSException("请选择1至100000条记录");
    List<Map<String,Object>> result=new ArrayList<>();
    for(int start=0;start<keys.size();start+=500) {
      var chunk=keys.subList(start,Math.min(start+500,keys.size()));List<Object> args=new ArrayList<>();args.add(v);args.addAll(chunk);
      result.addAll(jdbc.queryForList("SELECT record_key,record_index,content_json FROM data_asset_record_projection WHERE version_id=? AND record_index>=0 AND record_key IN ("+String.join(",",Collections.nCopies(chunk.size(),"?"))+") FOR UPDATE",args.toArray()));
    }
    if(result.size()!=keys.size())throw new MSException("记录不存在或不属于该版本");
    result.sort(Comparator.comparingLong(row->((Number)row.get("record_index")).longValue()));
    return result;
  }
  public Map<String,Object> copy(String project,String id,String version,List<String> keys) {
    String v=prepare(project,id,version);
    var rows=selected(v,keys);
    String format=format(v,assets.detail(project,id).get("format").toString());
    List<String> lines=new ArrayList<>();long bytes=0;
    for(var row:rows){Object value=decode(row.get("content_json").toString());String line;
      if(!"JSON".equals(format)&&value instanceof List<?> cells)line=String.join(",",cells.stream().map(this::cell).toList());
      else line=value instanceof String text?text:encode(value);
      bytes+=line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+1;
      if(bytes>DataAssetLimits.MAX_CONTENT_BYTES)throw new MSException("复制内容超出安全容量，请减少选择条目");lines.add(line);
    }
    return Map.of("versionId",v,"content",String.join("\n",lines));
  }
  public Map<String,Object> used(String project,String id,String version,List<String> keys,String requestId) {
    if(requestId==null||requestId.isBlank()||requestId.length()>128)throw new MSException("操作请求标识无效");
    var scope=access.authorize(project,"TEST_DATA_ASSET:READ");
    String v=prepare(project,id,version);
    var rows=selected(v,keys);
    String fingerprint=hash(encode(List.of(id,v,keys)));
    jdbc.update("INSERT IGNORE INTO data_asset_record_request(organization_id,actor_id,request_id,request_hash) VALUES(?,?,?,?)",scope.organizationId(),scope.actorId(),requestId,fingerprint);
    var previous=jdbc.queryForList("SELECT request_hash FROM data_asset_record_request WHERE organization_id=? AND actor_id=? AND request_id=? FOR UPDATE",scope.organizationId(),scope.actorId(),requestId);
    if(!previous.isEmpty()&&!fingerprint.equals(previous.getFirst().get("request_hash")))throw new MSException("操作请求标识已用于其他内容");
    long now=System.currentTimeMillis();
    for(int start=0;start<rows.size();start+=500){List<Object[]> batch=new ArrayList<>();
      for(var row:rows.subList(start,Math.min(start+500,rows.size())))batch.add(new Object[]{id,row.get("record_key"),now,scope.actorId()});
      jdbc.batchUpdate("INSERT IGNORE INTO data_asset_record_usage(asset_id,record_key,used_time,actor_id) VALUES(?,?,?,?)",batch);
    }
    return Map.of("versionId",v,"used",true,"requestId",requestId);
  }
}
