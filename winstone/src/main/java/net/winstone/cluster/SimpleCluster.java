/*
 * Copyright 2003-2006 Rick Knowles <winstone-devel at lists sourceforge net>
 * Distributed under the terms of either:
 * - the common development and distribution license (CDDL), v1.0; or
 * - the GNU Lesser General Public License, v2.1 or later
 */
package net.winstone.cluster;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import net.winstone.WinstoneResourceBundle;
import net.winstone.log.Logger;
import net.winstone.log.LoggerFactory;
import net.winstone.util.StringUtils;

import winstone.HostConfiguration;
import winstone.HostGroup;
import winstone.WebAppConfiguration;
import winstone.WinstoneSession;

/**
 * Represents a cluster of winstone containers.
 * 
 * @author <a href="mailto:rick_knowles@hotmail.com">Rick Knowles</a>
 * @version $Id: SimpleCluster.java,v 1.8 2006/08/10 06:38:31 rickknowles Exp $
 */
public class SimpleCluster implements Runnable, Cluster {

    protected Logger logger = LoggerFactory.getLogger(getClass());
    final int SESSION_CHECK_TIMEOUT = 100;
    final int HEARTBEAT_PERIOD = 5000;
    final int MAX_NO_OF_MISSING_HEARTBEATS = 3;
    final byte NODELIST_DOWNLOAD_TYPE = (byte) '2';
    final byte NODE_HEARTBEAT_TYPE = (byte) '3';
    private int controlPort;
    private String initialClusterNodes;
    private Map<String, Date> clusterAddresses;
    private boolean interrupted;

    /**
     * Builds a cluster instance
     */
    public SimpleCluster(Map<String, String> args, Integer controlPort) {
        this.interrupted = false;
        this.clusterAddresses = new HashMap<String, Date>();
        if (controlPort != null) {
            this.controlPort = controlPort.intValue();
        }

        // Start cluster init thread
        this.initialClusterNodes = (String) args.get("clusterNodes");
        Thread thread = new Thread(this, "Cluster monitor thread");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    public void destroy() {
        this.interrupted = true;
    }

    /**
     * Send a heartbeat every now and then, and remove any nodes that haven't responded in 3 heartbeats.
     */
    public void run() {
        // Ask each of the known addresses for their cluster lists, and build a
        // set
        if (this.initialClusterNodes != null) {
            StringTokenizer st = new StringTokenizer(this.initialClusterNodes, ",");
            while (st.hasMoreTokens() && !interrupted) {
                askClusterNodeForNodeList(st.nextToken());
            }
        }
        logger.info(StringUtils.replaceToken("Cluster initialised with [#0] nodes", Integer.toString(this.clusterAddresses.size())));


        while (!interrupted) {
            try {
                Set<String> addresses = new HashSet<String>(this.clusterAddresses.keySet());
                Date noHeartbeatDate = new Date(System.currentTimeMillis() - (MAX_NO_OF_MISSING_HEARTBEATS * HEARTBEAT_PERIOD));
                for (Iterator<String> i = addresses.iterator(); i.hasNext();) {
                    String ipPort = i.next();

                    Date lastHeartBeat = (Date) this.clusterAddresses.get(ipPort);
                    if (lastHeartBeat.before(noHeartbeatDate)) {
                        this.clusterAddresses.remove(ipPort);
                        logger.debug(StringUtils.replaceToken("Removing address from cluster node list: [#0]", ipPort));
                    } // Send heartbeat
                    else {
                        sendHeartbeat(ipPort);
                    }

                }
                Thread.sleep(HEARTBEAT_PERIOD);
            } catch (Throwable err) {
                logger.error("Error in cluster monitor thread", err);
            }
        }
        logger.info("Cluster monitor thread finished");
    }

    /**
     * Check if the other nodes in this cluster have a session for this sessionId.
     * 
     * @param sessionId The id of the session to check for
     * @return A valid session instance
     */
    public WinstoneSession askClusterForSession(String sessionId, WebAppConfiguration webAppConfig) {
        // Iterate through the cluster members
        Collection<String> addresses = new ArrayList<String>(clusterAddresses.keySet());
        Collection<ClusterSessionSearch> searchThreads = new ArrayList<ClusterSessionSearch>();
        for (Iterator<String> i = addresses.iterator(); i.hasNext();) {
            String ipPort = i.next();
            ClusterSessionSearch search = new ClusterSessionSearch(webAppConfig.getContextPath(), webAppConfig.getOwnerHostname(), sessionId, ipPort, this.controlPort);
            searchThreads.add(search);
        }

        // Wait until we get an answer
        WinstoneSession answer = null;
        String senderThread = null;
        boolean finished = false;
        while (!finished) {
            // Loop through all search threads. If finished, exit, otherwise
            // sleep
            List<ClusterSessionSearch> finishedThreads = new ArrayList<ClusterSessionSearch>();
            for (Iterator<ClusterSessionSearch> i = searchThreads.iterator(); i.hasNext();) {
                ClusterSessionSearch searchThread = (ClusterSessionSearch) i.next();
                if (!searchThread.isFinished()) {
                    continue;
                } else if (searchThread.getResult() == null) {
                    finishedThreads.add(searchThread);
                } else {
                    answer = searchThread.getResult();
                    senderThread = searchThread.getAddressPort();
                }
            }

            // Remove finished threads
            for (Iterator<ClusterSessionSearch> i = finishedThreads.iterator(); i.hasNext();) {
                searchThreads.remove(i.next());
            }

            if (searchThreads.isEmpty() || (answer != null)) {
                finished = true;
            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException err) {
                }
            }
        }

        // Once we have an answer, terminate all search threads
        for (Iterator<ClusterSessionSearch> i = searchThreads.iterator(); i.hasNext();) {
            ClusterSessionSearch searchThread = (ClusterSessionSearch) i.next();
            searchThread.destroy();
        }
        if (answer != null) {
            answer.activate(webAppConfig);
            logger.debug(StringUtils.replaceToken("Session transferred from: [#0]", senderThread));
        }
        return answer;
    }

