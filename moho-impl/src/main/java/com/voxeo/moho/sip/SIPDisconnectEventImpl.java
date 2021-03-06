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

package com.voxeo.moho.sip;

import java.util.Map;

import javax.servlet.sip.SipServletRequest;

import com.voxeo.moho.SignalException;
import com.voxeo.moho.event.EventSource;

public class SIPDisconnectEventImpl extends SIPDisconnectEvent {

  protected SIPDisconnectEventImpl(final EventSource source, final SipServletRequest req) {
    super(source, req);
  }

  @Override
  public synchronized void accept(final Map<String, String> headers) throws SignalException {
    this.checkState();
    _accepted = true;
    if (this.source instanceof SIPCallImpl) {
      final SIPCallImpl retval = (SIPCallImpl) this.source;
      retval.doBye(_req, headers);
    }
  }

}
