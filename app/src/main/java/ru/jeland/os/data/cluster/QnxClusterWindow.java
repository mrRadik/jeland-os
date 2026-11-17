package ru.jeland.os.data.cluster;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.apache.commons.net.ProtocolCommandEvent;
import org.apache.commons.net.ProtocolCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPCmd;
import org.apache.commons.net.ftp.FTPReply;

/** The short ClusterApp visibility command sent over the OEM passive FTP channel. */
final class QnxClusterWindow {
    private static final String HOST = "192.168.8.1";
    private static final int PORT = 60005;
    private static final String BEGIN = "__QNX_FTP_OUTPUT_BEGIN__";
    private static final String END = "__QNX_FTP_OUTPUT_END__:";
    private final String host;
    private final int port;
    private final RawFtpClient client = new RawFtpClient();
    private boolean loggedIn;
    // The installed server uses one passive data port; every request remains finite.
    private static final java.util.concurrent.locks.ReentrantLock DATA_PORT = new java.util.concurrent.locks.ReentrantLock(true);
    private Consumer<String> report = line -> {};

    QnxClusterWindow() { this(HOST, PORT); }

    QnxClusterWindow(String host, int port) {
        this.host = host; this.port = port;
        client.setConnectTimeout(3000);
        client.setDefaultTimeout(5000);
        client.addProtocolCommandListener(new ProtocolCommandListener() {
            public void protocolCommandSent(ProtocolCommandEvent event) {
                report.accept("QNX_FTP_SEND " + event.getMessage().trim());
            }
            public void protocolReplyReceived(ProtocolCommandEvent event) {
                report.accept("QNX_FTP_REPLY " + event.getMessage().trim());
            }
        });
    }

    synchronized void setVisible(boolean visible, Consumer<String> reporter) throws IOException {
        acquireDataPort();
        try {
            report = reporter;
            String command = "/bin/screencmd setiv ClusterApp SCREEN_PROPERTY_VISIBLE " +
                (visible ? "1" : "0") + " && /bin/screencmd apply";
            report.accept("QNX_VISIBILITY_REQUEST visible=" + visible + " command=" + command);
            connect();
            String argument = ";echo " + BEGIN + ";" + command +
                ";QNX_FTP_RESULT=$?;echo " + END + "$QNX_FTP_RESULT";
            Socket data = client.openList(argument);
            if (data == null) throw new IOException("FTP LIST: " + client.getReplyString().trim());
            String output;
            try (Socket transfer = data) {
                transfer.setSoTimeout(5000);
                output = read(transfer.getInputStream());
            }
            boolean completed = client.completePendingCommand();
            String normalized = output.replace("\r\n", "\n");
            int begin = normalized.indexOf(BEGIN);
            int outputStart = begin < 0 ? 0 : begin + BEGIN.length();
            if (begin >= 0 && outputStart < normalized.length() && normalized.charAt(outputStart) == '\n') outputStart++;
            int end = begin < 0 ? -1 : normalized.indexOf(END, outputStart);
            int status = -1;
            if (end >= 0) {
                int start = end + END.length();
                int newline = normalized.indexOf('\n', start);
                try {
                    status = Integer.parseInt(normalized.substring(start,
                        newline < 0 ? normalized.length() : newline).trim());
                } catch (NumberFormatException ignored) {}
            }
            report.accept("QNX_VISIBILITY_RESULT visible=" + visible + " transfer=" + completed +
                " ftp=" + client.getReplyCode() + " exit=" + status);
            if (begin >= 0 && end > begin) {
                String content = normalized.substring(outputStart, end);
                if (content.endsWith("\n")) content = content.substring(0, content.length() - 1);
                report.accept("QNX_OUTPUT " + content);
            } else {
                report.accept("QNX_OUTPUT_UNFRAMED " + normalized.substring(outputStart));
            }
            if (!completed || status != 0) throw new IOException("QNX visibility not confirmed: transfer="+completed+" exit="+status);
        } finally { DATA_PORT.unlock(); }
    }

    /** Fixed read-only snapshot command. No ?wait, file writes or vehicle SETs. */
    synchronized String readTelemetry() throws IOException {
        acquireDataPort();
        try {
            connect();
            String command = ";echo __JOS_NATIVE_BEGIN__;echo __JOS_GAUGE__;" +
                "/bin/cat /tmp/pps/spi_service/a000 2>&1;echo __JOS_GAUGE_EXIT__:$?;echo __JOS_WARNINGS__;" +
                "/bin/cat /tmp/pps/spi_service/a00d 2>&1;echo __JOS_WARNINGS_EXIT__:$?;echo __JOS_COMPUTER__;" +
                "/bin/cat /tmp/pps/spi_service/a006 2>&1;echo __JOS_COMPUTER_EXIT__:$?;echo __JOS_AUXILIARY__;" +
                "/bin/cat /tmp/pps/spi_service/a005 2>&1;echo __JOS_AUXILIARY_EXIT__:$?;echo __JOS_TRIP__;" +
                "/bin/cat /tmp/pps/spi_service/a001 2>&1;echo __JOS_TRIP_EXIT__:$?;echo __JOS_NATIVE_END__";
            Socket data = client.openList(command);
            if (data == null) throw new IOException("Native PPS LIST: " + client.getReplyCode());
            String output;
            try (Socket transfer = data) {
                transfer.setSoTimeout(1500);
                output = read(transfer.getInputStream());
            }
            if (!client.completePendingCommand()) throw new IOException("Native PPS transfer incomplete");
            output = output.replace("\r\n", "\n");
            int start = output.indexOf("__JOS_NATIVE_BEGIN__\n");
            int end = output.indexOf("__JOS_NATIVE_END__", start < 0 ? 0 : start);
            if (start < 0 || end < start) throw new IOException("Native PPS framing missing");
            return output.substring(start + "__JOS_NATIVE_BEGIN__\n".length(), end);
        } finally { DATA_PORT.unlock(); }
    }

    void close() { disconnect(); }

    private static void acquireDataPort() throws IOException {
        try { DATA_PORT.lockInterruptibly(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException("QNX request interrupted",error); }
    }

    private void connect() throws IOException {
        if (loggedIn && client.isConnected() && client.isAvailable()) return;
        disconnect();
        report.accept("QNX_FTP_CONNECT host=" + HOST + " port=" + PORT);
        client.connect(host, port);
        client.setSoTimeout(5000);
        if (!FTPReply.isPositiveCompletion(client.getReplyCode())) {
            String reply = client.getReplyString();
            disconnect();
            throw new IOException("FTP connect: " + reply);
        }
        if (!client.login("anonymous", "anonymous")) {
            String reply = client.getReplyString();
            disconnect();
            throw new IOException("FTP login: " + reply);
        }
        client.enterLocalPassiveMode();
        client.setFileType(FTP.ASCII_FILE_TYPE);
        loggedIn = true;
    }

    private void disconnect() {
        loggedIn = false;
        if (!client.isConnected()) return;
        try { client.disconnect(); }
        catch (IOException error) { report.accept("QNX_DISCONNECT_ERROR " + error); }
    }

    private static String read(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (output.size() + count > 32768) throw new IOException("QNX response too large");
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static final class RawFtpClient extends FTPClient {
        Socket openList(String argument) throws IOException {
            return _openDataConnection_(FTPCmd.LIST, argument);
        }
    }
}
