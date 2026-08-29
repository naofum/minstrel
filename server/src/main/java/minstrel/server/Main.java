package minstrel.server;

import com.sun.net.httpserver.HttpServer;
import minstrel.server.handler.ApiHandler;
import minstrel.server.handler.StaticHandler;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Entry point of the Minstrel Windows server.
 * <p>
 * Serves the Minstrel web app (HTML/JS/CSS) from {@code webRoot} and the
 * app data (library, thumbnails, extracted assets) from {@code dataRoot}
 * using only the JDK built-in {@link HttpServer}.
 * <p>
 * Usage: {@code java -jar minstrel-server.jar [port] [webRoot] [dataRoot]}
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8000;
        File webRoot = new File(args.length > 1 ? args[1] : new File("..", "web").getCanonicalPath());
        File dataRoot = new File(args.length > 2 ? args[2] : new File("..", "data").getCanonicalPath());

        webRoot = webRoot.getCanonicalFile();
        dataRoot = dataRoot.getCanonicalFile();

        if (!webRoot.isDirectory()) {
            System.err.println("Web root does not exist: " + webRoot);
            System.exit(1);
        }
        if (!dataRoot.isDirectory()) {
            dataRoot.mkdirs();
        }

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api", new ApiHandler(webRoot, dataRoot));
        server.createContext("/", new StaticHandler(webRoot, dataRoot));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("Minstrel Windows server running at http://127.0.0.1:" + port + "/");
        System.out.println("  webRoot  : " + webRoot);
        System.out.println("  dataRoot : " + dataRoot);
        System.out.println("Press Ctrl+C to stop.");
    }
}