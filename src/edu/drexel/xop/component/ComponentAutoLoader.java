/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.component;

import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.logger.LogUtils;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The component autoloader periodically scans a specified directory for <br/>
 * jar files to be added. It then takes the jar, reads the component.xml and
 * loads required libraries.
 * 
 * You specify which plugins to enable using the property, xop.enabled.plugins,
 * Each plugin is separated by a comma. If no property value found, then all
 * plugins in the specified plugins directory is loaded.
 * 
 * @author David Millar
 */
public class ComponentAutoLoader {

    private static final Logger logger = LogUtils.getLogger(ComponentAutoLoader.class.getName());
    private static final String COMPONENT_PACKAGE_PREFIX = "edu.drexel.xop.component";
    private static final String COMPONENT_SUFFIX = "Component";

    // Config
    private ComponentManager componentManager;
    private JarFileLoader loader;

    public ComponentAutoLoader(ComponentManager componentManager) {
        logger.fine("Initializing ComponentAutoLoader...");
        this.componentManager = componentManager;
        init();
    }

    /**
     * initialize the ComponentAutoLoader, kicks off a filewatcher thread, and loads the
     * list of plugins to enable
     */
    private void init() {
        // Get props
        String dir = new File(XopProperties.getInstance().getProperty(XopProperties.COMPONENT_DIR)).getAbsolutePath()+"/";
	System.out.println(XopProperties.getInstance().getProperty(XopProperties.COMPONENT_DIR));
        List<String> enabledPlugins = loadEnabledPluginsProperty();

        if ((new File(dir)).exists()) {
            loader = new JarFileLoader(ComponentAutoLoader.class.getClassLoader());
            logger.log(Level.INFO, "Loading enabled plugins from " + dir);

            for (String comp : enabledPlugins) {
                logger.info("Loading: " + dir + comp + ".jar");
                loadComponent(comp);
            }
        } else {
            logger.log(Level.WARNING, "Plugin directory \"" + dir + "\" does not exist.");
        }
    }

    /**
     * @return a set of names of plugins to enable, if empty, then load all plugins
     */
    private List<String> loadEnabledPluginsProperty() {
        List<String> retVal = new LinkedList<>();
        String enabledPluginsStr = XopProperties.getInstance().getProperty(XopProperties.ENABLED_COMPONENTS);
        if (enabledPluginsStr == null || "".equals(enabledPluginsStr)) {
            logger.info(XopProperties.ENABLED_COMPONENTS + " property not found. Enabling all plugins.");
        } else {
            logger.info("Loading only the following plugins" + LogUtils.arraytoString(enabledPluginsStr.split(",")));
            for (String pluginName : enabledPluginsStr.split(",")) {
                retVal.add(pluginName.trim());
            }
        }
        return retVal;
    }

    private boolean fileIsCool(File file) {
        return ((file != null) && file.exists() && file.isFile() && file.canRead());
    }

    /**
     * Load Components
     * 
     * @param componentName component to load (if component is "foo", this method will try to load "foo.jar")
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void loadComponent(String componentName) {
        String dir = new File(XopProperties.getInstance().getProperty(XopProperties.COMPONENT_DIR)).getAbsolutePath();
        File file = new File(dir + "/"+ componentName + ".jar");
        try {

            JarFile jf = new JarFile(file);

            Manifest mf = jf.getManifest(); // if jar has a class-path in manifest add it's entries
            if (mf != null) {
                String cp = mf.getMainAttributes().getValue("class-path");
                if (cp != null) {
                    for (String cpe : cp.split("\\s+")) {
                        File lib = new File(file.getParentFile(), cpe);
                        if (fileIsCool(lib)) {
                            loader.addFile(lib);
                        }
                    }
                }
            }
            jf.close();
            loader.addFile(file);
            String componentFullName = COMPONENT_PACKAGE_PREFIX + "." + componentName + "."
                + componentName.toUpperCase() + COMPONENT_SUFFIX;
            Class clazz = loader.loadClass(componentFullName);
            Constructor ctor = clazz.getConstructor();
            VirtualComponent vc = (VirtualComponent) ctor.newInstance();
            // All these exceptions for 5 lines of code.
            componentManager.addComponent(vc);

        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            logger.log(Level.SEVERE, "No constructor for component", ex);
        } catch (NoSuchMethodException ex) {
            logger.log(Level.SEVERE, "No default constructor for component", ex);
        } catch (SecurityException ex) {
            logger.log(Level.SEVERE, "No public constructor for component", ex);
        } catch (ClassNotFoundException ex) {
            logger.log(Level.SEVERE, "Invalid class for component", ex);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (NoClassDefFoundError ex) {
            logger.log(Level.INFO, System.getProperty("java.class.path"));
            logger.log(Level.SEVERE, "Missing libraries.", ex);
        }
    }

    public static class JarFileLoader extends URLClassLoader {

        public JarFileLoader(ClassLoader parent) {
            super(new URL[] {}, parent);
        }

        public void addFile(File file) throws MalformedURLException {
            URL f = file.toURI().toURL();
            f = jarURL(f);
            addURL(urlFromPath(f.getFile()));
        }

        private URL jarURL(URL url) {
            return url;
        }

        private URL urlFromPath(String path) throws MalformedURLException {
            return new URL("jar:file://" + path + "!/");
        }
    }
}