    /**
     * Given an address, retrieve the list of cluster nodes and initialise dates
     * 
     * @param address The address to request a node list from
     */
    private void askClusterNodeForNodeList(String address) {
        try {
            int colonPos = address.indexOf(':');
            String ipAddress = address.substring(0, colonPos);
            String port = address.substring(colonPos + 1);
            Socket clusterListSocket = new Socket(ipAddress, Integer.parseInt(port));
            this.clusterAddresses.put(clusterListSocket.getInetAddress().getHostAddress() + ":" + port, new Date());
            InputStream in = clusterListSocket.getInputStream();
            OutputStream out = clusterListSocket.getOutputStream();
            out.write(NODELIST_DOWNLOAD_TYPE);
            out.flush();

            // Write out the control port
            ObjectOutputStream outControl = new ObjectOutputStream(out);
            outControl.writeInt(this.controlPort);
            outControl.flush();

            // For each node, add an entry to cluster nodes
            ObjectInputStream inData = new ObjectInputStream(in);
            int nodeCount = inData.readInt();
            for (int n = 0; n < nodeCount; n++) {
                this.clusterAddresses.put(inData.readUTF(), new Date());
            }

            inData.close();
            outControl.close();
            out.close();
            in.close();
            clusterListSocket.close();
        } catch (ConnectException err) {
            logger.debug(StringUtils.replaceToken("No cluster node detected at [#0] - ignoring", address));
        } catch (Throwable err) {
            logger.error(StringUtils.replaceToken("Error getting nodelist from: [#0]", address), err);
        }
    }

    /**
     * Given an address, send a heartbeat
     * 
     * @param address The address to request a node list from
     */
    private void sendHeartbeat(String address) {
        try {
            int colonPos = address.indexOf(':');
            String ipAddress = address.substring(0, colonPos);
            String port = address.substring(colonPos + 1);
            Socket heartbeatSocket = new Socket(ipAddress, Integer.parseInt(port));
            OutputStream out = heartbeatSocket.getOutputStream();
            out.write(NODE_HEARTBEAT_TYPE);
            out.flush();
            ObjectOutputStream outData = new ObjectOutputStream(out);
            outData.writeInt(this.controlPort);
            outData.close();
            heartbeatSocket.close();
            logger.debug(StringUtils.replaceToken("Heartbeat sent to: [#0]", address));
        } catch (ConnectException err) {/* ignore - 3 fails, and we remove */

        } catch (Throwable err) {
            logger.error(StringUtils.replaceToken("=Error sending heartbeat to: [#0]", address), err);
        }
    }

