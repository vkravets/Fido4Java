package org.fidonet.io;

import org.fidonet.io.exception.ProtocolException;
import org.fidonet.logger.ILogger;
import org.fidonet.logger.LoggerFactory;
import org.fidonet.types.Link;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author kreon
 * 
 */
public class Connector {
    private Socket clientSocket;
    private ProtocolConnector connector;
    private List<Message> messages;
    private Link link;
    private static final ILogger logger = LoggerFactory.getLogger(Connector.class);

    public Connector(ProtocolConnector connector) throws ProtocolException {
        this.connector = connector;
        messages = new ArrayList<Message>();
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public Message[] getReceived() {
        return connector.getReceived();
    }

    public Link getLink() {
        return link;
    }

    public void setLink(Link link) {
        this.link = link;
        logger.debug(String.format("Соединение с %s  установлено, получаем сообщения", link.getAddr()));
        // Отправка сообщений
        //List<Message> messages = FtnTosser.getMessagesForLink(link);
        //this.messages = messages;
    }

    private void doSocket(Socket clientSocket) throws Exception {
        int index = 0;
        while (!clientSocket.isClosed()) {
            boolean flag = true;
            if (clientSocket.getInputStream().available() > 0) {
                connector.avalible(clientSocket.getInputStream());
                flag = true;
            }

            Frame[] frames = connector.getFrames();
            if (frames != null && frames.length > 0) {
                for (Frame frame : frames) {
                    clientSocket.getOutputStream().write(frame.getBytes());
                }
                flag = true;
            }

            if (connector.canSend()) {
                if (messages.size() > index) {
                    connector.send(messages.get(index++));
                    flag = true;
                } else {
                    connector.eob();
                }
                continue;
            }
            if (connector.closed()) {
                clientSocket.close();
            }
            if (!flag) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }
        }
        messages = new ArrayList<Message>();
    }

    public void connect(Link link) throws ProtocolException {
        if (link == null) {
            throw new ProtocolException("Для connect() надо указать линк");
        }
        this.link = link;
        connector.reset();
        connector.initOutgoing(this);
        try {
            clientSocket = new Socket(link.getHostAddress(), link.getPort());
            doSocket(clientSocket);
        } catch (UnknownHostException e) {
            throw new ProtocolException("Неизвестный хост:" + link.getHostAddress());
        } catch (Exception e) {
            e.printStackTrace();
            throw new ProtocolException(e.getLocalizedMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void accept(Socket clientSocket) throws ProtocolException {
        connector.reset();
        connector.initIncoming(this);
        try {
            doSocket(clientSocket);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
