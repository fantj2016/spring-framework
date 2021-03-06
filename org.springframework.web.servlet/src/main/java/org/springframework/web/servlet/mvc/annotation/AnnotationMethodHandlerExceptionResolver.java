/*
 * Copyright 2002-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.servlet.mvc.annotation;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.ui.Model;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.support.WebArgumentResolver;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Implementation of the {@link org.springframework.web.servlet.HandlerExceptionResolver} interface that handles
 * exceptions through the {@link ExceptionHandler} annotation. <p>This exception resolver is enabled by default in the
 * {@link org.springframework.web.servlet.DispatcherServlet}.
 *
 * @author Arjen Poutsma
 * @since 3.0
 */
public class AnnotationMethodHandlerExceptionResolver extends AbstractHandlerExceptionResolver {

	private WebArgumentResolver[] customArgumentResolvers;

	/**
	 * Set a custom ArgumentResolvers to use for special method parameter types. Such a custom ArgumentResolver will kick
	 * in first, having a chance to resolve an argument value before the standard argument handling kicks in.
	 */
	public void setCustomArgumentResolver(WebArgumentResolver argumentResolver) {
		this.customArgumentResolvers = new WebArgumentResolver[]{argumentResolver};
	}

	/**
	 * Set one or more custom ArgumentResolvers to use for special method parameter types. Any such custom ArgumentResolver
	 * will kick in first, having a chance to resolve an argument value before the standard argument handling kicks in.
	 */
	public void setCustomArgumentResolvers(WebArgumentResolver[] argumentResolvers) {
		this.customArgumentResolvers = argumentResolvers;
	}

	@Override
	protected ModelAndView doResolveException(HttpServletRequest request,
			HttpServletResponse response,
			Object handler,
			Exception ex) {
		if (handler != null) {
			Method handlerMethod = findBestExceptionHandlerMethod(handler, ex);
			if (handlerMethod != null) {
				ServletWebRequest webRequest = new ServletWebRequest(request, response);
				try {
					Object[] args = resolveHandlerArguments(handlerMethod, handler, webRequest, ex);
					if (logger.isDebugEnabled()) {
						logger.debug("Invoking request handler method: " + handlerMethod);
					}
					Object retVal = doInvokeMethod(handlerMethod, handler, args);
					return getModelAndView(handlerMethod, retVal, webRequest);
				}
				catch (Exception invocationEx) {
					logger.error("Invoking request method resulted in exception : " + handlerMethod, invocationEx);
				}
			}
		}
		return null;
	}

	/**
	 * Finds the handler method that matches the thrown exception best.
	 *
	 * @param handler the handler object
	 * @param thrownException the exception to be handled
	 * @return the best matching method; or <code>null</code> if none is found
	 */
	private Method findBestExceptionHandlerMethod(Object handler, final Exception thrownException) {
		final Class<?> handlerType = handler.getClass();
		final Class<? extends Throwable> thrownExceptionType = thrownException.getClass();

		final Map<Class<? extends Throwable>, Method> resolverMethods =
				new LinkedHashMap<Class<? extends Throwable>, Method>();

		ReflectionUtils.doWithMethods(handlerType, new ReflectionUtils.MethodCallback() {
			public void doWith(Method method) {
				method = ClassUtils.getMostSpecificMethod(method, handlerType);
				List<Class<? extends Throwable>> handledExceptions = getHandledExceptions(method);
				for (Class<? extends Throwable> handledException : handledExceptions) {
					if (handledException.isAssignableFrom(thrownExceptionType)) {
						if (!resolverMethods.containsKey(handledException)) {
							resolverMethods.put(handledException, method);
						}
						else {
							Method oldMappedMethod = resolverMethods.get(handledException);
							if (!oldMappedMethod.equals(method)) {
								throw new IllegalStateException(
										"Ambiguous exception handler mapped for " + handledException + "]: {" +
												oldMappedMethod + ", " + method + "}.");
							}

						}
					}
				}
			}
		});

		return getBestMatchingMethod(thrownException, resolverMethods);
	}

