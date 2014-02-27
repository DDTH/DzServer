package dzserver.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;

import org.apache.commons.io.IOUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;

import play.Logger;

public class FelixOsgiUtils {
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
}
