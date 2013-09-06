package eu.lindenbaum.flex.rtmpt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * @author Paul Burlov paul.burlov@lindenbaum.eu
 * @created Juni 2013
 */
public class RtmptServlet extends HttpServlet {
  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  private static final long REAPER_PERIOD = 1000;
  private static final long INACTIVITY_TIMEOUT = 3000;
  private static final int TCP_CONNECT_TIMEOUT = 500;
  private static final String CONTENT_TYPE = "application/x-fcs";
  static final ConcurrentHashMap<String, RTMPConnection> connections = new ConcurrentHashMap<String, RTMPConnection>();
  static final ThreadLocal<byte[]> byteBufferRef = new ThreadLocal<byte[]>() {

    @Override
    protected byte[] initialValue() {
      return new byte[8192];
    }
  };

  static final Timer connectionReaper = new Timer("RTMP Connection Reaper", true);

  static public String remoteHost = "localhost";
  static public int remotePort = 1935;
  static {
    connectionReaper.schedule(new TimerTask() {

      @Override
      public void run() {
        for (Map.Entry<String, RTMPConnection> entry : connections.entrySet()) {
          if (System.currentTimeMillis() - entry.getValue().getLastAccessTime() > INACTIVITY_TIMEOUT) {
            System.out.println("Close connection for session: " + entry.getKey());
            closeConnection(entry.getKey());
          }
        }

      }
    }, REAPER_PERIOD, REAPER_PERIOD);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    if (req.getPathInfo().contains("status")) {
      printStatus(resp);
    }
  }

  private void printStatus(HttpServletResponse resp) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("RTMP host: " + remoteHost + ":" + remotePort + " Reachable: " + checkTargetHostReachable()
              + "\n");
    sb.append("Open sessions: " + connections.size() + "\n");
    returnMessage(sb.toString(), resp);
  }

  static boolean checkTargetHostReachable() {
    try {
      Socket socket = openSocket();
      socket.close();
      return true;
    }
    catch (Exception e) {
      return false;
    }
  }

  static Socket openSocket() throws IOException {
    Socket sock = new Socket();
    sock.connect(new InetSocketAddress(remoteHost, remotePort), TCP_CONNECT_TIMEOUT);
    return sock;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
                                                                         IOException {
    String path = req.getPathInfo();
    // System.out.println(path);
    char p = path.charAt(1);
    switch (p) {
      case 'f': {
        // fcs/ident2
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        resp.setHeader("Connection", "Keep-Alive");
        resp.setHeader("Cache-Control", "no-cache");
        return;
      }
      case 'o': {
        // open
        String sessionId = generateSessionId();
        try {
          connections.put(sessionId, new RTMPConnection(openSocket()));
        }
        catch (IOException ioe) {
          handleBadRequest(ioe.getMessage(), resp);
          return;
        }
        System.out.println("Open connection for session: " + sessionId);
        returnMessage(sessionId + "\n", resp);
        return;
      }
      case 's': {
        String sessionId = path.split("/")[2];
        RTMPConnection con = connections.get(sessionId);
        if (con == null) {
          handleBadRequest("unknown session id: " + sessionId, resp);
        }
        IOUtils.copy(req.getInputStream(), con.socket.getOutputStream());
      }
      case 'i': {
        // idle
        /*
         * Verfuegbare Daten von Socket lesen und an Request weiterreichen
         */
        String sessionId = path.split("/")[2];
        RTMPConnection con = connections.get(sessionId);
        if (con == null) {
          handleBadRequest("unknown session id: " + sessionId, resp);
        }
        int readed = 0;
        byte[] buf = byteBufferRef.get();
        if (con.socket.getInputStream().available() > 0) {
          readed = con.socket.getInputStream().read(buf,
                                                    0,
                                                    Math.min(con.socket.getInputStream().available(),
                                                             buf.length));
        }
        if (readed < 0) {
          closeConnection(sessionId);
          returnCloseMessage(resp);
          return;
        }
        if (readed == 0) {
          con.increaseEmptyMessages();
        }
        else {
          con.resetEmptyMessagesCounter();
        }
        returnMessage(con.getCurrentDelay(), buf, readed, resp);
        break;
      }
      case 'c': {
        // close
        String sessionId = path.split("/")[2];
        closeConnection(sessionId);
        returnCloseMessage(resp);
        return;
      }
      default:
        handleBadRequest("invalid path " + path, resp);
    }
  }

  private String generateSessionId() {
    String sessionId = "";
    do {
      sessionId = RandomStringUtils.randomAlphanumeric(16);
    } while (connections.containsKey(sessionId));
    return sessionId;
  }

  private void returnCloseMessage(HttpServletResponse resp) throws IOException {
    returnMessage((byte) 0, resp);
  }

  static protected void closeConnection(String sessionId) {
    RTMPConnection con = connections.remove(sessionId);
    closeConnection(con);
  }

  static protected void closeConnection(RTMPConnection con) {
    if (con != null) {
      try {
        con.socket.close();
      }
      catch (Exception egal) {
        // egal
      }
    }
  }

  protected void returnMessage(int message, HttpServletResponse resp) throws IOException {
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setHeader("Connection", "Keep-Alive");
    resp.setHeader("Cache-Control", "no-cache");
    resp.setContentType(CONTENT_TYPE);
    resp.setContentLength(1);
    resp.getOutputStream().write(message);
    resp.flushBuffer();
  }

  protected void returnMessage(String message, HttpServletResponse resp) throws IOException {
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setHeader("Connection", "Keep-Alive");
    resp.setHeader("Cache-Control", "no-cache");
    resp.setContentType("text/plain");
    resp.getWriter().write(message);
    resp.flushBuffer();
  }

  protected void returnMessage(int delay, byte[] data, int length, HttpServletResponse resp) throws IOException {
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setHeader("Connection", "Keep-Alive");
    resp.setHeader("Cache-Control", "no-cache");
    resp.setContentType(CONTENT_TYPE);
    resp.setContentLength(length + 1);
    resp.getOutputStream().write(delay);
    resp.getOutputStream().write(data, 0, length);
    resp.flushBuffer();
  }

  /**
   * Return an error message to the client.
   * 
   * @param message
   *            Message
   * @param resp
   *            Servlet response
   * @throws IOException
   *             I/O exception
   */
  protected void handleBadRequest(String message, HttpServletResponse resp) throws IOException {
    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    resp.setHeader("Connection", "Keep-Alive");
    resp.setHeader("Cache-Control", "no-cache");
    resp.setContentType("text/plain");
    resp.getWriter().write(message);
    resp.flushBuffer();
  }

}