	/**
	 * Returns all the exception classes handled by the given method. <p>Default implementation looks for exceptions in the
	 * {@linkplain ExceptionHandler#value() annotation}, or - if that annotation element is empty - any exceptions listed
	 * in the method parameters if the method is annotated with {@code @ExceptionHandler}.
	 *
	 * @param method the method
	 * @return the handled exceptions
	 */
	@SuppressWarnings("unchecked")
	protected List<Class<? extends Throwable>> getHandledExceptions(Method method) {
		List<Class<? extends Throwable>> result = new ArrayList<Class<? extends Throwable>>();
		ExceptionHandler exceptionHandler = AnnotationUtils.findAnnotation(method, ExceptionHandler.class);
		if (exceptionHandler != null) {
			if (!ObjectUtils.isEmpty(exceptionHandler.value())) {
				result.addAll(Arrays.asList(exceptionHandler.value()));
			}
			else {
				for (Class<?> param : method.getParameterTypes()) {
					if (Throwable.class.isAssignableFrom(param)) {
						result.add((Class<? extends Throwable>) param);
					}
				}
			}
		}
		return result;
	}

	/** Returns the best matching method. Uses the {@link DepthComparator}. */
	private Method getBestMatchingMethod(Exception thrownException,
			Map<Class<? extends Throwable>, Method> resolverMethods) {
		if (!resolverMethods.isEmpty()) {
			List<Class<? extends Throwable>> handledExceptions =
					new ArrayList<Class<? extends Throwable>>(resolverMethods.keySet());
			Collections.sort(handledExceptions, new DepthComparator(thrownException));
			Class<? extends Throwable> bestMatchMethod = handledExceptions.get(0);
			return resolverMethods.get(bestMatchMethod);
		}
		else {
			return null;
		}
	}

	/** Resolves the arguments for the given method. Delegates to {@link #resolveCommonArgument}. */
	private Object[] resolveHandlerArguments(Method handlerMethod,
			Object handler,
			NativeWebRequest webRequest,
			Exception thrownException) throws Exception {

		Class[] paramTypes = handlerMethod.getParameterTypes();
		Object[] args = new Object[paramTypes.length];

		Class<?> handlerType = handler.getClass();

		for (int i = 0; i < args.length; i++) {
			MethodParameter methodParam = new MethodParameter(handlerMethod, i);
			GenericTypeResolver.resolveParameterType(methodParam, handlerType);

			Class paramType = methodParam.getParameterType();

			Object argValue = resolveCommonArgument(methodParam, webRequest, thrownException);
			if (argValue != WebArgumentResolver.UNRESOLVED) {
				args[i] = argValue;
			}
			else {
				throw new IllegalStateException(
						"Unsupported argument [" + paramType.getName() + "] for @ExceptionHandler method: " +
								handlerMethod);
			}
		}
		return args;
	}

	/**
	 * Resolves common method arguments. Delegates to registered {@link #setCustomArgumentResolver(WebArgumentResolver)
	 * argumentResolvers} first, then checking {@link #resolveStandardArgument}.
	 *
	 * @param methodParameter the method parameter
	 * @param webRequest the request
	 * @param thrownException the exception thrown
	 * @return the argument value, or {@link WebArgumentResolver#UNRESOLVED}
	 */
	protected Object resolveCommonArgument(MethodParameter methodParameter,
			NativeWebRequest webRequest,
			Exception thrownException) throws Exception {
		// Invoke custom argument resolvers if present...
		if (this.customArgumentResolvers != null) {
			for (WebArgumentResolver argumentResolver : this.customArgumentResolvers) {
				Object value = argumentResolver.resolveArgument(methodParameter, webRequest);
				if (value != WebArgumentResolver.UNRESOLVED) {
					return value;
				}
			}
		}

		// Resolution of standard parameter types...
		Class paramType = methodParameter.getParameterType();
		Object value = resolveStandardArgument(paramType, webRequest, thrownException);
		if (value != WebArgumentResolver.UNRESOLVED && !ClassUtils.isAssignableValue(paramType, value)) {
			throw new IllegalStateException(
					"Standard argument type [" + paramType.getName() + "] resolved to incompatible value of type [" +
							(value != null ? value.getClass() : null) +
							"]. Consider declaring the argument type in a less specific fashion.");
		}
		return value;
	}

