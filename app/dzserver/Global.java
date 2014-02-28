package dzserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.framework.util.Util;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.launch.Framework;

import play.Application;
import play.GlobalSettings;
import play.Logger;
import play.Play;

import com.github.ddth.frontapi.ApiParams;
import com.github.ddth.frontapi.IApi;
import com.github.ddth.frontapi.IApiRegistry;
import com.github.ddth.frontapi.impl.ApiRegistry;
import com.github.ddth.frontapi.impl.ThriftApiServer;
import com.github.ddth.frontapi.internal.Activator;
import com.github.ddth.frontapi.osgi.Constants;

import dzserver.utils.FelixOsgiUtils;

public class Global extends GlobalSettings {
    static {
        System.setProperty("java.awt.headless", "true");

        // default environment
        System.setProperty("spring.profiles.active", "development");
        System.setProperty("logger.resource", "logger-development.xml");
    }

    // /**
    // * {@inheritDoc}
    // */
    // @Override
    // @SuppressWarnings("unchecked")
    // public <T extends EssentialFilter> Class<T>[] filters() {
    // return new Class[] { GzipFilter.class };
    // }

    private void initEnv() {
        File appHome = dzserver.Application.applicationHomeDir();
        File tempDir = new File(appHome, "tmp");
        FileUtils.deleteQuietly(tempDir);
        tempDir.mkdirs();

        if (Play.isProd()) {
            System.setProperty("spring.profiles.active", "production");
            System.setProperty("logger.resource", "logger-production.xml");
        } else if (Play.isTest()) {
            System.setProperty("spring.profiles.active", "test");
            System.setProperty("logger.resource", "logger-test.xml");
        } else {
            System.setProperty("spring.profiles.active", "development");
            System.setProperty("logger.resource", "logger-development.xml");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStart(Application app) {
        initEnv();

        super.onStart(app);

        try {
            init();
        } catch (Exception e) {
            try {
                destroy();
            } catch (Exception e1) {
                throw new RuntimeException(e1);
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStop(Application app) {
        try {
            destroy();
        } catch (Exception e) {
            Logger.warn(e.getMessage(), e);
        }

        super.onStop(app);
    }

    private void init() throws Exception {
        initFelix();
        initApiRegistry();
        initThriftServer();
    }

    private void destroy() throws Exception {
        try {
            destroyThriftServer();
        } catch (Exception e) {
            Logger.error(e.getMessage(), e);
        }

        try {
            destroyFelix();
        } catch (Exception e) {
            Logger.error(e.getMessage(), e);
        }

        try {
            destroyApiRegistry();
        } catch (Exception e) {
            Logger.warn(e.getMessage(), e);
        }
    }

    private ThriftApiServer thriftApiServer;

    private void initThriftServer() {
        boolean thriftServerEnabled = false;
        try {
            thriftServerEnabled = Boolean.parseBoolean(dzserver.Application
                    .staticConfigString(Activator.PROP_THRIFT_SERVER_ENABLED));
        } catch (Exception e) {
            thriftServerEnabled = false;
        }
        if (thriftServerEnabled) {
            int thriftPort = ThriftApiServer.DEFAULT_THRIFT_SERVER_PORT;
            try {
                thriftPort = Integer.parseInt(dzserver.Application
                        .staticConfigString(Activator.PROP_THRIFT_SERVER_PORT));
            } catch (Exception e) {
                thriftPort = ThriftApiServer.DEFAULT_THRIFT_SERVER_PORT;
            }
            Logger.info("API Thrift Server enabled, port " + thriftPort + ".");

            thriftApiServer = new ThriftApiServer(apiRegistry);
            int thriftMaxFrameSize = Integer.parseInt(dzserver.Application
                    .staticConfigString(Activator.PROP_THRIFT_MAX_FRAME_SIZE));
            long thriftMaxReadBufferSize = Long.parseLong(dzserver.Application
                    .staticConfigString(Activator.PROP_THRIFT_MAX_READ_BUFFER_SIZE));
            int thriftClientTimeout = Integer.parseInt(dzserver.Application
                    .staticConfigString(Activator.PROP_THRIFT_CLIENT_TIMEOUT));
            thriftApiServer.setClientTimeoutMillisecs(thriftClientTimeout)
                    .setMaxFrameSize(thriftMaxFrameSize)
                    .setMaxReadBufferSize(thriftMaxReadBufferSize).setPort(thriftPort);
            thriftApiServer.start();
        } else {
            Logger.info("API Thrift Server disabled.");
        }
    }

    private void destroyThriftServer() {
        if (thriftApiServer != null) {
            thriftApiServer.destroy();
            thriftApiServer = null;
        }
    }

    public static ApiRegistry apiRegistry;
    private static ServiceRegistration<IApiRegistry> apiRegistryServiceRegistration;

    private void initApiRegistry() {
        apiRegistry = new ApiRegistry();
        apiRegistry.init();

        Dictionary<String, String> props = new Hashtable<>();
        props.put(Constants.LOOKUP_PROP_MODULE, Activator.MODULE_NAME);
        apiRegistryServiceRegistration = framework.getBundleContext().registerService(
                IApiRegistry.class, apiRegistry, props);

        IApi pingApi = new IApi() {
            @Override
            public Object call(ApiParams params) throws Exception {
                return "pong";
            }
        };
        apiRegistry.register(Activator.MODULE_NAME, "ping", pingApi);
    }

    private void destroyApiRegistry() {
        if (apiRegistry != null) {
            try {
                apiRegistry.destroy();
            } finally {
                apiRegistry = null;
                if (apiRegistryServiceRegistration != null) {
                    try {
                        apiRegistryServiceRegistration.unregister();
                    } finally {
                        apiRegistryServiceRegistration = null;
                    }
                }
            }
        }
    }

    private static Framework framework;

    private void destroyFelix() throws BundleException, InterruptedException {
        if (framework != null) {
            try {
                framework.stop();
                framework.waitForStop(0);
            } finally {
                framework = null;
            }
        }
    }

    private void initFelix() throws IOException, BundleException, InterruptedException {
        Properties felixConfigProps = _loadFelixConfigProps();
        framework = FelixOsgiUtils.createFelixFramework(felixConfigProps);

        Runtime.getRuntime().addShutdownHook(new Thread("Apache Felix Shutdown Hook") {
            public void run() {
                try {
                    if (framework != null) {
                        framework.stop();
                        framework.waitForStop(0);
                    }
                } catch (Exception e) {
                    Logger.error("Error stopping framework", e);
                }
            }
        });

        _startAllBundles();
    }

    private static void _startAllBundles() {
        if (Logger.isDebugEnabled()) {
            Logger.debug("Starting all bundles...");
        }
        Bundle[] bundles = framework.getBundleContext().getBundles();
        Arrays.sort(bundles, new Comparator<Bundle>() {
            @Override
            public int compare(Bundle o1, Bundle o2) {
                long result = o1.getBundleId() - o2.getBundleId();
                return result < 0 ? -1 : (result > 0 ? 1 : 0);
            }
        });
        for (Bundle bundle : bundles) {
            try {
                FelixOsgiUtils.startBundle(bundle);
            } catch (Exception e) {
                Logger.error(e.getMessage(), e);
            }
        }
    }

    private static Properties _loadFelixConfigProps() throws IOException {
        File appFolder = dzserver.Application.applicationHomeDir();

        String confFelixPropsFile = dzserver.Application
                .staticConfigString("felix.properties.file");
        if (StringUtils.isBlank(confFelixPropsFile)) {
            confFelixPropsFile = "conf/osgi-felix.properties";
        }
        File felixPropsFile = new File(appFolder, confFelixPropsFile);

        if (!felixPropsFile.isFile() || !felixPropsFile.canRead()) {
            throw new IllegalArgumentException("[" + felixPropsFile.getAbsolutePath()
                    + "] is not a file or not readable");
        }

        Properties felixConfigProps = new Properties();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(felixPropsFile);
            felixConfigProps.load(fis);
        } finally {
            IOUtils.closeQuietly(fis);
        }

        DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
        Date date = new Date();
        String instanceRandomStr = df.format(date) + "_" + RandomStringUtils.randomAlphanumeric(8);

        // perform variables substitution for system properties.
        for (Enumeration<?> e = felixConfigProps.propertyNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            String value = felixConfigProps.getProperty(name);
            if (value != null) {
                value = value.replace("${random}", instanceRandomStr);
            }
            value = Util.substVars(value, name, null, felixConfigProps);
            felixConfigProps.setProperty(name, value);
        }

        return felixConfigProps;
    }
}
