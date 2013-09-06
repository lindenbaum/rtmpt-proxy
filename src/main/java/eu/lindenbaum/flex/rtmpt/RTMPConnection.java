package eu.lindenbaum.flex.rtmpt;

import java.net.Socket;

/**
 * @author Paul Burlov paul.burlov@lindenbaum.eu
 * @created Juni 2013
 */
public class RTMPConnection {
  private static final int START_DELAY = 1;
  private static final int MAX_DELAY = 21;
  private static final int DELAY_INCREASE_THRESHOLD = 10;
  final Socket socket;
  private int currentDelay = START_DELAY;
  private int emptyMessages;
  private long lastAccessTime = System.currentTimeMillis();

  public RTMPConnection(Socket sock) {
    socket = sock;
  }

  public void increaseEmptyMessages() {
    if (++emptyMessages % DELAY_INCREASE_THRESHOLD == 0) {
      emptyMessages = 0;
      currentDelay = Math.min(currentDelay + 4, MAX_DELAY);
    }
    lastAccessTime = System.currentTimeMillis();
  }

  public void resetEmptyMessagesCounter() {
    emptyMessages = 0;
    currentDelay = START_DELAY;
    lastAccessTime = System.currentTimeMillis();
  }

  public byte getCurrentDelay() {
    return (byte) currentDelay;
  }

  public long getLastAccessTime() {
    return lastAccessTime;
  }

}
