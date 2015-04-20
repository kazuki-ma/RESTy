package la.serendipity.resty;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import lombok.Value;

interface JsonTestCom {
  @Value
  static public class IpEcho {
    final String ip;
  }

  @Path("http://ip.jsontest.com")
  static interface Ip {
    @GET
    @Path("/")
    public IpEcho get();
  }

  @Value
  static public class MD5Result {
    final String md5;
    final String original;
  }

  @Path("http://md5.jsontest.com")
  static abstract class MD5 {
    /**
     * @param text Source string for MD5 digest.
     */
    @GET
    @Path("/")
    public abstract MD5Result calculate(@QueryParam("text") String text);

    /**
     * We can use abstract class and default method implementation for some operation.
     *
     * @return Only MD5 string of {@link #calculate(String)}
     */
    public String calculateAndReturnMD5Only(String text) {
      MD5Result result = this.calculate(text);
      return result.getMd5();
    }
  }
}
