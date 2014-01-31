/******************************************************************************
 * Copyright (c) 2012-2014, Vladimir Kravets                                  *
 *  All rights reserved.                                                      *
 *                                                                            *
 *  Redistribution and use in source and binary forms, with or without        *
 *  modification, are permitted provided that the following conditions are    *
 *  met: Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.                     *
 *  Redistributions in binary form must reproduce the above copyright notice, *
 *  this list of conditions and the following disclaimer in the documentation *
 *  and/or other materials provided with the distribution.                    *
 *  Neither the name of the Fido4Java nor the names of its contributors       *
 *  may be used to endorse or promote products derived from this software     *
 *  without specific prior written permission.                                *
 *                                                                            *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,     *
 *  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR    *
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR         *
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,     *
 *  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,       *
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 *  OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,  *
 *  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR   *
 *  OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,            *
 *  EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                        *
 ******************************************************************************/

package org.fidonet.echobase;

import org.fidonet.types.Link;
import org.fidonet.types.Message;

import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 10/3/12
 * Time: 7:24 AM
 */
public interface IBase {

    public boolean open();

    public boolean createArea(String areaName);

    public boolean createArea(String areaName, String description);

    public void addMessage(Message message, String areaname);

    public Iterator<Message> getMessages(String areaname);

    public Iterator<Message> getMessages(String areaname, long startMessage, long bundleSize);

    public Iterator<Message> getMessages(Link link);

    public Iterator<Message> getMessages(Link link, String areaname);

    public Iterator<Message> getMessages(Link link, String areaname, long startMessage, long bundleSize);

    public Message getMessage(String area, String id);

    public List<Message> getMessage(String id);

    public long getMessageSize(String areaname);

    public List<String> getAreas();

    public boolean isAreaExists(String name);

    public void setLinkLastMessage(Link link, String areaname, Long id);

    public boolean isDupe(Message message);

    public List<String> getSubscriptions(Link link);

    public void addSubscription(Link link, String area);

    public void close();

}
