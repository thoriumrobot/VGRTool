class Test {
	void test() {
		Class<?> handlerType = (handlerMethod != null ? handlerMethod.getBeanType() : null);
		Object exceptionHandlerObject = null;
		Method exceptionHandlerMethod = null;

		if (handlerMethod != null) {
			exceptionHandlerObject = handlerMethod.getBean();
		}
	}
}

