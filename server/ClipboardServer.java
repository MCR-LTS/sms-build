import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;
import java.util.Locale;

public class ClipboardServer {
    private static final int PORT = 8888;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            serverSocket.setReuseAddress(true);
            System.out.println("服务端启动！正在监听端口 " + PORT);
            System.out.println("本机 IP 地址 ");
            printRealIp();
            
            while (true) {
                try (Socket clientSocket = serverSocket.accept();
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))) {
                    String message = reader.readLine();

                    if (message != null && !message.isEmpty()) {
                        System.out.println("接收到消息: " + message);
                        setClipboard(message);
                    }
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printRealIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        System.out.println("  " + addr.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
        }
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
        return osName.toLowerCase(Locale.ROOT).contains("mac");
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
