package la.serendipity.resty.util;

import java.lang.reflect.Type;

public class JsonListType implements java.lang.reflect.ParameterizedType {
  final private Type implementationType;
  final private Type[] valueType;

  public JsonListType(Type implementationType, Type valueType) {
    this.implementationType = implementationType;
    this.valueType = new Type[] {valueType};
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
