package io.ecosync.database;
import java.util.*;
public interface DatabaseGateway {
 Map<String,Object> inspect(String assertion);
 Map<String,Object> call(String assertion,String tool,Map<String,Object> arguments);
 List<Map<String,Object>> tools(String assertion);
 void retire(UUID org,UUID connector);
 void cancel(UUID execution);
 void reconcile();
}
