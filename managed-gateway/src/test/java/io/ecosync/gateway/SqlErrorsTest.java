package io.ecosync.gateway;
import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class SqlErrorsTest {
 @Test void authenticationPermissionDeadlineAndTlsHaveSafeDistinctClassifications(){assertThat(SqlErrors.code(new SQLException("password=sentinel; internal-host","28P01"))).isEqualTo("DB_AUTHENTICATION_FAILED");assertThat(SqlErrors.code(new SQLException("SELECT secret","42501"))).isEqualTo("DB_ACCESS_DENIED");assertThat(SqlErrors.code(new SQLException("lock timeout","55P03"))).isEqualTo("DB_LIMIT");assertThat(SqlErrors.code(new SQLException("certificate details","08001",new javax.net.ssl.SSLHandshakeException("secret hostname")))).isEqualTo("DB_TLS_FAILED");}
 @Test void wrappedDestinationFailuresStayClosedWithoutSerializingExceptionMessages(){var failure=new SQLException("password=sentinel; jdbc:mysql://internal-host","08001",new java.io.IOException("private message",new GatewayFailure("DB_DESTINATION_DENIED")));assertThat(SqlErrors.code(failure)).isEqualTo("DB_DESTINATION_DENIED");assertThat(SqlErrors.metadata(failure).toString()).doesNotContain("sentinel","jdbc:","internal-host","private message");}
}
