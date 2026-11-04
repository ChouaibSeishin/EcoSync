package io.ecosync.gateway;
public class GatewayFailure extends RuntimeException {
 public final String code;
 public GatewayFailure(String code){super("Database operation failed");this.code=java.util.Set.of("DB_DISABLED","DB_DESTINATION_DENIED","DB_BUSY","DB_UNAVAILABLE","DB_SCHEMA_CHANGED","DB_AUTHENTICATION_FAILED","DB_ACCESS_DENIED","DB_INVALID_QUERY","DB_LIMIT","DB_CANCELLED","DB_TLS_FAILED").contains(code)?code:"DB_UNAVAILABLE";}
}
