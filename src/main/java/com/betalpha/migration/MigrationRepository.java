package com.betalpha.migration;

import com.google.common.collect.Lists;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.cognitor.cassandra.migration.resolver.ClassPathLocationScanner;
import org.cognitor.cassandra.migration.resolver.FileSystemLocationScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static org.cognitor.cassandra.migration.util.Ensure.notNullOrEmpty;

/**
 * <p>
 * This class represents the collection of scripts that contain database migrations. It will scan the given location for
 * scripts that can be executed and analyzes the version of the scripts.
 * </p>
 * <p>
 * Only scripts that end with <code>SCRIPT_EXTENSION</code> will be considered.
 * </p>
 * <p>
 * Within a script every line starting with <code>COMMENT_PREFIX</code> will be ignored.
 * </p>
 *
 * @author Patrick Kranz
 */
@Slf4j
@Service
@Data
public class MigrationRepository {
    /**
     * The default location in the classpath to check for migration scripts
     */
    public static final String DEFAULT_SCRIPT_PATH = "cassandra/migration";
    public static final String DEFAULT_DATA_PATH = "cassandra/data";
    /**
     * The script extension for migrations. Every file that not ends with this extension will not be considered.
     */
    public static final String SCRIPT_EXTENSION = ".cql";

    /**
     * The encoding that es expected from the cql files.
     */
    public static final String SCRIPT_ENCODING = "UTF-8";

    /**
     * The delimiter that needs to be placed between the version and the name of the script.
     */
    public static final String VERSION_NAME_DELIMITER = "_";