	/**
	 * Resolves standard method arguments. Default implementation handles {@link NativeWebRequest}, {@link ServletRequest},
	 * {@link ServletResponse}, {@link HttpSession}, {@link Principal}, {@link Locale}, request {@link InputStream},
	 * request {@link Reader}, response {@link OutputStream}, response {@link Writer}, and the given {@code
	 * thrownException}.
	 *
	 * @param parameterType the method parameter type
	 * @param webRequest the request
	 * @param thrownException the exception thrown
	 * @return the argument value, or {@link WebArgumentResolver#UNRESOLVED}
	 */
	protected Object resolveStandardArgument(Class parameterType,
			NativeWebRequest webRequest,
			Exception thrownException) throws Exception {
		if (WebRequest.class.isAssignableFrom(parameterType)) {
			return webRequest;
		}

		HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
		HttpServletResponse response = (HttpServletResponse) webRequest.getNativeResponse();

		if (ServletRequest.class.isAssignableFrom(parameterType)) {
			return request;
		}
		else if (ServletResponse.class.isAssignableFrom(parameterType)) {
			return response;
		}
		else if (HttpSession.class.isAssignableFrom(parameterType)) {
			return request.getSession();
		}
		else if (Principal.class.isAssignableFrom(parameterType)) {
			return request.getUserPrincipal();
		}
		else if (Locale.class.equals(parameterType)) {
			return RequestContextUtils.getLocale(request);
		}
		else if (InputStream.class.isAssignableFrom(parameterType)) {
			return request.getInputStream();
		}
		else if (Reader.class.isAssignableFrom(parameterType)) {
			return request.getReader();
		}
		else if (OutputStream.class.isAssignableFrom(parameterType)) {
			return response.getOutputStream();
		}
		else if (Writer.class.isAssignableFrom(parameterType)) {
			return response.getWriter();
		}
		else if (parameterType.isInstance(thrownException)) {
			return thrownException;
		}
		else {
			return WebArgumentResolver.UNRESOLVED;

		}
	}

	private Object doInvokeMethod(Method method, Object target, Object[] args) throws Exception {
		ReflectionUtils.makeAccessible(method);
		try {
			return method.invoke(target, args);
		}
		catch (InvocationTargetException ex) {
			ReflectionUtils.rethrowException(ex.getTargetException());
		}
		throw new IllegalStateException("Should never get here");
	}

	@SuppressWarnings("unchecked")
	private ModelAndView getModelAndView(Method handlerMethod, Object returnValue, ServletWebRequest webRequest)
			throws Exception {

		ResponseStatus responseStatus = AnnotationUtils.findAnnotation(handlerMethod, ResponseStatus.class);
		if (responseStatus != null) {
			HttpServletResponse response = webRequest.getResponse();
			response.setStatus(responseStatus.value().value());
		}
		if (returnValue instanceof ModelAndView) {
			return (ModelAndView) returnValue;
		}
		else if (returnValue instanceof Model) {
			return new ModelAndView().addAllObjects(((Model) returnValue).asMap());
		}
		else if (returnValue instanceof Map) {
			return new ModelAndView().addAllObjects((Map) returnValue);
		}
		else if (returnValue instanceof View) {
			return new ModelAndView((View) returnValue);
		}
		else if (returnValue instanceof String) {
			return new ModelAndView((String) returnValue);
		}
		else if (returnValue == null) {
			return new ModelAndView();
		}
		else {
			throw new IllegalArgumentException("Invalid handler method return value: " + returnValue);
		}
	}

	/** Comparator capable of sorting exceptions based on their depth from the thrown exception type. */
	private static class DepthComparator implements Comparator<Class<? extends Throwable>> {

		private final Class<? extends Throwable> handlerExceptionType;

		private DepthComparator(Exception handlerException) {
			this.handlerExceptionType = handlerException.getClass();
		}

		public int compare(Class<? extends Throwable> o1, Class<? extends Throwable> o2) {
			int depth1 = getDepth(o1, 0);
			int depth2 = getDepth(o2, 0);

			return depth2 - depth1;
		}

		private int getDepth(Class exceptionType, int depth) {
			if (exceptionType.equals(handlerExceptionType)) {
				// Found it!
				return depth;
			}
			// If we've gone as far as we can go and haven't found it...
			if (Throwable.class.equals(exceptionType)) {
				return -1;
			}
			return getDepth(exceptionType.getSuperclass(), depth + 1);
		}

	}
}
