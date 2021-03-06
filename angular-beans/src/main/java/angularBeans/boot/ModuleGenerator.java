/*
 * AngularBeans, CDI-AngularJS bridge 
 *
 * Copyright (c) 2014, Bessem Hmidi. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 */

package angularBeans.boot;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpSession;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;

import angularBeans.api.NGPostConstruct;
import angularBeans.api.NGReturn;
import angularBeans.api.NGSubmit;
import angularBeans.context.BeanLocator;
import angularBeans.context.NGSessionScopeContext;
import angularBeans.io.ByteArrayCache;
import angularBeans.io.Call;
import angularBeans.io.FileUpload;
import angularBeans.io.FileUploadHandler;
import angularBeans.io.LobWrapper;
import angularBeans.realtime.RealTime;
import angularBeans.util.AngularBeansUtils;
import angularBeans.util.ClosureCompiler;
import angularBeans.util.CommonUtils;
import angularBeans.util.CurrentNGSession;
import angularBeans.util.NGBean;
import angularBeans.util.StaticJsCache;
import angularBeans.validation.BeanValidationProcessor;

/**
 * 
 * The ModuleGenerator is the main component in the angularBean javaScript
 * generation used by BootServlet
 * 
 * 
 @author Bessem Hmidi
 */
@SuppressWarnings("serial")
@SessionScoped
public class ModuleGenerator implements Serializable {

	ClosureCompiler compiler = ClosureCompiler.getINSTANCE();

	private String contextPath;

	private String sessionID;

	@Inject
	AngularBeansUtils util;

	public ModuleGenerator() {

	}

	/**
	 * since the NGSession scope lifecycle is the same as the current HTTP
	 * session a unique sessionId by http session, we use the same session id
	 */
	@PostConstruct
	public void init() {
		sessionID = httpSession.getId();// String.valueOf(UUID.randomUUID());
		NGSessionScopeContext.setCurrentContext(sessionID);
		ngSession.setSessionId(sessionID);
	}

	public synchronized String getUID() {
		return sessionID;
	}

	@Inject
	ByteArrayCache cache;

	@Inject
	BeanLocator locator;

	@Inject
	HttpSession httpSession;

	@Inject
	transient FileUploadHandler uploadHandler;

	@Inject
	BeanValidationProcessor validationAdapter;

	@Inject
	transient CurrentNGSession ngSession;

	/**
	 * this method generate the angular-beans.js content and write it to the <br>
	 * jsBuffer used by BootServlet
	 * 
	 * @param jsBuffer
	 */
	public void getScript(StringBuffer jsBuffer) {

		String sessionPart = "var sessionId=\"" + sessionID + "\";";

		// sessionPart="var sessionId = /SESS\\w*ID=([^;]+)/i.test(document.cookie) ? RegExp.$1 : false;";

		jsBuffer.append(sessionPart);

		jsBuffer.append(StaticJsCache.CORE_SCRIPT);

		StringBuffer beansBuffer = new StringBuffer();
		for (NGBean mb : BeanRegistry.getInstance().getAngularBeans()) {
			beansBuffer.append(generateBean(mb));
		}

		jsBuffer.append(ClosureCompiler.getINSTANCE().getCompressedJavaScript(
				beansBuffer.toString()));

		if (StaticJsCache.VALIDATION_SCRIPT.length() == 0) {
			validationAdapter.build();
		}

		jsBuffer.append(StaticJsCache.VALIDATION_SCRIPT);

		jsBuffer.append(StaticJsCache.EXTENTIONS_SCRIPT.toString());

	}