    /**
     * Pattern matching the prefixes that can be put in the beginning of a line to indicate a single line comment.
     * Any line matching this pattern will be ignored.
     */
    public static final String SINGLE_LINE_COMMENT_PATTERN = "(^--.*)|(^//.*)";

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationRepository.class);
    private static final String EXTRACT_VERSION_ERROR_MSG = "Error for script %s. Unable to extract version.";
    private static final String SCANNING_SCRIPT_FOLDER_ERROR_MSG = "Error while scanning script folder for new scripts.";
    private static final String READING_SCRIPT_ERROR_MSG = "Error while reading script %s";

    private String scriptPath;
    private Pattern commentPattern;
    private List<Script> migrationScripts;
    @Value("${script.file.path}")
    private String serverScriptPath;
    @Value("${data.file.path}")
    private String serverDataPath;


    @Resource
    Environment environment;

    /**
     * Creates a new repository with the <code>DEFAULT_SCRIPT_PATH</code> configured.
     */
    public MigrationRepository() {
//        this(DEFAULT_SCRIPT_PATH);
    }

    /**
     * Creates a new repository with the given scriptPath. The scriptPath must not be null.
     *
     * @param scriptPath the path on the classpath to the migration scripts. Must not be null.
     * @throws MigrationException in case there is a problem reading the scripts in the path.
     */


    public MigrationRepository(String scriptPath) {
        notNullOrEmpty(scriptPath, "scriptPath");
        this.scriptPath = normalizePath(scriptPath);
        this.commentPattern = Pattern.compile(SINGLE_LINE_COMMENT_PATTERN);
        try {
            log.info("scriptPath=" + scriptPath);
            migrationScripts = scanForScripts(scriptPath);
        } catch (IOException exception) {
            throw new MigrationException(SCANNING_SCRIPT_FOLDER_ERROR_MSG, exception);
        }
    }

    @PostConstruct
    public void initial() {
        notNullOrEmpty(serverScriptPath, "scriptPath");
        this.scriptPath = normalizePath(serverScriptPath);
        this.commentPattern = Pattern.compile(SINGLE_LINE_COMMENT_PATTERN);
        try {
            log.info("scriptPath=" + serverScriptPath);
            migrationScripts = scanForScripts(serverScriptPath);
        } catch (IOException exception) {
            throw new MigrationException(SCANNING_SCRIPT_FOLDER_ERROR_MSG, exception);
        }
    }

    /**
     * Ensures that every path starts and ends with a slash character.
     *
     * @param scriptPath the scriptPath that needs to be normalized
     * @return a path with leading and trailing slash
     */
    private String normalizePath(String scriptPath) {
        StringBuilder builder = new StringBuilder(scriptPath.length() + 1);
        if (scriptPath.startsWith("/")) {
            builder.append(scriptPath.substring(1));
        } else {
            builder.append(scriptPath);
        }
        if (!scriptPath.endsWith("/")) {
            builder.append("/");
        }
        return builder.toString();
    }

    /**
     * Gets the version of the scripts. This version represents the highest version that can be found in the scripts,
     * meaning the script with the highest version will be the one defining the version that is returned here.
     * In case the directory is empty zero will be returned as a version number.
     *
     * @return the latest version of the migrations, or zero if the directory contains no scripts.
     */
    public int getLatestVersion() {
        if (migrationScripts.isEmpty()) {
            return 0;
        }
        return migrationScripts.get(migrationScripts.size() - 1).getVersion();
    }

    private List<Script> scanForScripts(String scriptPath) throws IOException {
        if (ObjectUtils.isEmpty(environment)) {
            return null;
        }
        List<String> profiles = Lists.newArrayList(environment.getActiveProfiles());
        if (profiles.contains("custom")) {
            return scanForScriptsCustom(DEFAULT_SCRIPT_PATH);
        }
        LOGGER.debug("Scanning for cql migration scripts in " + scriptPath);
        File folder = new File(scriptPath);
        if (!folder.isDirectory()) {
            return null;
        }
        File[] files = folder.listFiles();
        if (files.length <= 0) {
            return null;
        }
        Set<Script> scripts = new TreeSet<>();
        for (int i = 0; i < files.length; i++) {
            if (isMigrationScript(files[i].getName())) {
                String scriptName = files[i].getName();
                int version = extractScriptVersion(scriptName);
                scripts.add(new Script(version, files[i].getAbsolutePath(), scriptName));
            } else {
                LOGGER.warn(format("Ignoring file %s because it is not a cql file.", files[i].getAbsolutePath()));
            }
        }

        LOGGER.info(format("Found %d migration scripts", scripts.size()));
        return new ArrayList<>(scripts);
    }

    private List<Script> scanForScriptsCustom(String scriptPath) throws IOException {
        LOGGER.debug("Scanning for cql migration scripts in " + scriptPath);
        Enumeration<URL> scriptResources = getClass().getClassLoader().getResources(scriptPath);
        Set<MigrationRepository.Script> scripts = new TreeSet<>();
        while (scriptResources.hasMoreElements()) {
            URL script = scriptResources.nextElement();
            ClassPathLocationScanner scanner = new FileSystemLocationScanner();
            for (String resource : scanner.findResourceNames(scriptPath, script)) {
                if (isMigrationScript(resource)) {
                    String scriptName = extractScriptName(resource);
                    int version = extractScriptVersion(scriptName);
                    scripts.add(new MigrationRepository.Script(version, resource, scriptName));
                } else {
                    LOGGER.warn(format("Ignoring file %s because it is not a cql file.", resource));
                }
            }
        }
        LOGGER.info(format("Found %d migration scripts", scripts.size()));
        return new ArrayList<>(scripts);
    }


    private static int extractScriptVersion(String scriptName) {
        String[] splittedName = scriptName.split(VERSION_NAME_DELIMITER);
        try {
            return parseInt(splittedName[0]);
        } catch (NumberFormatException exception) {
            throw new MigrationException(format(EXTRACT_VERSION_ERROR_MSG, scriptName),
                    exception, scriptName);
        }
    }

    private static boolean isMigrationScript(String resource) {
        return resource.endsWith(SCRIPT_EXTENSION);
    }

    private String extractScriptName(String resourceName) {
        return resourceName.substring(scriptPath.length());
    }

    /**
     * Returns all migrations starting from and excluding the given version. Usually you want to provide the version of
     * the database here to get all migrations that need to be executed. In case there is no script with a newer
     * version than the one given, an empty list is returned.
     *
     * @param version the version that is currently in the database
     * @return all versions since the given version or an empty list if no newer script is available. Never null.
     * Does not include the given version.
     */
    public List<DbMigration> getMigrationsSinceVersion(int version) {
        List<DbMigration> dbMigrations = new ArrayList<>();
        migrationScripts.stream().filter(script -> script.getVersion() > version).forEach(script -> {
            String content = loadScriptContent(script);
            dbMigrations.add(new DbMigration(script.getScriptName(), script.getVersion(), content));
        });
        return dbMigrations;
    }

    private String loadScriptContent(Script script) {
        try {
            return readResourceFileAsString(script.getResourceName(), getClass().getClassLoader());
        } catch (IOException exception) {
            throw new MigrationException(format(READING_SCRIPT_ERROR_MSG, script.getResourceName()),
                    exception, script.getScriptName());
        }
    }

    private String readResourceFileAsString(String resourceName, ClassLoader classLoader) throws IOException {
        if (ObjectUtils.isEmpty(environment)) {
            return null;
        }
        List<String> profiles = Lists.newArrayList(environment.getActiveProfiles());
        if (profiles.contains("custom")) {
            return readResourceFileAsStringCustom(resourceName, classLoader);
        }

        StringBuilder fileContent = new StringBuilder(256);
        new BufferedReader(
                new InputStreamReader(new FileInputStream(resourceName), SCRIPT_ENCODING))
                .lines().filter(line -> !isLineComment(line)).forEach(fileContent::append);
        return fileContent.toString();
    }


    private String readResourceFileAsStringCustom(String resourceName, ClassLoader classLoader) throws IOException {
        StringBuilder fileContent = new StringBuilder(256);
        new BufferedReader(
                new InputStreamReader(classLoader.getResourceAsStream(resourceName), SCRIPT_ENCODING))
                .lines().filter(line -> !isLineComment(line)).forEach(fileContent::append);
        return fileContent.toString();
    }

    private boolean isLineComment(String line) {
        return commentPattern.matcher(line).matches();
    }

    private class Script implements Comparable {
        private final int version;
        private final String resourceName;
        private final String scriptName;

        public Script(int version, String resourceName, String scriptName) {
            this.version = version;
            this.resourceName = resourceName;
            this.scriptName = scriptName;
        }

        public int getVersion() {
            return version;
        }

        public String getResourceName() {
            return resourceName;
        }

        public String getScriptName() {
            return scriptName;
        }

        @Override
        public int compareTo(Object o) {
            if (o == null || !Script.class.isAssignableFrom(o.getClass())) {
                return 1;
            }
            return Integer.compare(this.version, ((Script) o).version);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !Script.class.isAssignableFrom(o.getClass())) {
                return false;
            }
            if (o == this) {
                return true;
            }

            Script other = (Script) o;
            return this.resourceName.equals(other.resourceName)
                    && this.scriptName.equals(other.scriptName)
                    && this.version == other.version;
        }

        @Override
        public int hashCode() {
            int result = version;
            result = 31 * result + resourceName.hashCode();
            result = 31 * result + scriptName.hashCode();
            return result;
        }
    }
}
