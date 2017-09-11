package org.cyverse.war;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AllowSymLinkAliasChecker;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.File;
import java.lang.management.ManagementFactory;

import sun.security.krb5.Config;

/**
 * @author Dennis Roberts
 */
public class WarRunner implements Runnable {

    // The container include jar attribute and the pattern to scan.
    private static final String CONTAINER_INCLUDE_JAR_ATTRIBUTE
            = "org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern";
    private static final String CONTAINER_INCLUDE_JAR_PATTERN
            = ".*/[^/]*servlet-api-[^/]*\\.jar$" + "|"
            + ".*/javax.servlet.jsp.jstl-.*\\.jar$" + "|"
            + ".*/[^/]*taglibs.*\\.jar$";

    private int port = 8080;
    private String contextPath = "/";
    private String warPath = null;
    private String realmName = null;
    private String realmPath = null;

    private final String[] args;

    public WarRunner(final String[] args) {
        this.args = args;
    }

    private void printUsage(Options options) {
        new HelpFormatter().printHelp("java -jar war-runner.jar", options);
        System.exit(1);
    }

    private void parseArguments() {
        CommandLineParser parser = new DefaultParser();

        // Define the command-line options.
        Options options = new Options();
        options.addOption(
                "p", "port", true,
                "port number (default: " + port + ")"
        );
        options.addOption(
                "c", "context-path", true,
                "context path (default: " + contextPath + ")"
        );
        options.addOption(
                "f", "war-file", true,
                "WAR file path (required)"
        );
        options.addOption(
                "r", "realm-name", true,
                "Security realm name (required)"
        );
        options.addOption(
                "R", "realm-file", true,
                "Realm configuration file path (required)"
        );

        // Parse the command line.
        CommandLine line = null;
        try {
            line = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            printUsage(options);
        }

        // Extract the arguments from the parsed command line.
        extractPort(options, line);
        extractContextPath(line);
        extractWarPath(options, line);
        extractRealmName(options, line);
        extractRealmFile(options, line);
    }

    private void extractRealmFile(Options options, CommandLine line) {
        if (line.hasOption("realm-file")) {
            File realmFile = new File(line.getOptionValue("realm-file"));
            if (!realmFile.exists()) {
                System.out.println("Realm file does not exist: " + realmFile.getAbsolutePath());
                printUsage(options);
            }
            realmPath = realmFile.getAbsolutePath();
        } else {
            System.out.println("The --realm-file (-R) argument is required.");
            printUsage(options);
        }
    }

    private void extractRealmName(Options options, CommandLine line) {
        if (line.hasOption("realm-name")) {
            realmName = line.getOptionValue("realm-name");
        } else {
            System.out.println("The --realm-name (-r) argument is required.");
            printUsage(options);
        }
    }

    private void extractWarPath(Options options, CommandLine line) {
        if (line.hasOption("war-file")) {
            File warFile = new File(line.getOptionValue("war-file"));
            if (!warFile.exists()) {
                System.out.println("WAR file does not exist: " + warFile.getAbsolutePath());
                printUsage(options);
            }
            warPath = warFile.getAbsolutePath();
        } else {
            System.out.println("The --war-file (-f) argument is required.");
            printUsage(options);
        }
    }

    private void extractContextPath(CommandLine line) {
        if (line.hasOption("context-path")) {
            contextPath = line.getOptionValue("context-path");
        }
    }

    private void extractPort(Options options, CommandLine line) {
        if (line.hasOption("port")) {
            try {
                port = Integer.parseInt(line.getOptionValue("port"));
            } catch (NumberFormatException _) {
                System.out.println("Invalid port number: " + line.getOptionValue("port"));
                printUsage(options);
            }
        }
    }

    private void runServer(Server server) throws Exception {

        // Start the server.
        try {
            server.start();
            server.dump(System.out);
        } catch (Exception e) {
            System.out.println("Unexpected server error: " + e);
            server.stop();
            throw e;
        }

        // Wait for the server to shut down.
        try {
            server.join();
        } catch (InterruptedException e) {
            System.out.println("Interrupt received; shutting down.");
            Thread.currentThread().interrupt();
        }
    }

    public void run() {
        // Parse the command-line options.
        parseArguments();

        System.out.println("Port: " + port);
        System.out.println("Context Path: " + contextPath);
        System.out.println("WAR Path: " + warPath);

        // Create the server.
        Server server = new Server(port);

        // Create the login service.
        LoginService loginService = new HashLoginService(realmName, realmPath);
        server.addBean(loginService);

        // Set up JMX.
        MBeanContainer mBeanContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
        server.addBean(mBeanContainer);

        // Set up the web application.
        WebAppContext webapp = new WebAppContext();
        webapp.setContextPath(contextPath);
        webapp.setWar(warPath);
        webapp.addAliasCheck(new AllowSymLinkAliasChecker());
        webapp.setThrowUnavailableOnStartupException(true);

        // Enable the annotation configuration.
        Configuration.ClassList classList = Configuration.ClassList.setServerDefault(server);
        classList.addBefore(
                "org.eclipse.jetty.webapp.JettyWebXmlConfiguration",
                "org.eclipse.jetty.annotations.AnnotationConfiguration"
        );

        // Specify the the include JAR pattern for JSP files.
        webapp.setAttribute(CONTAINER_INCLUDE_JAR_ATTRIBUTE, CONTAINER_INCLUDE_JAR_PATTERN);

        // Add the web application to the server.
        server.setHandler(webapp);

        // Run the server.
        try {
            runServer(server);
        } catch (Exception e) {
            System.out.println("Error encountered while running the server: " + e);
         }
    }

    public static void main(String[] args) {
        new WarRunner(args).run();
    }
}
