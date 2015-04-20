package la.serendipity.resty.exceptionhandring;

public enum StandardExceptionStrategy implements ExceptionHandler {
  // LOGING_AS_INFO,
  // LOGING_AS_WARN,
  LOGING_AS_ERROR, RETRY,
  // RETRY_WITH_TRACE,
  // WAIT300MS,
  // WAIT100MS,
  // WAIT1S,
  // WAIT10S,
  RETURN_EMPTY {},
  /**/;

  @Override
  public Object handle(MethodInformation methodInformation, Throwable exceptionOccured)
      throws Throwable {
    // TODO Auto-generated method stub
    return null;
  }
}
