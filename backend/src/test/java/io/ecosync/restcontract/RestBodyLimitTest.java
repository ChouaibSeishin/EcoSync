package io.ecosync.restcontract;
import io.ecosync.web.RestBodyLimit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
class RestBodyLimitTest {
 @Test void duplicateTypedArgumentsAreRejectedBeforeMvcOrDispatch()throws Exception{var request=new MockHttpServletRequest("POST","/api/organizations/one/rest-connectors/two/operations/three/invoke");request.setContent("{\"parameters\":{\"limit\":1,\"limit\":2}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));var response=new MockHttpServletResponse();var admitted=new AtomicBoolean();new RestBodyLimit().doFilter(request,response,(r,s)->admitted.set(true));assertThat(response.getStatus()).isEqualTo(400);assertThat(admitted.get()).isFalse();}
 @Test void validEnvelopeIsAvailableToMvcWithoutBeingConsumed()throws Exception{var request=new MockHttpServletRequest("POST","/api/organizations/one/rest-connectors");request.setContent("{\"displayName\":\"example\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));var response=new MockHttpServletResponse();new RestBodyLimit().doFilter(request,response,(r,s)->assertThat(new String(r.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).contains("example"));assertThat(response.getStatus()).isEqualTo(200);}
}