    /**
     * Accept a control socket request related to the cluster functions and process the request.
     * 
     * @param requestType A byte indicating the request type
     * @param in Socket input stream
     * @param outSocket output stream
     * @param webAppConfig Instance of the web app
     * @throws IOException
     */
    public void clusterRequest(byte requestType, InputStream in, OutputStream out, Socket socket, HostGroup hostGroup) throws IOException {
        if (requestType == ClusterSessionSearch.SESSION_CHECK_TYPE) {
            handleClusterSessionRequest(socket, in, out, hostGroup);
        } else if (requestType == NODELIST_DOWNLOAD_TYPE) {
            handleNodeListDownloadRequest(socket, in, out);
        } else if (requestType == NODE_HEARTBEAT_TYPE) {
            handleNodeHeartBeatRequest(socket, in);
        } else {
            logger.error("Unknown cluster request type: " + ((char) requestType));
        }
    }

    /**
     * Handles incoming socket requests for session search
     */
    public void handleClusterSessionRequest(Socket socket, InputStream in, OutputStream out, HostGroup hostGroup) throws IOException {
        // Read in a string for the sessionId
        ObjectInputStream inControl = new ObjectInputStream(in);
        int port = inControl.readInt();
        String ipPortSender = socket.getInetAddress().getHostAddress() + ":" + port;
        String sessionId = inControl.readUTF();
        String hostname = inControl.readUTF();
        HostConfiguration hostConfig = hostGroup.getHostByName(hostname);
        String webAppPrefix = inControl.readUTF();
        WebAppConfiguration webAppConfig = hostConfig.getWebAppByURI(webAppPrefix);
        ObjectOutputStream outData = new ObjectOutputStream(out);
        if (webAppConfig == null) {
            outData.writeUTF(ClusterSessionSearch.SESSION_NOT_FOUND);
        } else {
            WinstoneSession session = webAppConfig.getSessionById(sessionId, true);
            if (session != null) {
                outData.writeUTF(ClusterSessionSearch.SESSION_FOUND);
                outData.writeObject(session);
                outData.flush();
                if (inControl.readUTF().equals(ClusterSessionSearch.SESSION_RECEIVED)) {
                    session.passivate();
                }
                logger.debug(StringUtils.replaceToken("Session transferred to: [#0]", ipPortSender));
            } else {
                outData.writeUTF(ClusterSessionSearch.SESSION_NOT_FOUND);
            }
        }
        outData.close();
        inControl.close();
    }

    /**
     * Handles incoming socket requests for cluster node lists.
     */
    public void handleNodeListDownloadRequest(Socket socket, InputStream in, OutputStream out) throws IOException {
        // Get the ip and port of the requester, and make sure we don't send
        // that
        ObjectInputStream inControl = new ObjectInputStream(in);
        int port = inControl.readInt();
        String ipPortSender = socket.getInetAddress().getHostAddress() + ":" + port;
        List<String> allClusterNodes = new ArrayList<String>(this.clusterAddresses.keySet());
        List<String> relevantClusterNodes = new ArrayList<String>();
        for (Iterator<String> i = allClusterNodes.iterator(); i.hasNext();) {
            String node = i.next();
            if (!node.equals(ipPortSender)) {
                relevantClusterNodes.add(node);
            }
        }

        ObjectOutputStream outData = new ObjectOutputStream(out);
        outData.writeInt(relevantClusterNodes.size());
        outData.flush();
        for (Iterator<String> i = relevantClusterNodes.iterator(); i.hasNext();) {
            String ipPort = i.next();
            if (!ipPort.equals(ipPortSender)) {
                outData.writeUTF(ipPort);
            }
            outData.flush();
        }
        outData.close();
        inControl.close();
    }

    /**
     * Handles heartbeats. Just updates the date of this node's last heartbeat
     */
    public void handleNodeHeartBeatRequest(Socket socket, InputStream in) throws IOException {
        ObjectInputStream inData = new ObjectInputStream(in);
        int remoteControlPort = inData.readInt();
        inData.close();
        String ipPort = socket.getInetAddress().getHostAddress() + ":" + remoteControlPort;
        this.clusterAddresses.put(ipPort, new Date());
        logger.debug(StringUtils.replaceToken("Heartbeat received from: [#0]", ipPort));
    }
}