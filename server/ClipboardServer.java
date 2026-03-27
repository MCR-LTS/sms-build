import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;
import java.util.Locale;

public class ClipboardServer {
    private static final int PORT = 8888;
    private static final int DISCOVERY_PORT = 9999;
    private static final String MAC_NAME = "mac";
    private static final String DISCOVER_MESSAGE = "SMSSYNC_DISCOVER";
    private static final String SERVER_RESPONSE_PREFIX = "SMSSYNC_SERVER";

    public static void main(String[] args) {
        startDiscoveryServer();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            serverSocket.setReuseAddress(true);
            System.out.println("服务端启动！正在监听端口 " + PORT);
            System.out.println("本机 IP 地址 ");
            printRealIp();
            
            while (true) {
                handleClient(serverSocket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void startDiscoveryServer() {
        Thread discoveryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (DatagramSocket discoverySocket = new DatagramSocket(DISCOVERY_PORT)) {
                    discoverySocket.setBroadcast(true);
                    System.out.println("自动发现服务启动！正在监听端口 " + DISCOVERY_PORT);

                    byte[] buffer = new byte[256];
                    while (true) {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        discoverySocket.receive(packet);
                        handleDiscoveryRequest(discoverySocket, packet);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        discoveryThread.setDaemon(true);
        discoveryThread.start();
    }

    private static void handleDiscoveryRequest(DatagramSocket discoverySocket, DatagramPacket packet) {
        try {
            String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
            if (!DISCOVER_MESSAGE.equals(message)) {
                return;
            }

            String serverIp = getLocalIpForClient(packet.getAddress());
            if (serverIp == null || serverIp.isEmpty()) {
                return;
            }

            String response = SERVER_RESPONSE_PREFIX + "|" + serverIp + "|" + PORT;
            byte[] responseData = response.getBytes(StandardCharsets.UTF_8);
            DatagramPacket responsePacket = new DatagramPacket(
                    responseData,
                    responseData.length,
                    packet.getAddress(),
                    packet.getPort()
            );

            discoverySocket.send(responsePacket);
            System.out.println("已回复自动发现请求: " + serverIp);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    private static void handleClient(ServerSocket serverSocket) {
        try (Socket clientSocket = serverSocket.accept();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))) {
            String message = reader.readLine();
            if (message == null || message.isEmpty()) {
                return;
            }

            System.out.println("接收到消息: " + message);
            setClipboard(message);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    private static String getLocalIpForClient(InetAddress clientAddress) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(clientAddress, DISCOVERY_PORT);
            InetAddress localAddress = socket.getLocalAddress();
            if (localAddress instanceof Inet4Address && !localAddress.isLoopbackAddress()) {
                return localAddress.getHostAddress();
            }
        } catch (Exception e) {
        }

        return getFirstIpv4Address();
    }

    private static void printRealIp() {
        String firstIp = getFirstIpv4Address();
        if (firstIp != null) {
            System.out.println("  " + firstIp);
        }

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (!ip.equals(firstIp)) {
                            System.out.println("  " + ip);
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    private static String getFirstIpv4Address() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
        }

        return null;
    }

    private static void setClipboard(String text) {
        if (isMac()) {
            setClipboardOnMac(text);
        } else {
            StringSelection selection = new StringSelection(text);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
        }
        System.out.println("成功复制到剪贴板！");
    }

    private static boolean isMac() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase(Locale.ROOT).contains(MAC_NAME);
    }

    private static void setClipboardOnMac(String text) {
        try {
            Process process = new ProcessBuilder("pbcopy").start();
            try (OutputStream outputStream = process.getOutputStream()) {
                outputStream.write(text.getBytes(StandardCharsets.UTF_8));
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("pbcopy exited with code " + exitCode);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
