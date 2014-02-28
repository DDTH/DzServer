package dzserver.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.felix.main.AutoProcessor;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

import play.Logger;

public class FelixOsgiUtils {

    /**
     * Creates an initializes an instance of Apache Felix Framework.
     * 
     * @param felixConfigProps
     * @return
     * @throws IOException
     * @throws BundleException
     */
    public static Framework createFelixFramework(Properties felixConfigProps) throws IOException,
            BundleException {
        // configure Felix auto-deploy directory: relative with application
        // directory
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

        // configure Felix temp (storage) directory: relative with application
        // directory if not starts with a slash (/), otherwise absolute
        // path
        String sCacheDir = felixConfigProps
                .getProperty(org.osgi.framework.Constants.FRAMEWORK_STORAGE);
        if (sCacheDir == null) {
            throw new RuntimeException("Can not find configuration ["
                    + org.osgi.framework.Constants.FRAMEWORK_STORAGE + "]");
        } else if (Logger.isDebugEnabled()) {
            Logger.debug(org.osgi.framework.Constants.FRAMEWORK_STORAGE + ": " + sCacheDir);
        }
        File fCacheDir = sCacheDir.startsWith("/") ? new File(sCacheDir) : new File(
                dzserver.Application.applicationHomeDir(), sCacheDir);
        felixConfigProps.setProperty(org.osgi.framework.Constants.FRAMEWORK_STORAGE,
                fCacheDir.getAbsolutePath());

        // configure Felix's File Install watch directory: relative with
        // application directory
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

            // configure Felix File Install temp directory: relative with
            // application
            // directory if not starts with a slash (/), otherwise absolute
            // path
            String sFileInstallTempDir = felixConfigProps.getProperty("felix.fileinstall.tmpdir");
            if (sFileInstallTempDir == null) {
                throw new RuntimeException("Can not find configuration [felix.fileinstall.tmpdir]");
            }
            File fFileInstallTempDir = sFileInstallTempDir.startsWith("/") ? new File(
                    sFileInstallTempDir) : new File(dzserver.Application.applicationHomeDir(),
                    sFileInstallTempDir);
            if (Logger.isDebugEnabled()) {
                Logger.debug("felix.fileinstall.tmpdir: " + fFileInstallTempDir.getAbsolutePath());
            }
            felixConfigProps.setProperty("felix.fileinstall.tmpdir",
                    fFileInstallTempDir.getAbsolutePath());
        }

        Map<String, String> config = new HashMap<String, String>();
        for (Entry<Object, Object> entry : felixConfigProps.entrySet()) {
            config.put(entry.getKey().toString(), entry.getValue().toString());
        }
        FrameworkFactory factory = new org.apache.felix.framework.FrameworkFactory();
        Framework framework = factory.newFramework(config);
        framework.init();
        AutoProcessor.process(felixConfigProps, framework.getBundleContext());
        deployBundles(framework, fAutoDeployDir);
        framework.start();
        return framework;
    }

    /**
     * Starts a bundle.
     * 
     * @param bundle
     * @throws BundleException
     */
    public static void startBundle(Bundle bundle) throws BundleException {
        int state = bundle.getState();
        if ((state == Bundle.RESOLVED || state == Bundle.INSTALLED)
                && bundle.getHeaders().get(Constants.FRAGMENT_HOST) == null) {
            bundle.start();
            if (Logger.isDebugEnabled()) {
                Logger.debug("Bundle [" + bundle + "] has been started.");
            }
        }
    }

    /**
     * Deploys all bundles under a directory (and all its sub-directories).
     * 
     * @param framework
     * @param fileOrDir
     * @throws FileNotFoundException
     * @throws BundleException
     */
    public static void deployBundles(Framework framework, File fileOrDir)
            throws FileNotFoundException, BundleException {
        if (fileOrDir.isFile() && fileOrDir.getName().endsWith(".jar")) {
            _deployBundle(framework, fileOrDir.getName(), fileOrDir);
        } else {
            // make sure bundles are deployed and started in order!
            File[] files = fileOrDir.listFiles();
            if (files != null && files.length > 0) {
                Arrays.sort(files, new Comparator<File>() {
                    @Override
                    public int compare(File file1, File file2) {
                        return file1.compareTo(file2);
                    }
                });
                for (File f : files) {
                    if (!f.getName().startsWith(".")) {
                        deployBundles(framework, f);
                    }
                }
            }
        }
    }

    private static Bundle _deployBundle(Framework framework, String bundleId, File file)
            throws BundleException, FileNotFoundException {
        FileInputStream fis = new FileInputStream(file);
        try {
            return _deployBundle(framework, bundleId, fis);
        } finally {
            IOUtils.closeQuietly(fis);
        }
    }

    private static Bundle _deployBundle(Framework framework, String bundleId, InputStream is)
            throws BundleException {
        return _deployBundle(framework, bundleId, is, false);
    }

    private static Bundle _deployBundle(Framework framework, String bundleId, InputStream is,
            boolean start) throws BundleException {
        Bundle bundle = framework.getBundleContext().installBundle(bundleId, is);
        if (Logger.isDebugEnabled()) {
            Logger.debug("Bundle [" + bundle + "] has been deployed.");
        }
        if (start) {
            startBundle(bundle);
        }
        return bundle;
    }

}
