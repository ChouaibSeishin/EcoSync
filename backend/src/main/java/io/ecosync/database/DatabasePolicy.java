package io.ecosync.database;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
/** Exact operator tuples; the tenant cannot supply JDBC URLs or driver properties. */
@Component @ConditionalOnProperty(name="ecosync.database.enabled",havingValue="true")
public class DatabasePolicy {
 private final Set<String> allowed;private final boolean development;
 public DatabasePolicy(@Value("${ecosync.database.destinations:}")String policy,@Value("${ecosync.database.environment:production}")String environment){allowed=Set.copyOf(Arrays.stream(policy.split(",")).filter(s->!s.isBlank()).toList());development=environment.equals("development");}
 public void check(String type,String host,int port,String database,List<String> schemas,boolean tls){
  if(type==null||!Set.of("POSTGRESQL","MYSQL").contains(type)||host==null||!host.matches("[a-z0-9][a-z0-9.-]{0,252}")||host.endsWith(".")||database==null||!database.matches("[A-Za-z_][A-Za-z0-9_]{0,62}")||schemas==null||schemas.isEmpty()||schemas.size()>4||new HashSet<>(schemas).size()!=schemas.size()||!tls&&!development)throw new DatabaseFailure("DB_DESTINATION_DENIED");
  for(String schema:schemas)if(schema==null||!schema.matches("[A-Za-z_][A-Za-z0-9_]{0,62}")||schema.toLowerCase(Locale.ROOT).startsWith("pg_")||Set.of("information_schema","mysql","performance_schema","sys","app").contains(schema.toLowerCase(Locale.ROOT))||!allowed.contains(type+"|"+host+"|"+port+"|"+database+"|"+schema+"|"+(tls?"tls":"dev")))throw new DatabaseFailure("DB_DESTINATION_DENIED");
 }
}
