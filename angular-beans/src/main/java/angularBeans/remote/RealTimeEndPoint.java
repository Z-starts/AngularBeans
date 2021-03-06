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

/**
 @author Bessem Hmidi
 */
package angularBeans.remote;

import java.util.HashSet;
import java.util.logging.Logger;

import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;

import org.projectodd.sockjs.SockJsConnection;
import org.projectodd.sockjs.SockJsServer;
import org.projectodd.sockjs.servlet.SockJsServlet;

import angularBeans.context.NGSessionScopeContext;
import angularBeans.context.SessionMapper;
import angularBeans.events.RealTimeErrorEvent;
import angularBeans.events.RealTimeSessionCloseEvent;
import angularBeans.events.RealTimeSessionReadyEvent;
import angularBeans.realtime.AngularBeansServletContextListener;
import angularBeans.realtime.GlobalConnectionHolder;
import angularBeans.util.AngularBeansUtils;
import angularBeans.util.CommonUtils;

import com.google.gson.JsonObject;

/**
 * The RealTimeEndPoint servlet is the realtime sockjs protocol endpoint
 * 
 * @author Bessem Hmidi
 *
 */

@SuppressWarnings("serial")
@WebServlet(loadOnStartup = 1, asyncSupported = true, urlPatterns = "/rt-service/*")
public class RealTimeEndPoint extends SockJsServlet {

	@Inject
	@DataReceivedEvent
	private Event<RealTimeDataReceivedEvent> receiveEvents;

	@Inject
	@RealTimeSessionReadyEvent
	private Event<RealTimeDataReceivedEvent> sessionOpenEvent;

	@Inject
	@RealTimeSessionCloseEvent
	private Event<RealTimeDataReceivedEvent> sessionCloseEvent;

	@Inject
	@RealTimeErrorEvent
	private Event<RealTimeDataReceivedEvent> errorEvent;

	@Inject
	GlobalConnectionHolder globalConnectionHolder;

	@Inject
	Logger logger;

	// @OnClose
	// public void onclose(Session session) {
	// sessionCloseEvent.fire(new WSocketEvent(session, null));
	// Logger.getLogger("AngularBeans").info("ws-channel closed");
	// }
	//
	// @OnError
	// public void onError(Session session, Throwable error) {
	// // errorEvent.fire(new WSocketEvent(session,
	// // Util.parse(Util.getJson(error))));
	// error.printStackTrace();
	// }
	//

	// @PostConstruct
	// public void start() {
	// try {
	// super.inito();
	// } catch (ServletException e) {

	// e.printStackTrace();
	// }
	//
	// }

	@Override
	public void init() throws ServletException {
		SockJsServer server = AngularBeansServletContextListener.sockJsServer;
		// Various options can be set on the server, such as:
		// echoServer.options.responseLimit = 4 * 1024;

		// server.options.
		// onConnection is the main entry point for handling SockJS connections
		server.onConnection(new SockJsServer.OnConnectionHandler() {

			@Override
			public void handle(final SockJsConnection connection) {

				// logger.info("session opened");
				// onData gets called when a client sends data to the server
				connection.onData(new SockJsConnection.OnDataHandler() {
					@Override
					public void handle(String message) {

						JsonObject jObj = CommonUtils.parse(message)
								.getAsJsonObject();
						String UID = null;

						if (jObj.get("session") == null) {
							UID = SessionMapper.getHTTPSessionID(connection.id);
						} else {
							UID = jObj.get("session").getAsString();
							SessionMapper.getSessionsMap().put(UID,
									new HashSet<String>());

						}
						SessionMapper.getSessionsMap().get(UID)
								.add(connection.id);

						RealTimeDataReceivedEvent ev = new RealTimeDataReceivedEvent(
								connection, jObj);

						ev.setConnection(connection);
						ev.setSessionId(UID);
						NGSessionScopeContext.setCurrentContext(UID);

						String service = jObj.get("service").getAsString();

						if (service.equals("ping")) {

							sessionOpenEvent.fire(ev);
							logger.info("AngularBeans-client: " + UID);

						} else {

							receiveEvents.fire(ev);

						}

						// connection.write(message);
					}
				});

				// onClose gets called when a client disconnects
				connection.onClose(new SockJsConnection.OnCloseHandler() {
					@Override
					public void handle() {

						getServletContext().log(
								"Realtime client disconnected..");
					}
				});
			}
		});

		setServer(server);

		// Don't forget to call super.init() to wire everything up
		super.init();

	}

}
