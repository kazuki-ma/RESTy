package la.serendipity.resty.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collection;
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

import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.HttpResponse;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionLikeType;
import com.fasterxml.jackson.databind.type.MapLikeType;
import com.fasterxml.jackson.databind.type.SimpleType;

import la.serendipity.HttpRequestInitializer;
import la.serendipity.resty.annotations.ListType;
import la.serendipity.resty.annotations.MapType;
import la.serendipity.resty.util.JsonListType;
import la.serendipity.resty.util.JsonMapType;
import la.serendipity.resty.util.Parameter;
import lombok.Cleanup;

/**
 * RestMapper core class which invoke REST request dinamically via interface
 * call.
 * 
 * @author Kazuki Matsuda
 */
public class InvocationHandlerImplementation//
	implements InvocationHandler, MethodHandler {
    private final Logger log;
    private final ObjectMapper objectMapper;
    private final String urlBase;
    private final HttpRequestInitializer httpRequestInitializer;

    public InvocationHandlerImplementation(final String urlBase,
	    final HttpRequestInitializer httpRequestInitializer,
	    final Class<?> target) {
	log = LoggerFactory.getLogger(target.getCanonicalName() + "(RESTy)");
	Path path = target.getAnnotation(Path.class);
	if (path != null) {
	    this.urlBase = urlBase + path.value();
	} else {
	    this.urlBase = urlBase;
	}
	this.httpRequestInitializer = httpRequestInitializer;
	objectMapper = null;
    }

    @Override
    public Object invoke(final Object proxy, final Method method,
	    final Object[] arguments) throws IOException,
	    MethodNotSupportedException, URISyntaxException {
	final Path path = method.getAnnotation(Path.class);
	final List<Parameter> parameters = Parameter.getParameters(method,
		arguments);

	final String httpMethod = resolveMethod(method);

	final Class<?> returnClass = method.getReturnType();
	JavaType returnType = detectReturnType(method, returnClass);

	final String uriString = urlBase + path.value();

	final Map<Object, Object> jsonData = new HashMap<>();

	@Cleanup
	final CloseableHttpClient client = HttpClients.createDefault();
	final URIBuilder uriBuilder = new URIBuilder(uriString);

	for (Parameter parameter : parameters) {
	    final Object argument = parameter.getArgument();
	    if (argument == null) {
		log.debug("Ignore null value parameter = {}",
			parameter.toString());
		continue;
	    }

	    if (parameter.getAnnotation(QueryParam.class) != null) {
		final String name = parameter.getAnnotation(QueryParam.class)
			.value();
		if (argument instanceof java.util.Collection) {
		    for (Object arg : (java.util.Collection<?>) argument) {
			uriBuilder.addParameter(name,
				arg != null ? arg.toString() : "");
		    }
		} else {
		    uriBuilder.addParameter(name, argument.toString());
		}
	    }
	    if (parameter.getAnnotation(PathParam.class) != null) {
		final String valueName = parameter.getAnnotation(
			PathParam.class).value();
		uriBuilder.setHost(uriBuilder.getHost().replace(
			"{" + valueName + "}", argument.toString()));
		uriBuilder.setPath(uriBuilder.getPath().replace(
			"{" + valueName + "}", argument.toString()));
	    }
	    if (parameter.getAnnotation(FormParam.class) != null) {
		jsonData.put(parameter.getAnnotation(FormParam.class).value(),
			argument);
	    }
	    if (argument instanceof Type) {
		returnType = (Type) argument;
	    }
	}

	final HttpRequest request = buildRequest(httpMethod,
		uriBuilder.build(), jsonData);

	log.trace("REQUEST : {}", request.getRequestLine());
	{
	    final Object returnRequest = shouldReturnRequestOrItsRelated(
		    request, returnClass);
	    if (returnRequest != null) {
		return returnRequest;
	    }
	}
	final HttpResponse res = client.execute(null, request);
	log.trace("RESPONSE: {}", res.getStatusLine());

	if (HttpResponse.class.isAssignableFrom(returnClass)) {
	    return res;
	}
	try {
	    if (HttpResponse.class.isAssignableFrom(returnClass)) {
		return res;
	    }
	    if (String.class.isAssignableFrom(returnClass)) {
		return createStringResponse(res);
	    }
	    if (StatusLine.class.isAssignableFrom(returnClass)) {
		return res.getStatusLine();
	    }
	    if (Integer.class.isAssignableFrom(returnClass)
		    || int.class.isAssignableFrom(returnClass)) {
		return res.getStatusLine().getStatusCode();
	    }
	    return createObjectResponse(res, returnType);
	} finally {
	    client.close();
	}
    }

    @SuppressWarnings("rawtypes")
    private JavaType detectReturnType(final Method method,
	    final Class<?> returnClass) {
	if (method.isAnnotationPresent(MapType.class)) {
	    final MapType typeAnnotation = method.getAnnotation(MapType.class);
	    SimpleType value = SimpleType.construct(typeAnnotation.value());

	    Class<? extends Map> implementation = typeAnnotation
		    .implementation();
	    Class<? extends Class> implementationClass = implementation
		    .getClass();

	    if (implementationClass.isAssignableFrom(returnClass)) {
		return MapLikeType.construct(returnClass, SimpleType.construct(String.class), value);
	    } else {
		return new MapLikeType(value, returnClass);
	    }
	}
	if (method.isAnnotationPresent(ListType.class)) {
	    final ListType listType = method.getAnnotation(ListType.class);
	    Class<? extends List> implementation = listType.implementation();
	    Class<? extends Class> implementationClass = implementation
		    .getClass();
	    Class<?> value = listType.value();

	    if (implementationClass.isAssignableFrom(returnClass)) {
		return new CollectionLikeType(value, implementation);
	    } else {
		return new CollectionLikeType(value, returnClass);
	    }
	}
	return returnClass;
    }

    private HttpRequest buildRequest(final String httpMethod, final URI uri,
	    final Map<Object, Object> jsonData)
	    throws MethodNotSupportedException {

	final HttpRequestFactory requestFactory = new DefaultHttpRequestFactory();
	final HttpRequest newHttpRequest = requestFactory.newHttpRequest(
		httpMethod, uri.toString());

	return newHttpRequest;
    }

    private Object shouldReturnRequestOrItsRelated(final HttpRequest request,
	    final Class<?> returnClass) {
	if (URL.class.isAssignableFrom(returnClass)) {
	    try {
		return URI.create(request.getRequestLine().getUri()).toURL();
	    } catch (MalformedURLException e) {
		throw new IllegalArgumentException(e);
	    }
	}
	if (URI.class.isAssignableFrom(returnClass)) {
	    return URI.create(request.getRequestLine().getUri());
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

    private Object createObjectResponse(HttpResponse res, JavaType returnType)
	    throws IOException {
	Charset charset;
	try {
	    String encoding = res.getEntity().getContentEncoding().getValue();
	    charset = Charset.forName(encoding);
	} catch (Exception e) {
	    charset = Charset.forName("UTF-8");
	}

	try (final InputStream content = res.getEntity().getContent()) {
	    final InputStreamReader json = new InputStreamReader(content,
		    charset);
	    return objectMapper.readValue(json, returnType);
	}
    }

    private Object createStringResponse(final HttpResponse res)
	    throws IOException {
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
    public Object invoke(Object self, Method thisMethod, Method proceed,
	    Object[] args) throws Throwable {
	return this.invoke(self, thisMethod, args);
    }
}
