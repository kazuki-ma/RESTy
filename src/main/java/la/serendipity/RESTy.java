package la.serendipity;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

import javassist.util.proxy.MethodFilter;
import javassist.util.proxy.ProxyFactory;
import la.serendipity.resty.core.InvocationHandlerImplementation;

public class RESTy {
  public static <T> T matelialize(final Class<T> target) {
    return matelialize("", target);
  }

  public static <T> T matelialize(String urlPrefix, final Class<T> target) {
    if (Modifier.isInterface(target.getModifiers())) {
      return createInstanceFromInterfaceViaJavaLangReflectProxy(target);
    } else {
      return createInstanceFromAbstractViaJavassist(target);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T createInstanceFromInterfaceViaJavaLangReflectProxy(final Class<T> target) {
    return (T) Proxy.newProxyInstance(getContextClassLoader(), new Class<?>[] {target},
        new InvocationHandlerImplementation(target, ""));
  }

  private static <T> T createInstanceFromAbstractViaJavassist(final Class<T> target) {
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
    ((javassist.util.proxy.Proxy) foo).setHandler(new InvocationHandlerImplementation(target, ""));
    return foo;
  }

  private static ClassLoader getContextClassLoader() {
    return Thread.currentThread().getContextClassLoader();
  }
}
