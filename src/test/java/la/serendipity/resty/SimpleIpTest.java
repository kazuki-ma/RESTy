package la.serendipity.resty;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import la.serendipity.RESTy;


@Slf4j
public class SimpleIpTest {
  @Test
  public void testIp() {
    JsonTestCom.Ip ipEchoInterface = RESTy.matelialize(JsonTestCom.Ip.class);
    JsonTestCom.IpEcho ipEcho = ipEchoInterface.get();
    Assert.assertNotNull("Get IP", ipEcho);

    log.info("My IP is : {}", ipEcho.getIp());
  }
}
