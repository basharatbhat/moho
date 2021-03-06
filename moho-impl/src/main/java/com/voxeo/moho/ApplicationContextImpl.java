/**
 * Copyright 2010 Voxeo Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.voxeo.moho;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.media.mscontrol.MsControlFactory;
import javax.sdp.SdpFactory;
import javax.servlet.ServletContext;
import javax.servlet.sip.SipFactory;

import com.voxeo.moho.conference.ConferenceManager;
import com.voxeo.moho.sip.SIPEndpointImpl;
import com.voxeo.moho.textchannel.TextChannels;
import com.voxeo.moho.util.Utils.DaemonThreadFactory;
import com.voxeo.moho.voicexml.VoiceXMLEndpointImpl;

public class ApplicationContextImpl extends AttributeStoreImpl implements ExecutionContext {

  protected Application _application;

  protected MsControlFactory _mcFactory;

  protected ConferenceManager _confMgr;

  protected SipFactory _sipFactory;

  protected SdpFactory _sdpFactory;

  protected MediaServiceFactory _msFactory;

  protected String _controller;

  protected Map<String, Call> _calls = new ConcurrentHashMap<String, Call>();

  protected Map<String, String> _parameters = new ConcurrentHashMap<String, String>();

  protected ServletContext _servletContext;

  protected ThreadPoolExecutor _executor;

  public ApplicationContextImpl(final Application app, final MsControlFactory mc, final SipFactory sip,
      final SdpFactory sdp, final String controller, final ServletContext servletContext, final int threadPoolSize) {
    _application = app;
    _mcFactory = mc;
    _sipFactory = sip;
    _sdpFactory = sdp;
    _controller = controller;
    _servletContext = servletContext;

    _executor = new ThreadPoolExecutor(threadPoolSize, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
        new SynchronousQueue<Runnable>(), new DaemonThreadFactory("MohoContext"));
  }

  @Override
  public Application getApplication() {
    return _application;
  }

  public Endpoint getEndpoint(final String addr, String type) {
    if (addr == null) {
      throw new IllegalArgumentException("argument is null");
    }
    try {
      if (addr.startsWith("sip:") || addr.startsWith("sips:") || addr.startsWith("<sip:") || addr.startsWith("<sips:")) {
        return new SIPEndpointImpl(this, _sipFactory.createAddress(addr));
      }
      else if (addr.startsWith("mscontrol://")) {
        return new MixerEndpointImpl(this, addr);
      }
      else if (addr.startsWith("file://") || addr.startsWith("http://") || addr.startsWith("https://")
          || addr.startsWith("ftp://")) {
        return new VoiceXMLEndpointImpl(this, addr);
      }
      else if (addr.startsWith("tel:") || addr.startsWith("fax:") || addr.startsWith("<tel:")
          || addr.startsWith("<fax:")) {
        return new SIPEndpointImpl(this, _sipFactory.createAddress(addr));
      }
      else if (type != null && TextChannels.getProvider(type) != null) {
        return TextChannels.getProvider(type).createEndpoint(addr, this);
      }
    }
    catch (final Exception e) {
      throw new IllegalArgumentException(e);
    }
    throw new IllegalArgumentException("Unsupported format: " + addr);
  }

  @Override
  public Endpoint getEndpoint(final String addr) {
    return getEndpoint(addr, null);
  }

  @Override
  public Endpoint createEndpoint(String endpoint, String type) {
    return getEndpoint(endpoint, type);
  }

  @Override
  public Endpoint createEndpoint(String endpoint) {
    return getEndpoint(endpoint, null);
  }

  @Override
  public MsControlFactory getMSFactory() {
    return _mcFactory;
  }

  @Override
  public SipFactory getSipFactory() {
    return _sipFactory;
  }

  @Override
  public ConferenceManager getConferenceManager() {
    return _confMgr;
  }

  public void setConferenceManager(ConferenceManager conferenceManager) {
    _confMgr = conferenceManager;
  }

  @Override
  public Executor getExecutor() {
    return _executor;
  }

  @Override
  public Call getCall(final String cid) {
    return _calls.get(cid);
  }

  @Override
  public void addCall(final Call call) {
    _calls.put(call.getId(), call);
  }

  @Override
  public void removeCall(final String id) {
    _calls.remove(id);
  }

  @Override
  public String getParameter(final String name) {
    return _parameters.get(name);
  }

  @Override
  public Map<String, String> getParameters() {
    return Collections.unmodifiableMap(_parameters);
  }

  public String getController() {
    return _controller;
  }

  public void setParameter(final String name, final String value) {
    _parameters.put(name, value);
  }

  @Override
  public SdpFactory getSdpFactory() {
    return _sdpFactory;
  }

  @Override
  public MediaServiceFactory getMediaServiceFactory() {
    return _msFactory;
  }

  public void setMediaServiceFactory(MediaServiceFactory factory) {
    _msFactory = factory;
  }

  @Override
  public ServletContext getServletContext() {
    return _servletContext;
  }

  @Override
  public String getRealPath(final String path) {
    return getServletContext().getRealPath(path);
  }

  public void destroy() {
    getApplication().destroy();
    _executor.shutdown();
  }

}
