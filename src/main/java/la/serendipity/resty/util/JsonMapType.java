package la.serendipity.resty.util;

import java.lang.reflect.Type;

public class JsonMapType implements java.lang.reflect.ParameterizedType {
  final private Type implementationType;
  final private Type[] valueType;

  public JsonMapType(Type implementationType, Type valueType) {
    this.implementationType = implementationType;
    this.valueType = new Type[] {String.class, valueType};
  }

  @Override
  public Type[] getActualTypeArguments() {
    return valueType;
  }

  @Override
  public Type getRawType() {
    return implementationType;
  }

  @Override
  public Type getOwnerType() {
    return null;
  }
}
