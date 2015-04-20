package la.serendipity;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;

import javassist.util.proxy.MethodFilter;
import javassist.util.proxy.ProxyFactory;
import la.serendipity.resty.core.InvocationHandlerImplementation;

public class RESTy {
  static private final HttpRequestInitializer noOpInitializer = new HttpRequestInitializer() {
	@Override
	public void initialize(HttpRequest request) throws IOException {
	}
};
  public static <T> T matelialize(final Class<T> target) {
    return matelialize("", noOpInitializer, target);
  }

  public static <T> T matelialize(String urlPrefix, final HttpRequestInitializer httpRequestInitializer, final Class<T> target) {
    if (Modifier.isInterface(target.getModifiers())) {
      return createInstanceFromInterfaceViaJavaLangReflectProxy(urlPrefix, httpRequestInitializer, target);
    } else {
      return createInstanceFromAbstractViaJavassist(urlPrefix, httpRequestInitializer, target);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T createInstanceFromInterfaceViaJavaLangReflectProxy(String urlPrefix, final HttpRequestInitializer httpRequestInitializer, final Class<T> target) {
    return (T) Proxy.newProxyInstance(getContextClassLoader(), new Class<?>[] {target},
        new InvocationHandlerImplementation(urlPrefix, httpRequestInitializer, target));
  }

  private static <T> T createInstanceFromAbstractViaJavassist(String urlPrefix, final HttpRequestInitializer httpRequestInitializer, final Class<T> target) {
    ProxyFactory proxyFactory = new ProxyFactory();
    proxyFactory.setSuperclass(target);
    proxyFactory.setFilter(new MethodFilter() {
      @Override
      public boolean isHandled(Method m) {
        if (Modifier.isAbstract(m.getModifiers())) {
          return true;
        }
        if (m.getName().equals("finalize")) {
          return false;
        }
        return false;
      }
    });

    @SuppressWarnings("unchecked")
    Class<T> generatedClass = proxyFactory.createClass();

    final T foo;
    try {
      foo = generatedClass.newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      throw new IllegalArgumentException(e);
    }
    ((javassist.util.proxy.Proxy) foo).setHandler(new InvocationHandlerImplementation(urlPrefix, httpRequestInitializer, target));
    return foo;
  }

  private static ClassLoader getContextClassLoader() {
    return Thread.currentThread().getContextClassLoader();
  }
}
