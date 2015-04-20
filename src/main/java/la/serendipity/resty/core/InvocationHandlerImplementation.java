package la.serendipity.resty.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javassist.util.proxy.MethodHandler;

import javax.ws.rs.FormParam;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.http.EmptyContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;

import la.serendipity.resty.annotations.ListType;
import la.serendipity.resty.annotations.MapType;
import la.serendipity.resty.util.JsonListType;
import la.serendipity.resty.util.JsonMapType;
import la.serendipity.resty.util.Parameter;

/**
 * RestMapper core class which invoke REST request dinamically via interface call.
 * 
 * @author Kazuki Matsuda
 */
public class InvocationHandlerImplementation//
    implements InvocationHandler, MethodHandler {
  private final Logger log;
  private static final EmptyContent EMPTY_CONTENT = new EmptyContent();
  private final HttpTransport httpTransport = new NetHttpTransport();
  private final GsonFactory gsonFactory = new GsonFactory();
  private final Gson gson = new Gson();

  private final String urlBase;
  private final HttpRequestInitializer httpRequestInitializer;

  public InvocationHandlerImplementation(String urlBase, HttpRequestInitializer httpRequestInitializer, Class<?> target) {
	log = LoggerFactory.getLogger(target.getCanonicalName() + "(RESTy)");
    Path path = target.getAnnotation(Path.class);
    if (path != null) {
      this.urlBase = urlBase + path.value();
    } else {
      this.urlBase = urlBase;
    }
    this.httpRequestInitializer = httpRequestInitializer;
  }

  @Override
  public Object invoke(final Object proxy, final Method method, final Object[] arguments)
      throws IOException {
    final Path path = method.getAnnotation(Path.class);
    final List<Parameter> parameters = Parameter.getParameters(method, arguments);

    final String httpMethod = resolveMethod(method);

    final Class<?> returnClass = method.getReturnType();
    Type returnType = detectReturnType(method, returnClass);

    final String urlString = urlBase + path.value();

    final GenericUrl url = new GenericUrl(urlString);
    final Map<Object, Object> jsonData = new HashMap<>();

    for (Parameter parameter : parameters) {
      final Object argument = parameter.getArgument();
      if (argument == null) {
        log.debug("Ignore null value parameter = {}", parameter.toString());
        continue;
      }

      if (parameter.getAnnotation(QueryParam.class) != null) {
        url.put(parameter.getAnnotation(QueryParam.class).value(), argument);
      }
      if (parameter.getAnnotation(PathParam.class) != null) {
        final String valueName = parameter.getAnnotation(PathParam.class).value();
        url.setRawPath(url.getRawPath().replace("{" + valueName + "}", argument.toString()));
      }
      if (parameter.getAnnotation(FormParam.class) != null) {
        jsonData.put(parameter.getAnnotation(FormParam.class).value(), argument);
      }
      if (argument instanceof Type) {
        returnType = (Type) argument;
      }
    }

    final HttpRequest request = buildRequest(httpMethod, url, jsonData);

    log.info("REQUEST : {} {} {}", request.getRequestMethod(), request.getUrl(),
        request.getHeaders());
    {
      final Object returnRequest = shouldReturnRequestOrItsRelated(request, returnClass);
      if (returnRequest != null) {
        return returnRequest;
      }
    }
    final HttpResponse res = request.execute();
    log.info("RESPONSE: {} {} {}", res.getStatusCode(), res.getStatusMessage(), res.getHeaders());

    if (HttpResponse.class.isAssignableFrom(returnClass)) {
      return res;
    }
    try {
      if (String.class.isAssignableFrom(returnClass)) {
        return createStringResponse(res);
      }
      if (HttpHeaders.class.isAssignableFrom(returnClass)) {
        return res.getHeaders();
      }
      if (Integer.class.isAssignableFrom(returnClass) || int.class.isAssignableFrom(returnClass)) {
        return res.getStatusCode();
      }
      return createObjectResponse(res, returnType);
    } finally {
      res.disconnect();
    }
  }

  @SuppressWarnings("rawtypes")
  private Type detectReturnType(final Method method, final Class<?> returnClass) {
    if (method.isAnnotationPresent(MapType.class)) {
      final MapType typeAnnotation = method.getAnnotation(MapType.class);
      Class<?> value = typeAnnotation.value();
      Class<? extends Map> implementation = typeAnnotation.implementation();
      Class<? extends Class> implementationClass = implementation.getClass();

      if (implementationClass.isAssignableFrom(returnClass)) {
        return new JsonMapType(value, implementation);
      } else {
        return new JsonMapType(value, returnClass);
      }
    }
    if (method.isAnnotationPresent(ListType.class)) {
      final ListType listType = method.getAnnotation(ListType.class);
      Class<? extends List> implementation = listType.implementation();
      Class<? extends Class> implementationClass = implementation.getClass();
      Class<?> value = listType.value();

      if (implementationClass.isAssignableFrom(returnClass)) {
        return new JsonListType(value, implementation);
      } else {
        return new JsonListType(value, returnClass);
      }
    }
    return returnClass;
  }

  private HttpRequest buildRequest(final String httpMethod, final GenericUrl url,
      final Map<Object, Object> jsonData) throws IOException {

    final HttpRequestFactory requestFactory =
        this.httpTransport.createRequestFactory(httpRequestInitializer);

    switch (httpMethod) {
      case HttpMethod.HEAD:
        return requestFactory.buildHeadRequest(url);
      case HttpMethod.GET:
        return requestFactory.buildGetRequest(url);
      case HttpMethod.DELETE:
        return requestFactory.buildDeleteRequest(url);
      case HttpMethod.OPTIONS:
        return requestFactory.buildRequest(httpMethod, url, EMPTY_CONTENT);
      case HttpMethod.POST:
      case HttpMethod.PUT:
        final JsonHttpContent httpContent = new JsonHttpContent(this.gsonFactory, jsonData);
        return requestFactory.buildRequest(httpMethod, url, httpContent);
    }
    throw new UnsupportedOperationException(httpMethod.toString());
  }

  private Object shouldReturnRequestOrItsRelated(final HttpRequest request,
      final Class<?> returnClass) {
    if (URL.class.isAssignableFrom(returnClass)) {
      return request.getUrl().toURI();
    }
    if (URI.class.isAssignableFrom(returnClass)) {
      return request.getUrl().toURI();
    }
    if (HttpRequest.class.isAssignableFrom(returnClass)) {
      return request;
    }
    return null;
  }

  static String resolveMethod(final Method method) {
    if (method.isAnnotationPresent(HttpMethod.class)) {
      return method.getAnnotation(HttpMethod.class).value();
    }

    for (Annotation annotation : method.getAnnotations()) {
      Class<? extends Annotation> clazz = annotation.annotationType();
      if (clazz.isAnnotationPresent(HttpMethod.class)) {
        return clazz.getAnnotation(HttpMethod.class).value();
      }
    }

    return "GET";
  }

  private Object createObjectResponse(HttpResponse res, Type returnType) throws IOException {
    Charset charset;
    try {
      String encoding = res.getContentEncoding();
      charset = Charset.forName(encoding);
    } catch (Exception e) {
      charset = Charset.forName("UTF-8");
    }

    try (final InputStream content = res.getContent()) {
      final InputStreamReader json = new InputStreamReader(content, charset);
      return gson.fromJson(json, returnType);
    }
  }

  private Object createStringResponse(final HttpResponse res) throws IOException {
    final InputStream content = res.getContent();
    String encoding = res.getContentEncoding();
    try {
      Charset.forName(encoding);
    } catch (Exception e) {
      encoding = "UTF-8";
    }

    try (Scanner scanner = new Scanner(content, encoding)) {
      scanner.useDelimiter("\\A");
      if (!scanner.hasNext()) {
        return "";
      }

      final String inputStreamString = scanner.next();
      return inputStreamString.toString();
    }
  }

  @Override
  public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args)
      throws Throwable {
    return this.invoke(self, thisMethod, args);
  }
}
