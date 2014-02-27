package dzserver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.framework.util.Util;
import org.apache.felix.main.AutoProcessor;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

import play.Application;
import play.GlobalSettings;
import play.Logger;
import play.Play;
import play.api.mvc.EssentialFilter;
import play.filters.gzip.GzipFilter;
import dzserver.utils.FelixOsgiUtils;

public class Global extends GlobalSettings {
    static {
        System.setProperty("java.awt.headless", "true");

        // default environment
        System.setProperty("spring.profiles.active", "development");
        System.setProperty("logger.resource", "logger-development.xml");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends EssentialFilter> Class<T>[] filters() {
        return new Class[] { GzipFilter.class };
    }

    private void initEnv() {
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
    }

    private void destroy() throws Exception {
        destroyFelix();
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
        _initFelixFramework(felixConfigProps);

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

    private static void _initFelixFramework(Properties felixConfigProps) throws IOException,
            BundleException {
        // configure Felix auto-deploy directory
        String sAutoDeployDir = felixConfigProps.getProperty(AutoProcessor.AUTO_DEPLOY_DIR_PROPERY);
        if (sAutoDeployDir == null) {
            throw new RuntimeException("Can not find configuration ["
                    + AutoProcessor.AUTO_DEPLOY_DIR_PROPERY + "]");
        }
        File fAutoDeployDir = new File(dzserver.Application.applicationHomeDir(), sAutoDeployDir);
        if (Logger.isDebugEnabled()) {
            Logger.debug(AutoProcessor.AUTO_DEPLOY_DIR_PROPERY + ": "
                    + fAutoDeployDir.getAbsolutePath());
        }
        felixConfigProps.setProperty(AutoProcessor.AUTO_DEPLOY_DIR_PROPERY,
                fAutoDeployDir.getAbsolutePath());

        // configure Felix temp (storage) directory
        String sCacheDir = felixConfigProps
                .getProperty(org.osgi.framework.Constants.FRAMEWORK_STORAGE);
        if (sCacheDir == null) {
            throw new RuntimeException("Can not find configuration ["
                    + org.osgi.framework.Constants.FRAMEWORK_STORAGE + "]");
        } else if (Logger.isDebugEnabled()) {
            Logger.debug(org.osgi.framework.Constants.FRAMEWORK_STORAGE + ": " + sCacheDir);
        }
        File fCacheDir = new File(dzserver.Application.applicationHomeDir(), sCacheDir);
        felixConfigProps.setProperty(org.osgi.framework.Constants.FRAMEWORK_STORAGE,
                fCacheDir.getAbsolutePath());

        // configure Felix's File Install watch directory
        final String PROP_FELIX_FILE_INSTALL_DIR = "felix.fileinstall.dir";
        String sMonitorDir = felixConfigProps.getProperty(PROP_FELIX_FILE_INSTALL_DIR);
        if (sMonitorDir != null) {
            File fMonitorDir = new File(dzserver.Application.applicationHomeDir(), sMonitorDir);
            felixConfigProps
                    .setProperty(PROP_FELIX_FILE_INSTALL_DIR, fMonitorDir.getAbsolutePath());
            if (Logger.isDebugEnabled()) {
                Logger.debug(PROP_FELIX_FILE_INSTALL_DIR + ": " + fMonitorDir.getAbsolutePath());
            }
        }

        // check for Felix's Remote Shell listen IP & Port
        if (Logger.isDebugEnabled()) {
            String remoteShellListenIp = felixConfigProps.getProperty("osgi.shell.telnet.ip");
            String remoteShellListenPort = felixConfigProps.getProperty("osgi.shell.telnet.port");
            Logger.debug("Remote Shell: " + remoteShellListenIp + ":" + remoteShellListenPort);
        }

        Map<String, String> config = new HashMap<String, String>();
        for (Entry<Object, Object> entry : felixConfigProps.entrySet()) {
            config.put(entry.getKey().toString(), entry.getValue().toString());
        }
        FrameworkFactory factory = new org.apache.felix.framework.FrameworkFactory();
        framework = factory.newFramework(config);
        framework.init();
        AutoProcessor.process(felixConfigProps, framework.getBundleContext());
        FelixOsgiUtils.deployBundles(framework, fAutoDeployDir);
        framework.start();
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
