package io.ecosync.rest;
import io.ecosync.restcontract.*;
import java.util.*;
public interface RestGateway {
 Map<String,Object> inspect(String assertion,Map<String,Object> arguments);
 Map<String,Object> call(String assertion,String tool,Map<String,Object> arguments);
 List<Map<String,Object>> tools(String assertion);
 void retire(UUID org,UUID connector);
 void cancel(UUID execution);
 void reconcile();
}
