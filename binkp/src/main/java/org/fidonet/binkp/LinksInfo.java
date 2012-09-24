package org.fidonet.binkp;

import org.fidonet.types.Link;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/24/12
 * Time: 4:21 PM
 */
public class LinksInfo {
    private Link curLink = null;
    private List<Link> links = new ArrayList<Link>();

    public LinksInfo() {
    }

    public LinksInfo(Link curLink) {
        setCurLink(curLink);
    }

    public LinksInfo(List<Link> links) {
        setLinks(links);
    }

    public LinksInfo(Link curLink, List<Link> links) {
        setCurLink(curLink);
        setLinks(links);
    }

    public Link getCurLink() {
        return curLink;
    }

    public void setCurLink(Link curLink) {
        this.curLink = curLink;
    }

    public List<Link> getLinks() {
        return links;
    }

    public void setLinks(List<Link> links) {
        if (links != null)
            this.links = links;
    }
}
