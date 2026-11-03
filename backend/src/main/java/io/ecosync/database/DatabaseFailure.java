package io.ecosync.database;
public class DatabaseFailure extends RuntimeException {
 private final String code;
 public DatabaseFailure(String code){super("Database operation could not be completed.");this.code=java.util.Set.of("DB_DISABLED","DB_DESTINATION_DENIED","DB_BUSY","DB_UNAVAILABLE","DB_SCHEMA_CHANGED","DB_ACCESS_DENIED","DB_INVALID_QUERY","DB_LIMIT","DB_CANCELLED","DB_TLS_FAILED").contains(code)?code:"DB_UNAVAILABLE";}
 public String code(){return code;}
 public int status(){return switch(code){case "DB_ACCESS_DENIED"->403;case "DB_DISABLED","DB_SCHEMA_CHANGED","DB_CANCELLED"->409;case "DB_BUSY","DB_LIMIT"->429;case "DB_INVALID_QUERY","DB_DESTINATION_DENIED","DB_TLS_FAILED"->422;default->503;};}
}
