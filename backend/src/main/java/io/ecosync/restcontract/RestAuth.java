package io.ecosync.restcontract;
import java.util.*;
public final class RestAuth {
 private RestAuth(){}
 public static void header(String name){if(name==null||!name.matches("[A-Za-z][A-Za-z0-9-]{0,63}")||Set.of("authorization","host","connection","content-length","content-type","transfer-encoding","accept","accept-encoding","cookie","set-cookie","upgrade","te","trailer","expect","proxy-authorization","proxy-authenticate","via","forwarded","range","user-agent").contains(name.toLowerCase(Locale.ROOT))||name.toLowerCase(Locale.ROOT).startsWith("sec-")||name.toLowerCase(Locale.ROOT).startsWith("x-forwarded-"))throw new RestFailure("REST_AUTH_UNSUPPORTED");}
 public static void configuration(String type,String header,String secret){if(type==null||!Set.of("NONE","BEARER","HEADER_API_KEY").contains(type))throw new RestFailure("REST_AUTH_UNSUPPORTED");if(type.equals("HEADER_API_KEY"))header(header);else if(header!=null&&!header.isEmpty())throw new RestFailure("REST_AUTH_UNSUPPORTED");if(type.equals("NONE")){if(secret!=null&&!secret.isEmpty())throw new RestFailure("REST_AUTH_UNSUPPORTED");}else if(secret==null||!secret.matches("[\\x21-\\x7e]{16,512}"))throw new RestFailure("REST_AUTH_UNSUPPORTED");}
 public static boolean compatible(List<String> auth,String type,String header){return auth.contains(type.equals("HEADER_API_KEY")?type+":"+header.toLowerCase(Locale.ROOT):type);}
}
