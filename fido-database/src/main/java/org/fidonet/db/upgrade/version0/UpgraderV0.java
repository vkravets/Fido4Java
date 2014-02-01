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

package org.fidonet.db.upgrade.version0;

import org.fidonet.db.OrmManager;
import org.fidonet.db.upgrade.Upgrader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 2/1/14
 * Time: 2:38 PM
 */
public class UpgraderV0 implements Upgrader {

    private static final Logger logger = LoggerFactory.getLogger(UpgraderV0.class);

    @Override
    public boolean upgrade(OrmManager oldManager, OrmManager newManager) {
        logger.info("Upgrade Links information....");
        if (!upgradeLinks(oldManager, newManager)) return false;
        logger.info("Upgrade Areas information....");
        if (!upgradeEchoareas(oldManager, newManager)) return false;
        logger.info("Upgrade Subscriptions information....");
        if (!upgradeSubcriptions(oldManager, newManager)) return false;
        logger.info("Upgrade Netmail information....");
        if (!upgradeNetmails(oldManager, newManager)) return false;
        logger.info("Upgrade Echomail information....");
        return upgradeEchomails(oldManager, newManager);
    }

    @Override
    public long getVersion() {
        return 0;
    }

    private boolean upgradeEchomails(OrmManager oldManager, OrmManager newManager) {
        return Helper.upgradeObjects(Echomail.class, oldManager, org.fidonet.db.objects.Echomail.class, newManager, new ObjectUpgrader<Echomail, org.fidonet.db.objects.Echomail>() {
            @Override
            public void upgrade(Echomail oldObject, org.fidonet.db.objects.Echomail newObject) {

            }

            @Override
            public void onException(Echomail oldObject, Exception ex) {
                logger.error("Error upgrade echomail. Details {}", oldObject, ex);
            }
        }, true);
    }

    private boolean upgradeNetmails(OrmManager oldManager, OrmManager newManager) {
        return Helper.upgradeObjects(Netmail.class, oldManager, org.fidonet.db.objects.Netmail.class, newManager, new ObjectUpgrader<Netmail, org.fidonet.db.objects.Netmail>() {
            @Override
            public void upgrade(Netmail oldObject, org.fidonet.db.objects.Netmail newObject) {

            }

            @Override
            public void onException(Netmail oldObject, Exception ex) {
                logger.error("Error upgrade mail. Details {}", oldObject, ex);
            }
        }, true);
    }

    private boolean upgradeSubcriptions(OrmManager oldManager, OrmManager newManager) {
        return Helper.upgradeObjects(Subscription.class, oldManager, org.fidonet.db.objects.Subscription.class, newManager, new ObjectUpgrader<Subscription, org.fidonet.db.objects.Subscription>() {
            @Override
            public void upgrade(Subscription oldObject, org.fidonet.db.objects.Subscription newObject) {

            }

            @Override
            public void onException(Subscription oldObject, Exception ex) {
                logger.error("Error subscription mail. Details {}", oldObject, ex);
            }
        }, true);
    }

    private boolean upgradeEchoareas(OrmManager oldManager, OrmManager newManager) {
        return Helper.upgradeObjects(Echoarea.class, oldManager, org.fidonet.db.objects.Echoarea.class, newManager, new ObjectUpgrader<Echoarea, org.fidonet.db.objects.Echoarea>() {
            @Override
            public void upgrade(Echoarea oldObject, org.fidonet.db.objects.Echoarea newObject) {
                newObject.setName(oldObject.getName());
                newObject.setDescription(oldObject.getDescription());
            }

            @Override
            public void onException(Echoarea oldObject, Exception ex) {
                logger.error("Error upgrade {} area", oldObject.getName(), ex);
            }
        }, true);
    }

    private boolean upgradeLinks(OrmManager oldManager, OrmManager newManager) {
        return Helper.upgradeObjects(ConfigurationLink.class, oldManager, org.fidonet.db.objects.ConfigurationLink.class, newManager, new ObjectUpgrader<ConfigurationLink, org.fidonet.db.objects.ConfigurationLink>() {
            @Override
            public void upgrade(ConfigurationLink oldObject, org.fidonet.db.objects.ConfigurationLink newObject) {
                newObject.setAddress(oldObject.getAddress());
                newObject.setHost(oldObject.getHost());
                newObject.setPort(oldObject.getPort());
                newObject.setPassword(oldObject.getPassword());
                newObject.setPacket_password(oldObject.getPacket_password());
                newObject.setFlags(oldObject.getFlags());
            }

            @Override
            public void onException(ConfigurationLink oldObject, Exception ex) {
                logger.error("Error upgrade {} area", oldObject.getAddress(), ex);
            }
        }, true);
    }
}
