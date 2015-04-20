package la.serendipity.resty.annotations;

public @interface MapType {
  Class<?> value();

  @SuppressWarnings("rawtypes")
  Class<? extends java.util.Map> implementation() default java.util.Map.class;
}
