package la.serendipity.resty.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import lombok.Value;

@Value
public class Parameter {
  final Annotation[] annotations;
  final Class<?> type;
  final Object argument;

  @SuppressWarnings("unchecked")
  public <T> T getAnnotation(Class<T> annotationClass) {
    for (Annotation annotation : annotations) {
      if (annotationClass.isAssignableFrom(annotation.getClass())) {
        return (T) annotation;
      }
    }
    return null;
  }

  public static List<Parameter> getParameters(Method method, Object[] arguments) {
    final Annotation[][] parameterAnnotations = method.getParameterAnnotations();
    final Class<?>[] parameterTypes = method.getParameterTypes();

    final int length = parameterAnnotations.length;

    final List<Parameter> parameters = new ArrayList<>();

    for (int i = 0; i < length; ++i) {
      final Annotation[] annotations = parameterAnnotations[i];
      final Class<?> parameterType = parameterTypes[i];
      final Object argument = arguments[i];
      parameters.add(new Parameter(annotations, parameterType, argument));
    }

    return parameters;
  }
}
