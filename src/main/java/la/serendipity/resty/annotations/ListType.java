package la.serendipity.resty.annotations;

public @interface ListType {
  Class<?> value();

  @SuppressWarnings("rawtypes")
  Class<? extends java.util.List> implementation() default java.util.List.class;
}
