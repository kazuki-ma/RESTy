package la.serendipity.resty.exceptionhandring;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

import la.serendipity.resty.util.Parameter;

public interface ExceptionHandler {
  Object handle(MethodInformation methodInformation, Throwable exceptionOccured) throws Throwable;

  public static class MethodInformation {
    Object proxy;
    Method method;
    List<Parameter> parameters;
    Type returnType;
  }
}