	/**
	 * this method concern is the generation of the AngularJS service from the @AngularBean
	 * CDI bean.
	 * 
	 * @param bean
	 *            the bean wrapper for an @AngularBean CDI bean.
	 * @return a StringBuffer containing the generated angular service code.
	 */
	public StringBuffer generateBean(NGBean bean) {

		StringBuffer buffer = new StringBuffer();
		Class<? extends Object> clazz = bean.getTargetClass();

		Method[] methods = bean.getMethods();

		buffer.append(";app.factory('" + bean.getName() + "',function "
				+ bean.getName() + "(");

		// writer.write("['$rootScope','$scope','$http','$location','logger','responseHandler','RTSrvc',function");

		buffer.append("$rootScope, $http, $location,logger,responseHandler,$q");

		buffer.append(",RTSrvc");
		buffer.append("){\n");

		// writer.write("var deffered = $q.defer();");
		buffer.append("var " + bean.getName() + "={serviceID:'"
				+ bean.getName() + "'};");// ,scopes:[]};");

		buffer.append("\nvar rpath=$rootScope.baseUrl+'" // + contextPath
				+ "http/invoke/service/';\n");

		Object reference = locator.lookup(bean.getName(), sessionID);

		for (Method get : bean.getters()) {
			Object result = null;

			String getter = get.getName();

			String modelName = CommonUtils.obtainFieldNameFromAccessor(getter);

			if (get.getReturnType().equals(LobWrapper.class)) {

				String uid = String.valueOf(UUID.randomUUID());
				cache.getCache().put(uid, new Call(reference, get));

				result = contextPath + "lob/" + uid;

				buffer.append(bean.getName() + "." + modelName + "='" + result
						+ "';");
				continue;

			}

			validationAdapter.processBeanValidationParsing(get);

			Method m;

			try {

				m = bean.getTargetClass().getMethod((getter));

				result = m.invoke(reference);

				if ((result == null && (m.getReturnType().equals(String.class))))
					result = "";

				if (result == null)
					continue;
				Class<? extends Object> resultClazz = result.getClass();
				if (!resultClazz.isPrimitive()) {
					result = util.getJson(result);
				}

			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (SecurityException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}

			buffer.append(bean.getName() + "." + modelName + "=" + result + ";");

		}

		for (Method m : bean.getMethods()) {
			if (m.isAnnotationPresent(FileUpload.class)) {

				String uploadPath = m.getAnnotation(FileUpload.class).path();

				Call call = new Call(reference, m);

				uploadHandler.getUploadsActions().put(uploadPath, call);
			}
		}

		buffer.append(generateStaticPart(bean).toString());

		buffer.append(");\n");

		return buffer;
	}

	/**
	 * 
	 * @param bean
	 *            the CDI bean wrapper
	 * @return StringBuffer containing the javaScript code of the static (non
	 *         properties values dependent) code. by static parts we mean the JS
	 *         code that can be generated from the java class of the bean (to
	 *         initialize the angularJs service we need to call getters on the
	 *         CDI bean instance and that is considered as the dynamic part of
	 *         the angularBean javascript generation)
	 */
	private StringBuffer generateStaticPart(NGBean bean) {

		StringBuffer cachedStaticPart = new StringBuffer();
		if (StaticJsCache.CACHED_BEAN_STATIC_PART.containsKey(bean
				.getTargetClass())) {
			return StaticJsCache.CACHED_BEAN_STATIC_PART.get(bean
					.getTargetClass());
		}

		Method[] nativesMethods = Object.class.getMethods();

		for (Method m : bean.getMethods()) {

			boolean isNative = false;
			for (Method nativeMethod : nativesMethods) {
				if (nativeMethod.equals(m))
					isNative = true;
			}

			if (isNative)
				continue;

			if ((!CommonUtils.isSetter(m)) && (!CommonUtils.isGetter(m))) {

				// String csModel = null;
				String[] csUpdates = null;
				Set<Method> setters = new HashSet<Method>();

				String httpMethod = "get";

				if (m.isAnnotationPresent(GET.class)) {
					httpMethod = "get";
				}

				if (m.isAnnotationPresent(POST.class)) {
					httpMethod = "post";
				}

				if (m.isAnnotationPresent(DELETE.class)) {
					httpMethod = "delete";
				}

				if (m.isAnnotationPresent(PUT.class)) {
					httpMethod = "put";
				}

				if (m.isAnnotationPresent(NGReturn.class)) {
					NGReturn returns = m.getAnnotation(NGReturn.class);
					csUpdates = returns.updates();
				}

				// if (m.isAnnotationPresent(NGSubmit.class)
				// || m.isAnnotationPresent(NGRedirect.class)) {

				if (m.isAnnotationPresent(NGSubmit.class)) {

					String[] models = m.getAnnotation(NGSubmit.class)
							.backEndModels();

					if (models.length == 1 && models[0].equals("*")) {

						pushScope(bean.getMethods(), setters);

					} else {

						for (String model : models) {

							for (Method md : bean.getMethods()) {

								if (CommonUtils.isSetter(md)) {
									String methodName = md.getName();
									String modelName = CommonUtils
											.obtainFieldNameFromAccessor(methodName);
									if (modelName.equals(model)) {
										setters.add(md);
									}

								}

							}
						}
					}
				}
				// else {
				// pushScope(methods, setters);
				// }

				//

				cachedStaticPart.append("angularBeans.addMethod("
						+ bean.getName() + ",'" + m.getName() + "',function(");

				// writer.write(bean.getName() + "." + m.getName() +
				// "= function(");

				// ---------------------------------------------

				// Handle args
				// ---------------------------------------------
				Type[] args = m.getParameterTypes();

				if (!m.isAnnotationPresent(FileUpload.class)) {

					if (args.length > 0) {
						String argsString = "";
						for (int i = 0; i < args.length; i++) {

							argsString += ("arg" + i + ",");

						}

						cachedStaticPart.append(argsString.substring(0,
								argsString.length() - 1));

					}
				}

				cachedStaticPart.append(") {")

				.append("var mainReturn={data:{}};").append("var params={};");// sessionUID:$rootScope.sessionUID

				cachedStaticPart.append(addParams(bean, setters, m, args));

				if (m.isAnnotationPresent(RealTime.class)) {

					cachedStaticPart.append("return RTSrvc.call("
							+ bean.getName() + ",'" + bean.getName() + "."
							+ m.getName() + "',params");

					cachedStaticPart.append(").then(function(response) {\n");

					cachedStaticPart.append("var msg=(response);");

					cachedStaticPart
							.append("mainReturn.data= responseHandler.handleResponse(msg,"
									+ bean.getName() + ",true);");

					cachedStaticPart.append("return mainReturn.data;"); // }");

					cachedStaticPart
							.append("} ,function(response){return $q.reject(response.data);});");

				} else {

					cachedStaticPart.append("\n  return $http." + httpMethod
							+ "(rpath+'" + bean.getName() + "/" + m.getName()
							+ "/json");

					if (httpMethod.equals("post")) {
						cachedStaticPart.append("',params");
					} else {
						// encodeURI
						String paramsQuery = ("?params='+encodeURIComponent(angular.toJson(params))");

						cachedStaticPart.append(paramsQuery);
					}

					cachedStaticPart.append(").then(function(response) {\n");

					cachedStaticPart.append("var msg=response.data;");
					// writer.write("var callers=RTSrvc.getCallers();");

					cachedStaticPart
							.append("mainReturn.data= responseHandler.handleResponse(msg,"
									+ bean.getName() + ",true);");

					// writer.write("deffered.resolve();");

					cachedStaticPart.append("return mainReturn.data;"); // }");

					cachedStaticPart
							.append("} ,function(response){return $q.reject(response.data);});");

				}

				cachedStaticPart.append("});");

				if ((!CommonUtils.isSetter(m)) && (!CommonUtils.isGetter(m))) {
					if (m.isAnnotationPresent(NGPostConstruct.class)) {

						cachedStaticPart.append(bean.getName() + "."
								+ m.getName() + "();\n");
					}
				}
			}
		}

		cachedStaticPart.append("return " + bean.getName() + ";} \n");
		StaticJsCache.CACHED_BEAN_STATIC_PART.put(bean.getClass(),
				cachedStaticPart);
		return cachedStaticPart;

	}

	private void pushScope(Method[] methods, Set<Method> setters) {
		for (Method md : methods) {

			if (CommonUtils.isSetter(md)) {
				setters.add(md);
			}
		}
	}

	private StringBuffer addParams(NGBean bean, Set<Method> setters, Method m,
			Type[] args) {

		StringBuffer sb = new StringBuffer();

		for (Method setter : setters) {

			String name = CommonUtils.obtainFieldNameFromAccessor(setter.getName());
			sb.append("params['" + name + "']=" + bean.getName() + "." + name
					+ ";");
		}

		if (args.length > 0) {
			String argsString = "";
			for (int i = 0; i < args.length; i++) {
				argsString += "arg" + i + ",";
			}
			argsString = argsString.substring(0, argsString.length() - 1);
			sb.append("params['args']=[" + argsString + "];\n");
		}
		return sb;
	}

	public void setContextPath(String contextPath) {

		util.setContextPath(contextPath);
		this.contextPath = contextPath;
	}

	public String getContextPath() {
		System.out.println("from util :" + contextPath);

		return contextPath;
	}
}