package la.serendipity.resty;

import lombok.extern.slf4j.Slf4j;

import org.junit.Assert;
import org.junit.Test;

import la.serendipity.RESTy;

@Slf4j
public class MD5RemoteCalculateTest {
  /**
   * @see <a href="http://www.wolframalpha.com/input/?i=MD5+of+%22TEST%22">MD5 of "TEST"</a>
   */
  @Test
  public void testMD5() {
    JsonTestCom.MD5 remoteMD5Calculator = RESTy.matelialize(JsonTestCom.MD5.class);

    JsonTestCom.MD5Result md5Result = remoteMD5Calculator.calculate("TEST");
    log.debug("{}", md5Result);

    Assert.assertNotNull("Get MD5 Result", md5Result);
    Assert.assertEquals("Equal echoed original. So we are handling query parameter collectry.",
        "TEST",//
        md5Result.getOriginal());

    Assert.assertEquals("Our result equals Wolfram result",
        "033b d94b 1168 d7e4 f0d6 44c3 c95e 35bf".replaceAll(" ", ""),//
        md5Result.getMd5());
  }
}
