/**
 * Copyright 2010 Voxeo Corporation Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.voxeo.moho.sip;

import java.io.IOException;
import java.util.Map;

import javax.media.mscontrol.join.Joinable.Direction;
import javax.media.mscontrol.networkconnection.SdpPortManagerEvent;
import javax.servlet.sip.Rel100Exception;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

import org.apache.log4j.Logger;

import com.voxeo.moho.CanceledException;
import com.voxeo.moho.ExecutionContext;
import com.voxeo.moho.MediaException;
import com.voxeo.moho.event.CallCompleteEvent;

public class SIPIncomingCall extends SIPCallImpl {

  private static final Logger LOG = Logger.getLogger(SIPIncomingCall.class);

  protected SIPIncomingCall(final ExecutionContext context, final SipServletRequest req) {
    super(context, req);
    setRemoteSDP(SIPHelper.getRawContentWOException(req));
  }

  @Override
  protected JoinDelegate createJoinDelegate(final Direction direction) {
    JoinDelegate retval = null;
    if (isNoAnswered()) {
      retval = new Media2NIJoinDelegate(this);
    }
    else if (isAnswered()) {
      retval = new Media2AIJoinDelegate(this);
    }
    else {
      throw new IllegalStateException("The SIPCall state is " + getSIPCallState());
    }
    return retval;
  }

  @Override
  protected JoinDelegate createJoinDelegate(final SIPCallImpl other, final JoinType type, final Direction direction) {
    JoinDelegate retval = null;
    if (type == JoinType.DIRECT) {
      if (isNoAnswered()) {
        if (other.isNoAnswered()) {
          if (other instanceof SIPOutgoingCall) {
            retval = new DirectNI2NOJoinDelegate(this, (SIPOutgoingCall) other, direction);
          }
          else if (other instanceof SIPIncomingCall) {
            retval = new DirectNI2NIJoinDelegate(this, (SIPIncomingCall) other, direction);
          }
        }
        else if (other.isAnswered()) {
          if (other instanceof SIPOutgoingCall) {
            retval = new DirectNI2AOJoinDelegate(this, (SIPOutgoingCall) other, direction);
          }
          else if (other instanceof SIPIncomingCall) {
            retval = new DirectNI2AIJoinDelegate(this, (SIPIncomingCall) other, direction);
          }
        }
      }
      else if (isAnswered()) {
        if (other.isNoAnswered()) {
          if (other instanceof SIPOutgoingCall) {
            retval = new DirectAI2NOJoinDelegate(this, (SIPOutgoingCall) other, direction);
          }
          else if (other instanceof SIPIncomingCall) {
            retval = new DirectNI2AIJoinDelegate((SIPIncomingCall) other, this, direction);
          }
        }
        else if (other.isAnswered()) {
          if (other instanceof SIPOutgoingCall) {
            retval = new DirectAI2AOJoinDelegate(this, (SIPOutgoingCall) other, direction);
          }
          else if (other instanceof SIPIncomingCall) {
            retval = new DirectAI2AIJoinDelegate(this, (SIPIncomingCall) other, direction);
          }
        }
      }
    }
    else {
      retval = new BridgeJoinDelegate(this, other, direction);
    }
    return retval;
  }

  @Override
  public synchronized void onEvent(final SdpPortManagerEvent event) {
    if (getSIPCallState() == SIPCall.State.PROGRESSING) {
      try {
        final byte[] sdp = event.getMediaServerSdp();
        this.setLocalSDP(sdp);
        final SipServletResponse res = getSipInitnalRequest().createResponse(SipServletResponse.SC_SESSION_PROGRESS);
        res.setContent(sdp, "application/sdp");
        try {
          res.sendReliably();
        }
        catch (final Rel100Exception e) {
          LOG.warn("", e);
          res.send();
        }
        setSIPCallState(SIPCall.State.PROGRESSED);
        this.notifyAll();
      }
      catch (final IOException e) {
        LOG.warn("", e);
      }
    }
    super.onEvent(event);
  }

  protected synchronized void doCancel() {
    if (isTerminated()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Receiving Cancel, but is already terminated. callID:"
            + (getSipSession() != null ? getSipSession().getCallId() : ""));
      }
    }
    else if (isNoAnswered()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Receiving Cancel, not answered. terminating, callID"
            + (getSipSession() != null ? getSipSession().getCallId() : ""));
      }
      if (_joinDelegate != null) {
        _joinDelegate.setException(new CanceledException());
      }
      this.setSIPCallState(SIPCall.State.DISCONNECTED);
      terminate(CallCompleteEvent.Cause.CANCEL, null);
    }
    else {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Receiving Cancel, but is already answered. terminating, callID"
            + (getSipSession() != null ? getSipSession().getCallId() : ""));
      }
      this.setSIPCallState(SIPCall.State.DISCONNECTED);
      terminate(CallCompleteEvent.Cause.CANCEL, null);
    }
  }

  protected synchronized void doInvite(final Map<String, String> headers) throws IOException {
    if (_cstate == SIPCall.State.INVITING) {
      setSIPCallState(SIPCall.State.RINGING);
      final SipServletResponse res = _invite.createResponse(SipServletResponse.SC_RINGING);
      SIPHelper.addHeaders(res, headers);
      res.send();
    }
  }

  protected synchronized void doInviteWithEarlyMedia(final Map<String, String> headers) throws MediaException {
    if (_cstate == SIPCall.State.INVITING) {
      setSIPCallState(SIPCall.State.PROGRESSING);
      processSDPOffer(getSipInitnalRequest());
      while (!this.isTerminated() && _cstate == SIPCall.State.PROGRESSING) {
        try {
          this.wait();
        }
        catch (final InterruptedException e) {
          // ignore
        }
      }
      if (_cstate != SIPCall.State.PROGRESSED) {
        throw new IllegalStateException("" + this);
      }
    }
  }

  protected synchronized void doPrack(final SipServletRequest req) throws IOException {
    if (_cstate == SIPCall.State.PROGRESSED) {
      final SipServletResponse res = req.createResponse(SipServletResponse.SC_OK);
      if (getLocalSDP() != null) {
        res.setContent(getLocalSDP(), "application/sdp");
      }
      res.send();
    }
  }

}
