class Test {
	void test() {
				Class<?> handlerType = (handlerMethod != null ? handlerMethod.getBeanType() : null);
		Object exceptionHandlerObject = null;
		Method exceptionHandlerMethod = null;

		if (handlerType != null) {
			{
				Objects.requireNonNull(handlerMethod);
				exceptionHandlerObject = handlerMethod.getBean();
			}
		}
	}
}

