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
package angularBeans.wsocket;

import java.io.Serializable;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import angularBeans.AngularBeansUtil;
import angularBeans.context.NGSessionScopeContext;
import angularBeans.wsocket.annotations.WSocketReceiveEvent;
import angularBeans.wsocket.annotations.WSocketSessionCloseEvent;
import angularBeans.wsocket.annotations.WSocketSessionReadyEvent;

import com.google.gson.JsonObject;

@ServerEndpoint(value = "/ws-service", configurator = GetHttpSessionConfigurator.class)
public class WSocketServcieEndPoint implements Serializable {

	
	
	
	@Inject
	@WSocketReceiveEvent
	private Event<WSocketEvent> receiveEvents;

	@Inject
	@WSocketSessionReadyEvent
	private Event<WSocketEvent> sessionOpenEvent;

	@Inject
	@WSocketSessionCloseEvent
	private Event<WSocketEvent> sessionCloseEvent;

	@Inject
	@WSocketErrorEvent
	private Event<WSocketEvent> errorEvent;

	@PostConstruct
	public void init() {
		// Thread.currentThread().setName("Wsocket End Point");
	}

	@OnOpen
	public void onOpen(Session session, EndpointConfig conf) {
		// NGSessionScopeContext.changeHolder(UID);
		// sessionOpenEvent.fire(new WSocketEvent(session, null));

	}

	@OnMessage
	public void onMessage(Session session, String message) {

		JsonObject jObj = AngularBeansUtil.parse(message);
		String UID = jObj.get("session").getAsString();

		WSocketEvent ev = new WSocketEvent(session, AngularBeansUtil.parse(message));

		ev.setSession(session);
		NGSessionScopeContext.setCurrentContext(UID);

		String service = jObj.get("service").getAsString();

		if (service.equals("ping")) {
			
			sessionOpenEvent.fire(ev);
			Logger.getLogger("AngularBeans").info("ws-client: " + UID);
			
		} else {

			receiveEvents.fire(ev);
		}

	}

	@OnClose
	public void onclose(Session session) {
		sessionCloseEvent.fire(new WSocketEvent(session, null));
		Logger.getLogger("AngularBeans").info("ws-channel closed");
	}

	@OnError
	public void onError(Session session, Throwable error) {
		// errorEvent.fire(new WSocketEvent(session,
		// Util.parse(Util.getJson(error))));
		error.printStackTrace();
	}

}