package eu.lindenbaum.flex.rtmpt;

import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 * @author Paul Burlov paul.burlov@lindenbaum.eu
 * @created Juni 2013
 */
public class RtmptProxy {

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.out.println("Required arguments: <local_ip:port> <rtmp_host:port>");
      return;
    }
    String localAddress = StringUtils.substringBefore(args[0], ":");
    int localPort = Integer.parseInt(StringUtils.substringAfter(args[0], ":"));
    String remoteHost = StringUtils.substringBefore(args[1], ":");
    int remotePort = Integer.parseInt(StringUtils.substringAfter(args[1], ":"));
    System.out.println("Starting RTMPT proxy " + localAddress + ":" + localPort + " -> " + remoteHost + ":"
                       + remotePort);
    Server server = new Server(new InetSocketAddress(localAddress, localPort));
    MBeanContainer mbContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
    server.getContainer().addEventListener(mbContainer);
    server.addBean(mbContainer);
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
    context.setContextPath("/");
    server.setHandler(context);

    RtmptServlet.remoteHost = remoteHost;
    RtmptServlet.remotePort = remotePort;
    context.addServlet(new ServletHolder(new RtmptServlet()), "/*");
    server.start();
    server.join();

  }

}
