import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
/** Runs the same compiled JDBC integration class in Linux when Windows host networking is unreliable. */
public final class LinuxTestRunner {
 public static void main(String[] args){var listener=new SummaryGeneratingListener();var request=LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass("io.ecosync.gateway.DialectIT")).build();LauncherFactory.create().execute(request,listener);var summary=listener.getSummary();summary.printTo(new java.io.PrintWriter(System.out,true));summary.printFailuresTo(new java.io.PrintWriter(System.out,true));System.out.println("GATEWAY_JDBC_TESTS="+summary.getTestsSucceededCount()+"; SKIPPED="+summary.getTestsSkippedCount());if(summary.getTestsFoundCount()!=10||summary.getTestsFailedCount()!=0||summary.getTestsSkippedCount()!=0)System.exit(1);}
}
